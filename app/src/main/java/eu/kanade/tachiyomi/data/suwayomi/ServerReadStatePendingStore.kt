package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import tachiyomi.data.Database

data class PendingServerReadState(
    val serverKey: String,
    val mangaId: Int,
    val chapterId: Int,
    val isRead: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

class ServerReadStatePendingStore(
    private val database: Database,
) {
    suspend fun upsert(serverKey: String, mangaId: Int, chapterId: Int, isRead: Boolean) {
        val now = System.currentTimeMillis()
        database.server_read_state_pendingQueries.upsertPendingReadState(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
            isRead = if (isRead) 1L else 0L,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun getForServer(serverKey: String): List<PendingServerReadState> {
        return database.server_read_state_pendingQueries
            .getPendingReadStatesForServer(serverKey)
            .awaitAsList()
            .map {
                PendingServerReadState(
                    serverKey = it.server_key,
                    mangaId = it.manga_id.toInt(),
                    chapterId = it.chapter_id.toInt(),
                    isRead = it.is_read != 0L,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at,
                )
            }
    }

    suspend fun getForManga(serverKey: String, mangaId: Int): List<PendingServerReadState> {
        return database.server_read_state_pendingQueries
            .getPendingReadStatesForManga(serverKey, mangaId.toLong())
            .awaitAsList()
            .map {
                PendingServerReadState(
                    serverKey = it.server_key,
                    mangaId = it.manga_id.toInt(),
                    chapterId = it.chapter_id.toInt(),
                    isRead = it.is_read != 0L,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at,
                )
            }
    }

    suspend fun count(serverKey: String): Long {
        return database.server_read_state_pendingQueries.countPendingReadStates(serverKey).awaitAsOne()
    }

    suspend fun delete(serverKey: String, mangaId: Int, chapterId: Int) {
        database.server_read_state_pendingQueries.deletePendingReadState(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
        )
    }
}
