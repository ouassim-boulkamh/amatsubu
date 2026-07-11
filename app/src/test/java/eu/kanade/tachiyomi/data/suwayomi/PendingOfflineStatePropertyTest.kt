package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class PendingOfflineStatePropertyTest {

    @Test
    fun `generated offline histories preserve pending state through replay conflicts and restart`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(offlineHistoryStepArb, 1..80)) { history ->
            withDatabase { database ->
                var reads = ServerReadStatePendingStore(database)
                var intents = ServerReaderIntentPendingStore(database)
                val server = mutableMapOf<OfflineKey, ServerReaderIntentBaseline>()
                val expectedReads = mutableMapOf<OfflineKey, Boolean>()
                val expectedIntents = mutableMapOf<OfflineIntentKey, ExpectedIntent>()

                history.forEach { step ->
                    when (step) {
                        is OfflineHistoryStep.QueueRead -> {
                            server.baselineFor(step.key)
                            reads.upsert(step.key.serverKey, step.key.mangaId, step.key.chapterId, step.isRead)
                            expectedReads[step.key] = step.isRead
                        }
                        is OfflineHistoryStep.QueueProgress -> {
                            val baseline = server.baselineFor(step.key)
                            intents.upsertProgress(
                                step.key.serverKey,
                                step.key.mangaId,
                                step.key.chapterId,
                                baseline,
                                step.isRead,
                                step.lastPageRead,
                            )
                            expectedIntents[OfflineIntentKey(step.key, PendingServerReaderIntentType.PROGRESS)] =
                                ExpectedIntent(baseline, step.isRead, step.lastPageRead, null)
                        }
                        is OfflineHistoryStep.QueueBookmark -> {
                            val baseline = server.baselineFor(step.key)
                            intents.upsertBookmark(
                                step.key.serverKey,
                                step.key.mangaId,
                                step.key.chapterId,
                                baseline,
                                step.isBookmarked,
                            )
                            expectedIntents[OfflineIntentKey(step.key, PendingServerReaderIntentType.BOOKMARK)] =
                                ExpectedIntent(baseline, null, null, step.isBookmarked)
                        }
                        is OfflineHistoryStep.ExternalChange -> {
                            server[step.key] = ServerReaderIntentBaseline(
                                step.isRead,
                                step.lastPageRead,
                                step.isBookmarked,
                            )
                        }
                        OfflineHistoryStep.Replay -> {
                            val pendingReads = reads.getForServer("server-a")
                            replayPendingReadStates(
                                pendingReads,
                                updateChaptersRead = { ids, isRead ->
                                    ids.forEach { id ->
                                        val key = server.keys.single {
                                            it.serverKey == "server-a" && it.chapterId == id
                                        }
                                        server[key] = server.baselineFor(key).copy(isRead = isRead)
                                    }
                                },
                                deletePendingReadState = { reads.delete(it.serverKey, it.mangaId, it.chapterId) },
                            )
                            expectedReads.keys.removeAll { it.serverKey == "server-a" }

                            val serverBeforeIntentReplay = server.toMap()
                            val pendingIntents = intents.getForServer("server-a")
                            replayPendingReaderIntents(
                                pendingIntents,
                                currentBaseline = {
                                    server.baselineFor(OfflineKey(it.serverKey, it.mangaId, it.chapterId))
                                },
                                updateProgress = {
                                    val key = OfflineKey(it.serverKey, it.mangaId, it.chapterId)
                                    server[key] = server.baselineFor(key).copy(
                                        isRead = it.desiredIsRead ?: it.baseline.isRead,
                                        lastPageRead = it.desiredLastPageRead ?: it.baseline.lastPageRead,
                                    )
                                },
                                updateBookmark = {
                                    val key = OfflineKey(it.serverKey, it.mangaId, it.chapterId)
                                    server[key] = server.baselineFor(key).copy(
                                        isBookmarked = it.desiredIsBookmarked ?: it.baseline.isBookmarked,
                                    )
                                },
                                deletePendingIntent = { intents.delete(it) },
                            )
                            expectedIntents.entries.removeAll { (key, expected) ->
                                if (key.key.serverKey != "server-a") return@removeAll false
                                val current = serverBeforeIntentReplay.getValue(key.key)
                                val conflicted = when (key.type) {
                                    PendingServerReaderIntentType.PROGRESS -> {
                                        current.isRead != expected.baseline.isRead ||
                                            current.lastPageRead != expected.baseline.lastPageRead
                                    }
                                    PendingServerReaderIntentType.BOOKMARK ->
                                        current.isBookmarked !=
                                            expected.baseline.isBookmarked
                                }
                                if (!conflicted) {
                                    server[key.key] = when (key.type) {
                                        PendingServerReaderIntentType.PROGRESS -> current.copy(
                                            isRead = expected.isRead!!,
                                            lastPageRead = expected.lastPageRead!!,
                                        )
                                        PendingServerReaderIntentType.BOOKMARK -> {
                                            current.copy(isBookmarked = expected.isBookmarked!!)
                                        }
                                    }
                                }
                                !conflicted
                            }
                        }
                        is OfflineHistoryStep.DeleteRead -> {
                            reads.delete(step.key.serverKey, step.key.mangaId, step.key.chapterId)
                            expectedReads.remove(step.key)
                        }
                        OfflineHistoryStep.Restart -> {
                            reads = ServerReadStatePendingStore(database)
                            intents = ServerReaderIntentPendingStore(database)
                        }
                    }
                    assertOfflineModel(reads, intents, expectedReads, expectedIntents)
                }
            }
        }
    }

    @Test
    fun `generated pending read-state operations are idempotent and server scoped`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(readOperationArb, 1..40)) { operations ->
            withDatabase { database ->
                val store = ServerReadStatePendingStore(database)
                val expected = mutableMapOf<ReadKey, Boolean>()

                operations.forEach { operation ->
                    val key = ReadKey(operation.serverKey, operation.mangaId, operation.chapterId)
                    when (operation) {
                        is ReadOperation.Upsert -> {
                            store.upsert(key.serverKey, key.mangaId, key.chapterId, operation.isRead)
                            expected[key] = operation.isRead
                        }
                        is ReadOperation.Delete -> {
                            store.delete(key.serverKey, key.mangaId, key.chapterId)
                            expected.remove(key)
                        }
                    }
                }

                expected.entries.groupBy { it.key.serverKey }.forEach { (serverKey, entries) ->
                    val actual = store.getForServer(serverKey).associate { state ->
                        ReadKey(state.serverKey, state.mangaId, state.chapterId) to state.isRead
                    }
                    assertEquals(entries.associate { it.key to it.value }, actual)
                    assertEquals(entries.size.toLong(), store.count(serverKey))
                }
                listOf("server-a", "server-b", "server-c").forEach { serverKey ->
                    val expectedForServer = expected.filterKeys { it.serverKey == serverKey }
                    assertEquals(expectedForServer.size.toLong(), store.count(serverKey))
                }
            }
        }
    }

    @Test
    fun `generated reader intents replace only matching server chapter and intent type`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(readerIntentOperationArb, 1..32)) { operations ->
            withDatabase { database ->
                val store = ServerReaderIntentPendingStore(database)
                val expected = mutableMapOf<IntentKey, ExpectedIntent>()

                operations.forEach { operation ->
                    val key = IntentKey(operation.serverKey, operation.mangaId, operation.chapterId, operation.type)
                    when (operation) {
                        is ReaderIntentOperation.Progress -> {
                            store.upsertProgress(
                                operation.serverKey,
                                operation.mangaId,
                                operation.chapterId,
                                operation.baseline,
                                operation.desiredIsRead,
                                operation.desiredLastPageRead,
                            )
                            expected[key] = ExpectedIntent(
                                operation.baseline,
                                operation.desiredIsRead,
                                operation.desiredLastPageRead,
                                null,
                            )
                        }
                        is ReaderIntentOperation.Bookmark -> {
                            store.upsertBookmark(
                                operation.serverKey,
                                operation.mangaId,
                                operation.chapterId,
                                operation.baseline,
                                operation.desiredIsBookmarked,
                            )
                            expected[key] = ExpectedIntent(
                                operation.baseline,
                                null,
                                null,
                                operation.desiredIsBookmarked,
                            )
                        }
                    }
                }

                listOf("server-a", "server-b", "server-c").forEach { serverKey ->
                    val actual = store.getForServer(serverKey).associate { intent ->
                        IntentKey(intent.serverKey, intent.mangaId, intent.chapterId, intent.type) to
                            ExpectedIntent(
                                intent.baseline,
                                intent.desiredIsRead,
                                intent.desiredLastPageRead,
                                intent.desiredIsBookmarked,
                            )
                    }
                    assertEquals(expected.filterKeys { it.serverKey == serverKey }, actual)
                    assertEquals(actual.size.toLong(), store.count(serverKey))
                }

                expected.keys.firstOrNull()?.let { key ->
                    val intent = store.getForServer(key.serverKey).single {
                        it.mangaId == key.mangaId &&
                            it.chapterId == key.chapterId &&
                            it.type == key.type
                    }
                    store.delete(intent)
                    assertNull(
                        store.getForServer(key.serverKey).firstOrNull {
                            it.mangaId == key.mangaId &&
                                it.chapterId == key.chapterId &&
                                it.type == key.type
                        },
                    )
                    expected
                        .filterKeys { it != key }
                        .keys
                        .forEach { retainedKey ->
                            assertTrue(
                                store.getForServer(retainedKey.serverKey).any { retained ->
                                    retained.mangaId == retainedKey.mangaId &&
                                        retained.chapterId == retainedKey.chapterId &&
                                        retained.type == retainedKey.type
                                },
                            )
                        }
                }
            }
        }
    }

    @Test
    fun `generated client-copy pages persist in source index order and remain server scoped`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), clientCopyInputArb) { input ->
            withDatabase { database ->
                val store = ClientDeviceChapterCopyStore(database)
                val first = input.toUpsert(serverKey = "server-a")
                val second = input.copy(chapterId = input.chapterId + 10_000).toUpsert(serverKey = "server-b")

                store.upsert(first)
                store.upsert(second)
                val stored = requireNotNull(store.getCopy("server-a", input.mangaId, input.chapterId))

                assertEquals(input.pageUrls.indices.toList(), stored.pages.map { it.index })
                assertEquals(input.pageUrls, stored.pages.map { it.sourceUrl })
                assertEquals(input.pageUrls.size, stored.expectedPageCount)
                assertEquals(input.pageUrls.count { it.isNotEmpty() }, stored.downloadedPageCount)
                assertEquals(1, store.getCopiesForServer("server-a").size)
                assertEquals(1, store.getCopiesForServer("server-b").size)

                store.deleteCopy("server-a", input.mangaId, input.chapterId)
                assertNull(store.getCopy("server-a", input.mangaId, input.chapterId))
                assertTrue(store.getCopy("server-b", input.mangaId, input.chapterId + 10_000) != null)
            }
        }
    }

    @Test
    suspend fun `generated orphan classification only marks copies absent from known server state`() {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.int(1..1000),
            Arb.int(1..1000),
            Arb.list(Arb.int(1..1000), 0..8),
            Arb.list(Arb.int(1..1000), 0..8),
            Arb.boolean(),
        ) { mangaId, chapterId, libraryIds, chapterIds, chaptersKnown ->
            val copy = copy(mangaId = mangaId, chapterId = chapterId)
            val actual = isClientDeviceChapterCopyOrphan(
                copy,
                libraryIds.toSet(),
                chapterIds.toSet().takeIf { chaptersKnown },
            )
            val expected = mangaId !in libraryIds || (chaptersKnown && chapterId !in chapterIds)
            assertEquals(expected, actual)
        }
    }

    private suspend fun withDatabase(block: suspend (Database) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            block(Database(driver))
        } finally {
            driver.close()
        }
    }

    private suspend fun assertOfflineModel(
        reads: ServerReadStatePendingStore,
        intents: ServerReaderIntentPendingStore,
        expectedReads: Map<OfflineKey, Boolean>,
        expectedIntents: Map<OfflineIntentKey, ExpectedIntent>,
    ) {
        listOf("server-a", "server-b").forEach { serverKey ->
            val actualReads = reads.getForServer(serverKey).associate {
                OfflineKey(it.serverKey, it.mangaId, it.chapterId) to it.isRead
            }
            assertEquals(expectedReads.filterKeys { it.serverKey == serverKey }, actualReads)
            val actualIntents = intents.getForServer(serverKey).associate {
                OfflineIntentKey(OfflineKey(it.serverKey, it.mangaId, it.chapterId), it.type) to
                    ExpectedIntent(it.baseline, it.desiredIsRead, it.desiredLastPageRead, it.desiredIsBookmarked)
            }
            assertEquals(expectedIntents.filterKeys { it.key.serverKey == serverKey }, actualIntents)
        }
    }
}

