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
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.BackupCompatibilityDecisionType
import eu.kanade.tachiyomi.data.backup.restore.BackupCompatibilityPolicy
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.MemoColumnAdapter
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BackupModelCompatibilityTest {

    @Test
    fun `legacy backups without memo fields decode with empty json memo`() {
        val legacyBackup = LegacyBackup(
            backupManga = listOf(
                LegacyBackupManga(
                    source = 1,
                    url = "/manga",
                    title = "Legacy manga",
                    chapters = listOf(
                        LegacyBackupChapter(
                            url = "/chapter",
                            name = "Legacy chapter",
                        ),
                    ),
                ),
            ),
        )

        val decoded = ProtoBuf.decodeFromByteArray<Backup>(
            ProtoBuf.encodeToByteArray(legacyBackup),
        )

        val manga = decoded.backupManga.single()
        val chapter = manga.chapters.single()
        assertEquals(JsonObject.EMPTY, MemoColumnAdapter.decode(manga.memo))
        assertEquals(JsonObject.EMPTY, MemoColumnAdapter.decode(chapter.memo))
    }

    @Test
    fun `Mihon extension store backup field decodes as ignored unknown section`() {
        val mihonBackup = MihonBackupWithExtensionStores(
            backupManga = emptyList(),
            backupExtensionStores = listOf(
                MihonBackupExtensionStore(
                    indexUrl = "https://store.example/index.min.json",
                    name = "Example store",
                    badgeLabel = "Example",
                    signingKey = "abc",
                    contactWebsite = "https://store.example",
                    contactDiscord = null,
                    isLegacy = false,
                    extensionListUrl = "https://store.example/extensions.min.json",
                ),
            ),
        )

        val decoded = ProtoBuf.decodeFromByteArray<Backup>(
            ProtoBuf.encodeToByteArray(mihonBackup),
        )

        assertEquals(0, decoded.backupManga.size)
        assertEquals(0, decoded.backupCategories.size)
        assertEquals(0, decoded.backupSources.size)
        assertEquals(0, decoded.backupPreferences.size)
        assertEquals(0, decoded.backupSourcePreferences.size)
    }

    @Test
    fun `Mihon backup with app preferences restores settings while ignoring library data`() {
        val mihonBackup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 1,
                    url = "/manga",
                    title = "Library manga",
                    chapters = listOf(BackupChapter(url = "/chapter", name = "Chapter 1")),
                    categories = listOf(1),
                    tracking = listOf(BackupTracking(syncId = 1, libraryId = 1)),
                    history = listOf(BackupHistory(url = "/chapter", lastRead = 1)),
                ),
            ),
            backupCategories = listOf(BackupCategory(name = "Default")),
            backupSources = listOf(BackupSource(name = "Source", sourceId = 1)),
            backupPreferences = listOf(
                BackupPreference("theme_mode", StringPreferenceValue("dark")),
                BackupPreference("reader_mode", IntPreferenceValue(2)),
                BackupPreference("download_new", BooleanPreferenceValue(true)),
            ),
            backupSourcePreferences = listOf(
                BackupSourcePreferences(
                    sourceKey = "source_1",
                    prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(true))),
                ),
            ),
        )

        val decoded = BackupDecoder.decodeBackupBytes(gzip(BackupCreator.encodeBackup(mihonBackup)))
        val result = BackupCompatibilityPolicy(
            appPreferences = mapOf(
                "theme_mode" to "system",
                "reader_mode" to 1,
                "download_new" to false,
            ),
            sourcePreferences = mapOf(
                "source_1" to mapOf("enabled" to false),
            ),
        ).evaluate(decoded, RestoreOptions(appSettings = true, sourceSettings = true))

        assertEquals(listOf("theme_mode", "reader_mode"), result.restorable.appPreferences.map { it.key })
        assertEquals(listOf("source_1"), result.restorable.sourcePreferences.map { it.sourceKey })
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupManga" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupCategories" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:download_new" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(bytes)
            }
            output.toByteArray()
        }
    }

    @Serializable
    private data class LegacyBackup(
        @ProtoNumber(1) val backupManga: List<LegacyBackupManga>,
    )

    @Serializable
    private data class MihonBackupWithExtensionStores(
        @ProtoNumber(1) val backupManga: List<LegacyBackupManga>,
        @ProtoNumber(106) val backupExtensionStores: List<MihonBackupExtensionStore>,
    )

    @Serializable
    private data class MihonBackupExtensionStore(
        @ProtoNumber(1) val indexUrl: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val badgeLabel: String?,
        @ProtoNumber(5) val signingKey: String,
        @ProtoNumber(4) val contactWebsite: String,
        @ProtoNumber(6) val contactDiscord: String?,
        @ProtoNumber(7) val isLegacy: Boolean?,
        @ProtoNumber(8) val extensionListUrl: String?,
    )

    @Serializable
    private data class LegacyBackupManga(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
        @ProtoNumber(3) var title: String = "",
        @ProtoNumber(16) var chapters: List<LegacyBackupChapter> = emptyList(),
    )

    @Serializable
    private data class LegacyBackupChapter(
        @ProtoNumber(1) var url: String,
        @ProtoNumber(2) var name: String,
    )
}
