package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class ServerReadStatePendingStoreTest {

    @Test
    fun `pending read states are isolated by server key`() = runStoreTest { store ->
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = true)
        store.upsert(serverKey = "server-b", mangaId = 1, chapterId = 10, isRead = false)

        val serverA = store.getForServer("server-a")
        val serverB = store.getForServer("server-b")

        assertEquals(1, store.count("server-a"))
        assertEquals(1, store.count("server-b"))
        assertEquals(listOf("server-a"), serverA.map { it.serverKey })
        assertEquals(listOf("server-b"), serverB.map { it.serverKey })
        assertTrue(serverA.single().isRead)
        assertFalse(serverB.single().isRead)
    }

    @Test
    fun `pending read states can be grouped by manga within a server`() = runStoreTest { store ->
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = true)
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 11, isRead = false)
        store.upsert(serverKey = "server-a", mangaId = 2, chapterId = 20, isRead = true)
        store.upsert(serverKey = "server-b", mangaId = 1, chapterId = 12, isRead = true)

        val grouped = store.getForManga(serverKey = "server-a", mangaId = 1)

        assertEquals(listOf(10, 11), grouped.map { it.chapterId })
        assertEquals(listOf(1, 1), grouped.map { it.mangaId })
        assertEquals(listOf("server-a", "server-a"), grouped.map { it.serverKey })
    }

    @Test
    fun `accepted replay deletion removes only the matching server manga and chapter`() = runStoreTest { store ->
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = true)
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 11, isRead = true)
        store.upsert(serverKey = "server-b", mangaId = 1, chapterId = 10, isRead = true)

        store.delete(serverKey = "server-a", mangaId = 1, chapterId = 10)

        assertEquals(listOf(11), store.getForServer("server-a").map { it.chapterId })
        assertEquals(listOf(10), store.getForServer("server-b").map { it.chapterId })
    }

    @Test
    fun `pending rows are retained for retry until explicitly deleted`() = runStoreTest { store ->
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = true)
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 11, isRead = true)

        store.delete(serverKey = "server-a", mangaId = 1, chapterId = 10)

        val retained = store.getForServer("server-a")
        assertEquals(listOf(11), retained.map { it.chapterId })
        assertTrue(retained.single().isRead)
        assertEquals(1, store.count("server-a"))
    }

    @Test
    fun `upserting the same chapter replaces desired read state without duplicating rows`() = runStoreTest { store ->
        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = true)
        val initial = store.getForServer("server-a").single()

        store.upsert(serverKey = "server-a", mangaId = 1, chapterId = 10, isRead = false)

        val updated = store.getForServer("server-a").single()
        assertEquals(1, store.count("server-a"))
        assertFalse(updated.isRead)
        assertEquals(initial.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt >= initial.updatedAt)
    }

    private fun runStoreTest(block: suspend (ServerReadStatePendingStore) -> Unit) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            block(ServerReadStatePendingStore(Database(driver)))
        } finally {
            driver.close()
        }
    }
}