private data class OfflineKey(val serverKey: String, val mangaId: Int, val chapterId: Int)

private data class OfflineIntentKey(val key: OfflineKey, val type: PendingServerReaderIntentType)

private sealed interface OfflineHistoryStep {
    data class QueueRead(val key: OfflineKey, val isRead: Boolean) : OfflineHistoryStep
    data class QueueProgress(val key: OfflineKey, val isRead: Boolean, val lastPageRead: Int) : OfflineHistoryStep
    data class QueueBookmark(val key: OfflineKey, val isBookmarked: Boolean) : OfflineHistoryStep
    data class ExternalChange(
        val key: OfflineKey,
        val isRead: Boolean,
        val lastPageRead: Int,
        val isBookmarked: Boolean,
    ) : OfflineHistoryStep
    data class DeleteRead(val key: OfflineKey) : OfflineHistoryStep
    data object Replay : OfflineHistoryStep
    data object Restart : OfflineHistoryStep
}

private fun MutableMap<OfflineKey, ServerReaderIntentBaseline>.baselineFor(
    key: OfflineKey,
): ServerReaderIntentBaseline {
    return getOrPut(key) { ServerReaderIntentBaseline(isRead = false, lastPageRead = 0, isBookmarked = false) }
}

private val offlineHistoryStepArb = Arb.bind(
    Arb.int(0..6),
    Arb.int(0..1),
    Arb.int(1..5),
    Arb.int(1..25),
    Arb.boolean(),
    Arb.int(0..500),
    Arb.boolean(),
) { kind, serverIndex, mangaId, chapterOffset, isRead, lastPageRead, isBookmarked ->
    val key = OfflineKey(
        serverKey = listOf("server-a", "server-b")[serverIndex],
        mangaId = mangaId,
        chapterId = mangaId * 100 + chapterOffset,
    )
    when (kind) {
        0 -> OfflineHistoryStep.QueueRead(key, isRead)
        1 -> OfflineHistoryStep.QueueProgress(key, isRead, lastPageRead)
        2 -> OfflineHistoryStep.QueueBookmark(key, isBookmarked)
        3 -> OfflineHistoryStep.ExternalChange(key, isRead, lastPageRead, isBookmarked)
        4 -> OfflineHistoryStep.DeleteRead(key)
        5 -> OfflineHistoryStep.Replay
        else -> OfflineHistoryStep.Restart
    }
}

