package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class ReadStateReplayOperationalTest {

    @Test
    fun `read-state replay survives outage restart and server switch without crossing identities`() = runTest {
        withDatabase { database ->
            val serverA = "http://server-a.test/api/graphql"
            val serverB = "http://server-b.test/api/graphql"
            var reads = ServerReadStatePendingStore(database)
            var intents = ServerReaderIntentPendingStore(database)
            val serverAState = mutableMapOf(
                101 to ServerReaderIntentBaseline(isRead = false, lastPageRead = 3, isBookmarked = false),
                102 to ServerReaderIntentBaseline(isRead = false, lastPageRead = 0, isBookmarked = false),
            )
            val serverBState = mutableMapOf(
                101 to ServerReaderIntentBaseline(isRead = false, lastPageRead = 0, isBookmarked = false),
                102 to ServerReaderIntentBaseline(isRead = false, lastPageRead = 0, isBookmarked = false),
            )

            reads.upsert(serverA, mangaId = 10, chapterId = 102, isRead = true)
            intents.upsertProgress(
                serverKey = serverA,
                mangaId = 10,
                chapterId = 101,
                baseline = serverAState.getValue(101),
                desiredIsRead = true,
                desiredLastPageRead = 12,
            )

            runCatching {
                replayPendingReadStates(
                    pending = reads.getForServer(serverA),
                    updateChaptersRead = { _, _ -> error("server outage") },
                    deletePendingReadState = { reads.delete(it.serverKey, it.mangaId, it.chapterId) },
                )
            }
            assertEquals(1, reads.count(serverA))
            assertEquals(1, intents.count(serverA))

            reads = ServerReadStatePendingStore(database)
            intents = ServerReaderIntentPendingStore(database)

            val pushedOnSwitchedServer = replayPendingReadStates(
                pending = reads.getForServer(serverB),
                updateChaptersRead = { chapterIds, isRead ->
                    chapterIds.forEach { chapterId ->
                        serverBState[chapterId] = serverBState.getValue(chapterId).copy(isRead = isRead)
                    }
                },
                deletePendingReadState = { reads.delete(it.serverKey, it.mangaId, it.chapterId) },
            )
            val intentReplayOnSwitchedServer = replayPendingReaderIntents(
                pending = intents.getForServer(serverB),
                currentBaseline = { serverBState.getValue(it.chapterId) },
                updateProgress = { error("switched server must not receive original server progress") },
                updateBookmark = { error("switched server must not receive original server bookmark") },
                deletePendingIntent = { intents.delete(it) },
            )

            assertEquals(0, pushedOnSwitchedServer)
            assertEquals(0, intentReplayOnSwitchedServer.pushed)
            assertEquals(ServerReaderIntentBaseline(false, 0, false), serverBState.getValue(101))
            assertEquals(ServerReaderIntentBaseline(false, 0, false), serverBState.getValue(102))
            assertEquals(1, reads.count(serverA))
            assertEquals(1, intents.count(serverA))

            val pushedOnOriginalServer = replayPendingReadStates(
                pending = reads.getForServer(serverA),
                updateChaptersRead = { chapterIds, isRead ->
                    chapterIds.forEach { chapterId ->
                        serverAState[chapterId] = serverAState.getValue(chapterId).copy(isRead = isRead)
                    }
                },
                deletePendingReadState = { reads.delete(it.serverKey, it.mangaId, it.chapterId) },
            )
            val intentReplayOnOriginalServer = replayPendingReaderIntents(
                pending = intents.getForServer(serverA),
                currentBaseline = { serverAState.getValue(it.chapterId) },
                updateProgress = {
                    serverAState[it.chapterId] = serverAState.getValue(it.chapterId).copy(
                        isRead = requireNotNull(it.desiredIsRead),
                        lastPageRead = requireNotNull(it.desiredLastPageRead),
                    )
                },
                updateBookmark = { error("bookmark should not be updated") },
                deletePendingIntent = { intents.delete(it) },
            )

            assertEquals(1, pushedOnOriginalServer)
            assertEquals(1, intentReplayOnOriginalServer.pushed)
            assertEquals(ServerReaderIntentBaseline(true, 12, false), serverAState.getValue(101))
            assertEquals(ServerReaderIntentBaseline(true, 0, false), serverAState.getValue(102))
            assertEquals(0, reads.count(serverA))
            assertEquals(0, intents.count(serverA))
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
}
