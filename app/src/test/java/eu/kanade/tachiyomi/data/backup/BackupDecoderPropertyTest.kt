package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.PreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.BackupCompatibilityDecisionType
import eu.kanade.tachiyomi.data.backup.restore.BackupCompatibilityPolicy
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.util.zip.GZIPOutputStream

class BackupDecoderPropertyTest {

    @Test
    fun `generated backup preferences round trip through raw and gzip protobuf`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), backupArb) { backup ->
            val rawBytes = BackupCreator.encodeBackup(backup)
            val gzipBytes = gzip(rawBytes)

            BackupDecoder.decodeBackupBytes(rawBytes) shouldBe backup
            BackupDecoder.decodeBackupBytes(gzipBytes) shouldBe backup
        }
    }

    @Test
    fun `generated backup sections round trip through protobuf without changing server owned fields`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), richBackupArb) { backup ->
            val decoded = BackupDecoder.decodeBackupBytes(gzip(BackupCreator.encodeBackup(backup)))

            decoded.toComparableBackup() shouldBe backup.toComparableBackup()
        }
    }

    @Test
    fun `json shaped backup bytes stay rejected`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), jsonLikeBytesArb) { bytes ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupDecoder.decodeBackupBytes(bytes)
            }
        }
    }

    @Test
    fun `corrupt raw and gzip shaped backup bytes fail without escaping the decoder contract`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), corruptBackupBytesArb) { bytes ->
            assertThrows(Exception::class.java) {
                BackupDecoder.decodeBackupBytes(bytes)
            }
        }
    }

    @Test
    fun `valid adjacent raw and gzip mutations finish with a controlled decoder result`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), validAdjacentMutationArb) { generated ->
            val result: Result<Backup> = assertTimeout(
                Duration.ofSeconds(1),
                ThrowingSupplier { runCatching { BackupDecoder.decodeBackupBytes(generated.bytes) } },
                generated.describe(),
            )

            result.exceptionOrNull()?.let { failure ->
                check(
                    failure is IllegalArgumentException || failure is IOException ||
                        failure is kotlinx.serialization.SerializationException,
                ) {
                    "Unexpected decoder failure ${failure::class.qualifiedName}: ${failure.message}; ${generated.describe()}"
                }
            }
        }
    }

    @Test
    fun `json shaped prefixes remain rejected around valid backups`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), richBackupArb, Arb.boolean()) { backup, gzip ->
            val valid = BackupCreator.encodeBackup(backup)
            val payload = if (gzip) gzip(valid) else valid
            val json = "{\"backup\":[]}".encodeToByteArray()

            assertThrows(Exception::class.java) {
                BackupDecoder.decodeBackupBytes(json + payload)
            }
        }
    }

    @Test
    fun `oversized raw and gzip backups fail through the decoder contract`() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            BackupDecoder.decodeBackupBytes(
                bytes = ByteArray(1025),
                maxDecodedBytes = 1024,
            )
        }
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            BackupDecoder.decodeBackupBytes(
                bytes = gzip(ByteArray(4096)),
                maxDecodedBytes = 1024,
            )
        }
    }

    @Test
    fun `compressed input limit rejects oversized gzip before decoding`() {
        val incompressible = ByteArray(1025) { it.toByte() }

        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            BackupDecoder.decodeBackupBytes(
                bytes = gzip(incompressible),
                maxCompressedBytes = 128,
                maxDecodedBytes = 4096,
            )
        }
    }

    @Test
    fun `backups at the exact byte limits remain accepted`() {
        val backup = Backup(backupManga = emptyList())
        val raw = BackupCreator.encodeBackup(backup)
        val compressed = gzip(raw)

        BackupDecoder.decodeBackupBytes(raw, maxDecodedBytes = raw.size) shouldBe backup
        BackupDecoder.decodeBackupBytes(
            compressed,
            maxCompressedBytes = compressed.size,
            maxDecodedBytes = raw.size,
        ) shouldBe backup
    }

    @Test
    fun `large repeated sections and long text below the policy limit round trip`() {
        val longText = "x".repeat(256 * 1024)
        val backup = Backup(
            backupManga = List(1_000) { index ->
                BackupManga(source = index.toLong() + 1, url = "manga-$index", title = "Manga $index")
            },
            backupPreferences = List(32) { index ->
                BackupPreference("long-text-$index", StringPreferenceValue(longText))
            },
        )
        val raw = BackupCreator.encodeBackup(backup)

        check(raw.size < BackupDecoder.MAX_DECODED_BYTES)
        BackupDecoder.decodeBackupBytes(raw).toComparableBackup() shouldBe backup.toComparableBackup()
        BackupDecoder.decodeBackupBytes(gzip(raw)).toComparableBackup() shouldBe backup.toComparableBackup()
    }

    @Test
    fun `malformed gzip is reported as a classified backup decode failure`() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            BackupDecoder.decodeBackupBytes(byteArrayOf(0x1f, 0x8b.toByte(), 0x08))
        }
    }

    @Test
    fun `restore option boolean arrays preserve only client owned settings`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(Arb.boolean(), 0..12)) { flags ->
            val decoded = RestoreOptions.fromBooleanArray(flags.toBooleanArray())

            decoded.libraryEntries shouldBe false
            decoded.categories shouldBe false
            decoded.appSettings shouldBe flags.getOrElse(2) { false }
            decoded.sourceSettings shouldBe flags.getOrElse(3) { false }
            decoded.privateSettings shouldBe flags.getOrElse(4) { false }
            decoded.asBooleanArray().toList() shouldBe listOf(
                false,
                false,
                flags.getOrElse(2) { false },
                flags.getOrElse(3) { false },
                flags.getOrElse(4) { false },
            )
        }
    }

    @Test
    fun `supported preference value types remain restorable when local types match`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(preferenceArb, 1..12)) { generatedPreferences ->
            val preferences = generatedPreferences.distinctBy { it.key }
            val existingValues = preferences.associate { it.key to it.value.defaultValueForType() }

            val result = BackupCompatibilityPolicy(
                appPreferences = existingValues,
                sourcePreferences = emptyMap(),
            ).evaluate(
                Backup(backupManga = emptyList(), backupPreferences = preferences),
                RestoreOptions(appSettings = true),
            )

            result.restorable.appPreferences shouldBe preferences
            result.summary.decisions.map { it.decision }.toSet() shouldBe
                setOf(BackupCompatibilityDecisionType.RESTORE_DIRECT)
        }
    }

    @Test
    fun `preference value type mismatches are recorded as unsupported without restore`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(preferenceArb, 1..12)) { generatedPreferences ->
            val preferences = generatedPreferences.distinctBy { it.key }
            val existingValues = preferences.associate { it.key to it.value.differentDefaultValueForType() }

            val result = BackupCompatibilityPolicy(
                appPreferences = existingValues,
                sourcePreferences = emptyMap(),
            ).evaluate(
                Backup(backupManga = emptyList(), backupPreferences = preferences),
                RestoreOptions(appSettings = true),
            )

            result.restorable.appPreferences shouldBe emptyList()
            result.summary.unsupportedCount shouldBe preferences.size
        }
    }

    @Test
    fun `generated valid backups decode without resource or platform failures`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), richBackupArb) { backup ->
            assertDoesNotThrow {
                BackupDecoder.decodeBackupBytes(BackupCreator.encodeBackup(backup))
            }
        }
    }

    private companion object {
        private const val MAX_MUTATION_RANGE = 64

        fun gzip(bytes: ByteArray): ByteArray {
            return ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { gzip ->
                    gzip.write(bytes)
                }
                output.toByteArray()
            }
        }

        val safeTextArb = Arb.string(0..64)

        val preferenceValueArb = arbitrary { random ->
            when (random.random.nextInt(6)) {
                0 -> IntPreferenceValue(random.random.nextInt())
                1 -> LongPreferenceValue(random.random.nextLong())
                2 -> FloatPreferenceValue(random.random.nextFloat())
                3 -> StringPreferenceValue(safeTextArb.bind())
                4 -> BooleanPreferenceValue(random.random.nextBoolean())
                else -> StringSetPreferenceValue(Arb.set(safeTextArb, 0..6).bind())
            }
        }

        val preferenceArb: Arb<BackupPreference> = Arb.bind(
            Arb.string(1..32).map { key -> key.ifBlank { "pref" } },
            preferenceValueArb,
        ) { key, value ->
            BackupPreference(key = key, value = value)
        }

        val backupArb: Arb<Backup> = Arb.list(preferenceArb, 0..12)
            .map { preferences ->
                Backup(
                    backupManga = emptyList(),
                    backupPreferences = preferences.distinctBy { it.key },
                )
            }

        val chapterArb: Arb<BackupChapter> = Arb.bind(
            Arb.string(1..48),
            Arb.string(1..48),
            Arb.boolean(),
            Arb.boolean(),
            Arb.long(0L..10_000L),
            Arb.long(0L..10_000L),
        ) { url, name, read, bookmark, lastPageRead, sourceOrder ->
            BackupChapter(
                url = url,
                name = name,
                read = read,
                bookmark = bookmark,
                lastPageRead = lastPageRead,
                sourceOrder = sourceOrder,
                chapterNumber = sourceOrder.toFloat(),
            )
        }

        val mangaArb: Arb<BackupManga> = Arb.bind(
            Arb.long(1L..Long.MAX_VALUE),
            Arb.string(1..48),
            Arb.string(1..48),
            Arb.list(chapterArb, 0..4),
            Arb.list(Arb.long(0L..64L), 0..4),
        ) { source, url, title, chapters, categories ->
            BackupManga(
                source = source,
                url = url,
                title = title,
                chapters = chapters,
                categories = categories.distinct(),
                tracking = listOf(BackupTracking(syncId = 1, libraryId = source)),
                history = chapters.take(1).map { BackupHistory(url = it.url, lastRead = it.dateFetch) },
            )
        }

        val categoryArb: Arb<BackupCategory> = Arb.bind(
            Arb.string(1..32),
            Arb.long(0L..64L),
            Arb.long(0L..64L),
        ) { name, order, id ->
            BackupCategory(name = name, order = order, id = id)
        }

        val sourceArb: Arb<BackupSource> = Arb.bind(
            Arb.string(1..32),
            Arb.long(1L..Long.MAX_VALUE),
        ) { name, sourceId ->
            BackupSource(name = name, sourceId = sourceId)
        }

        val sourcePreferencesArb: Arb<BackupSourcePreferences> = Arb.bind(
            Arb.string(1..32),
            Arb.list(preferenceArb, 0..8),
        ) { sourceKey, preferences ->
            BackupSourcePreferences(sourceKey = sourceKey, prefs = preferences.distinctBy { it.key })
        }

        val richBackupArb: Arb<Backup> = Arb.bind(
            Arb.list(mangaArb, 0..4),
            Arb.list(categoryArb, 0..4),
            Arb.list(sourceArb, 0..4),
            Arb.list(preferenceArb, 0..12),
            Arb.list(sourcePreferencesArb, 0..4),
        ) { manga, categories, sources, preferences, sourcePreferences ->
            Backup(
                backupManga = manga,
                backupCategories = categories,
                backupSources = sources,
                backupPreferences = preferences.distinctBy { it.key },
                backupSourcePreferences = sourcePreferences.distinctBy { it.sourceKey },
            )
        }

        val jsonLikeBytesArb = Arb.list(Arb.string(0..32), 0..8).map { values ->
            buildString {
                append("{")
                values.forEachIndexed { index, value ->
                    if (index > 0) append(",")
                    append("\"k")
                    append(index)
                    append("\":\"")
                    append(value.replace("\"", ""))
                    append("\"")
                }
                append("}")
            }.encodeToByteArray()
        }

        val corruptBackupBytesArb = Arb.list(Arb.int(0..255), 0..64).map { values ->
            val tail = values.map { it.toByte() }.toByteArray()
            when (tail.size % 3) {
                0 -> byteArrayOf(0x0f) + tail
                1 -> byteArrayOf(0x1f, 0x8b.toByte()) + tail
                else -> byteArrayOf(0x0b) + tail
            }
        }

        val validAdjacentMutationArb = arbitrary { random ->
            val backup = richBackupArb.bind()
            val gzip = random.random.nextBoolean()
            val baseline = BackupCreator.encodeBackup(backup).let { bytes -> if (gzip) gzip(bytes) else bytes }
            val mutations = List(random.random.nextInt(1, 5)) {
                BackupByteMutation(
                    kind = BackupByteMutation.Kind.entries.random(random.random),
                    first = random.random.nextInt(0, baseline.size + 1),
                    second = random.random.nextInt(0, baseline.size + 1),
                    value = random.random.nextInt(0, 256).toByte(),
                )
            }

            ValidAdjacentBackupInput(
                bytes = mutations.fold(baseline) { bytes, mutation -> mutation.applyTo(bytes) },
                encoding = if (gzip) "gzip" else "raw",
                mutations = mutations,
            )
        }

        data class ValidAdjacentBackupInput(
            val bytes: ByteArray,
            val encoding: String,
            val mutations: List<BackupByteMutation>,
        ) {
            fun describe() = "encoding=$encoding, mutations=${mutations.joinToString()}"
        }

        data class BackupByteMutation(
            val kind: Kind,
            val first: Int,
            val second: Int,
            val value: Byte,
        ) {
            enum class Kind {
                TRUNCATE,
                REPLACE,
                INSERT,
                DELETE,
                DUPLICATE_RANGE,
                SWAP_BYTES,
                APPEND_JSON,
            }

            fun applyTo(bytes: ByteArray): ByteArray {
                val firstIndex = first.coerceIn(0, bytes.size)
                val secondIndex = second.coerceIn(0, bytes.size)
                return when (kind) {
                    Kind.TRUNCATE -> bytes.copyOf(firstIndex)
                    Kind.REPLACE -> if (bytes.isEmpty()) {
                        byteArrayOf(value)
                    } else {
                        bytes.copyOf().also {
                            it[firstIndex.coerceAtMost(it.lastIndex)] = value
                        }
                    }
                    Kind.INSERT -> bytes.copyOfRange(0, firstIndex) + value + bytes.copyOfRange(firstIndex, bytes.size)
                    Kind.DELETE -> if (bytes.isEmpty() || firstIndex == bytes.size) {
                        bytes
                    } else {
                        bytes.copyOfRange(0, firstIndex) + bytes.copyOfRange(firstIndex + 1, bytes.size)
                    }
                    Kind.DUPLICATE_RANGE -> {
                        val start = minOf(firstIndex, secondIndex)
                        val end = maxOf(firstIndex, secondIndex).coerceAtMost(start + MAX_MUTATION_RANGE)
                        bytes.copyOfRange(0, end) + bytes.copyOfRange(start, end) + bytes.copyOfRange(end, bytes.size)
                    }
                    Kind.SWAP_BYTES ->
                        if (bytes.size < 2 || firstIndex == bytes.size ||
                            secondIndex == bytes.size
                        ) {
                            bytes
                        } else {
                            bytes.copyOf().also { mutated ->
                                val temporary = mutated[firstIndex]
                                mutated[firstIndex] = mutated[secondIndex]
                                mutated[secondIndex] = temporary
                            }
                        }
                    Kind.APPEND_JSON -> bytes + "{\"extra\":true}".encodeToByteArray()
                }
            }
        }

        fun Backup.toComparableBackup() = ComparableBackup(
            manga = backupManga.map {
                ComparableManga(
                    source = it.source,
                    url = it.url,
                    title = it.title,
                    chapters = it.chapters.map { chapter ->
                        ComparableChapter(
                            url = chapter.url,
                            name = chapter.name,
                            read = chapter.read,
                            bookmark = chapter.bookmark,
                            lastPageRead = chapter.lastPageRead,
                            sourceOrder = chapter.sourceOrder,
                            chapterNumber = chapter.chapterNumber,
                        )
                    },
                    categories = it.categories,
                    tracking = it.tracking,
                    history = it.history,
                )
            },
            categories = backupCategories.map { ComparableCategory(it.name, it.order, it.id, it.flags) },
            sources = backupSources,
            preferences = backupPreferences,
            sourcePreferences = backupSourcePreferences,
        )

        fun PreferenceValue.defaultValueForType(): Any = when (this) {
            is IntPreferenceValue -> 0
            is LongPreferenceValue -> 0L
            is FloatPreferenceValue -> 0f
            is StringPreferenceValue -> ""
            is BooleanPreferenceValue -> false
            is StringSetPreferenceValue -> emptySet<String>()
        }

        fun PreferenceValue.differentDefaultValueForType(): Any = when (this) {
            is IntPreferenceValue -> ""
            is LongPreferenceValue -> ""
            is FloatPreferenceValue -> ""
            is StringPreferenceValue -> 0
            is BooleanPreferenceValue -> ""
            is StringSetPreferenceValue -> ""
        }

        data class ComparableBackup(
            val manga: List<ComparableManga>,
            val categories: List<ComparableCategory>,
            val sources: List<BackupSource>,
            val preferences: List<BackupPreference>,
            val sourcePreferences: List<BackupSourcePreferences>,
        )

        data class ComparableManga(
            val source: Long,
            val url: String,
            val title: String,
            val chapters: List<ComparableChapter>,
            val categories: List<Long>,
            val tracking: List<BackupTracking>,
            val history: List<BackupHistory>,
        )

        data class ComparableChapter(
            val url: String,
            val name: String,
            val read: Boolean,
            val bookmark: Boolean,
            val lastPageRead: Long,
            val sourceOrder: Long,
            val chapterNumber: Float,
        )

        data class ComparableCategory(
            val name: String,
            val order: Long,
            val id: Long,
            val flags: Long,
        )
    }
}