private sealed interface ReadOperation {
    val serverKey: String
    val mangaId: Int
    val chapterId: Int

    data class Upsert(
        override val serverKey: String,
        override val mangaId: Int,
        override val chapterId: Int,
        val isRead: Boolean,
    ) : ReadOperation

    data class Delete(
        override val serverKey: String,
        override val mangaId: Int,
        override val chapterId: Int,
    ) : ReadOperation
}

private data class ReadKey(val serverKey: String, val mangaId: Int, val chapterId: Int)

private val readOperationArb = Arb.bind(
    Arb.int(0..2),
    Arb.int(1..8),
    Arb.int(1..16),
    Arb.boolean(),
    Arb.boolean(),
) {
        serverIndex,
        mangaId,
        chapterId,
        isRead,
        isDelete,
    ->
    val serverKey = listOf("server-a", "server-b", "server-c")[serverIndex]
    return@bind if (isDelete) {
        ReadOperation.Delete(serverKey, mangaId, chapterId)
    } else {
        ReadOperation.Upsert(serverKey, mangaId, chapterId, isRead)
    }
}

private sealed interface ReaderIntentOperation {
    val serverKey: String
    val mangaId: Int
    val chapterId: Int
    val type: PendingServerReaderIntentType
    val baseline: ServerReaderIntentBaseline

