package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class ServerReaderIntentPendingStoreTest {

    @Test
    fun `pending reader intents are isolated by server key`() = runStoreTest { store ->
        store.upsertProgress(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsRead = true,
            desiredLastPageRead = 12,
        )
        store.upsertBookmark(
            serverKey = "server-b",
            mangaId = 1,
            chapterId = 10,
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsBookmarked = true,
        )

        assertEquals(1, store.count("server-a"))
        assertEquals(1, store.count("server-b"))
        assertEquals(listOf("server-a"), store.getForServer("server-a").map { it.serverKey })
        assertEquals(listOf("server-b"), store.getForServer("server-b").map { it.serverKey })
    }

    @Test
    fun `progress intent captures baseline and desired server state`() = runStoreTest { store ->
        store.upsertProgress(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            baseline = baseline(isRead = false, lastPageRead = 4, isBookmarked = true),
            desiredIsRead = true,
            desiredLastPageRead = 20,
        )

        val pending = store.getForServer("server-a").single()

        assertEquals(PendingServerReaderIntentType.PROGRESS, pending.type)
        assertEquals(baseline(isRead = false, lastPageRead = 4, isBookmarked = true), pending.baseline)
        assertTrue(pending.desiredIsRead!!)
        assertEquals(20, pending.desiredLastPageRead)
        assertEquals(null, pending.desiredIsBookmarked)
    }

    @Test
    fun `bookmark intent captures baseline and desired server state`() = runStoreTest { store ->
        store.upsertBookmark(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            baseline = baseline(isRead = true, lastPageRead = 9, isBookmarked = false),
            desiredIsBookmarked = true,
        )

        val pending = store.getForServer("server-a").single()

        assertEquals(PendingServerReaderIntentType.BOOKMARK, pending.type)
        assertEquals(baseline(isRead = true, lastPageRead = 9, isBookmarked = false), pending.baseline)
        assertEquals(null, pending.desiredIsRead)
        assertEquals(null, pending.desiredLastPageRead)
        assertTrue(pending.desiredIsBookmarked!!)
    }

    @Test
    fun `upserting same intent replaces desired state without duplicating rows`() = runStoreTest { store ->
        val firstBaseline = baseline(isRead = false, lastPageRead = 1, isBookmarked = false)
        val secondBaseline = baseline(isRead = false, lastPageRead = 5, isBookmarked = false)
        store.upsertProgress(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            baseline = firstBaseline,
            desiredIsRead = false,
            desiredLastPageRead = 2,
        )
        val initial = store.getForServer("server-a").single()

        store.upsertProgress(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            baseline = secondBaseline,
            desiredIsRead = true,
            desiredLastPageRead = 6,
        )

        val updated = store.getForServer("server-a").single()
        assertEquals(1, store.count("server-a"))
        assertEquals(initial.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt >= initial.updatedAt)
        assertEquals(secondBaseline, updated.baseline)
        assertTrue(updated.desiredIsRead!!)
        assertEquals(6, updated.desiredLastPageRead)
    }

    @Test
    fun `accepted replay deletion removes only matching intent type`() = runStoreTest { store ->
        val baseline = baseline(isRead = false, lastPageRead = 0, isBookmarked = false)
        store.upsertProgress("server-a", 1, 10, baseline, desiredIsRead = true, desiredLastPageRead = 4)
        store.upsertBookmark("server-a", 1, 10, baseline, desiredIsBookmarked = true)

        val progress = store.getForServer("server-a")
            .single { it.type == PendingServerReaderIntentType.PROGRESS }
        store.delete(progress)

        val retained = store.getForServer("server-a").single()
        assertEquals(PendingServerReaderIntentType.BOOKMARK, retained.type)
    }

    private fun runStoreTest(block: suspend (ServerReaderIntentPendingStore) -> Unit) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            block(ServerReaderIntentPendingStore(Database(driver)))
        } finally {
            driver.close()
        }
    }

    private fun baseline(
        isRead: Boolean,
        lastPageRead: Int,
        isBookmarked: Boolean,
    ): ServerReaderIntentBaseline {
        return ServerReaderIntentBaseline(
            isRead = isRead,
            lastPageRead = lastPageRead,
            isBookmarked = isBookmarked,
        )
    }
}