    data class Progress(
        override val serverKey: String,
        override val mangaId: Int,
        override val chapterId: Int,
        override val baseline: ServerReaderIntentBaseline,
        val desiredIsRead: Boolean,
        val desiredLastPageRead: Int,
    ) : ReaderIntentOperation {
        override val type = PendingServerReaderIntentType.PROGRESS
    }

    data class Bookmark(
        override val serverKey: String,
        override val mangaId: Int,
        override val chapterId: Int,
        override val baseline: ServerReaderIntentBaseline,
        val desiredIsBookmarked: Boolean,
    ) : ReaderIntentOperation {
        override val type = PendingServerReaderIntentType.BOOKMARK
    }
}

private data class IntentKey(
    val serverKey: String,
    val mangaId: Int,
    val chapterId: Int,
    val type: PendingServerReaderIntentType,
)

private data class ExpectedIntent(
    val baseline: ServerReaderIntentBaseline,
    val isRead: Boolean?,
    val lastPageRead: Int?,
    val isBookmarked: Boolean?,
)

private val readerIntentOperationArb = Arb.bind(
    Arb.int(0..2),
    Arb.int(1..8),
    Arb.int(1..16),
    Arb.boolean(),
    Arb.boolean(),
    Arb.int(0..500),
    Arb.boolean(),
) { serverIndex, mangaId, chapterId, baselineRead, baselineBookmarked, lastPage, bookmark ->
    val serverKey = listOf("server-a", "server-b", "server-c")[serverIndex]
    val baseline = ServerReaderIntentBaseline(baselineRead, lastPage, baselineBookmarked)
    if (bookmark) {
        ReaderIntentOperation.Bookmark(serverKey, mangaId, chapterId, baseline, !baselineBookmarked)
    } else {
        ReaderIntentOperation.Progress(serverKey, mangaId, chapterId, baseline, !baselineRead, lastPage + 1)
    }
}

private data class ClientCopyInput(val mangaId: Int, val chapterId: Int, val pageUrls: List<String>) {
    fun toUpsert(serverKey: String): ClientDeviceChapterCopyUpsert {
        val manifest = SuwayomiChapterPageManifest(
            pages = pageUrls,
            chapter = SuwayomiChapterDto(
                id = chapterId,
                mangaId = mangaId,
                name = "Chapter $chapterId",
                url = "/chapter/$chapterId",
                sourceOrder = 1,
                chapterNumber = 1f,
                uploadDate = 1L,
                fetchedAt = "1",
                pageCount = pageUrls.size,
            ),
        )
        return ClientDeviceChapterCopyUpsert(
            serverKey,
            "Manga $mangaId",
            manifest,
            null,
            buildClientDeviceCopyPages(manifest)
                .reversed()
                .map { page -> page.copy(localUri = "file:///copy/${page.index}", isPresent = true) },
            ClientChapterCopyStatus.COMPLETE,
            ClientChapterCopyFreshness.FRESH,
        )
    }
}

private val clientCopyInputArb = Arb.bind(
    Arb.int(1..1000),
    Arb.int(1..1000),
    Arb.list(Arb.string(1..20), 0..12),
) { mangaId, chapterId, pages ->
    ClientCopyInput(mangaId, chapterId, pages.mapIndexed { index, value -> "https://pages.test/$index/$value" })
}

private fun copy(mangaId: Int, chapterId: Int) = ClientDeviceChapterCopy(
    "server", mangaId, chapterId, null, "Chapter", "/chapter", null, 0, 0f, 0L, "0", null, null, "hash",
    ClientChapterCopyStatus.COMPLETE, ClientChapterCopyFreshness.FRESH, 0, 0, 0L, 0L, null, null,
)
