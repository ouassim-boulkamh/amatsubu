package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import tachiyomi.data.Database

enum class PendingServerReaderIntentType {
    PROGRESS,
    BOOKMARK,
}

data class ServerReaderIntentBaseline(
    val isRead: Boolean,
    val lastPageRead: Int,
    val isBookmarked: Boolean,
)

data class PendingServerReaderIntent(
    val serverKey: String,
    val mangaId: Int,
    val chapterId: Int,
    val type: PendingServerReaderIntentType,
    val baseline: ServerReaderIntentBaseline,
    val desiredIsRead: Boolean?,
    val desiredLastPageRead: Int?,
    val desiredIsBookmarked: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
)

class ServerReaderIntentPendingStore(
    private val database: Database,
) {
    suspend fun upsertProgress(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        baseline: ServerReaderIntentBaseline,
        desiredIsRead: Boolean,
        desiredLastPageRead: Int,
    ) {
        upsert(
            serverKey = serverKey,
            mangaId = mangaId,
            chapterId = chapterId,
            type = PendingServerReaderIntentType.PROGRESS,
            baseline = baseline,
            desiredIsRead = desiredIsRead,
            desiredLastPageRead = desiredLastPageRead,
            desiredIsBookmarked = null,
        )
    }

    suspend fun upsertBookmark(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        baseline: ServerReaderIntentBaseline,
        desiredIsBookmarked: Boolean,
    ) {
        upsert(
            serverKey = serverKey,
            mangaId = mangaId,
            chapterId = chapterId,
            type = PendingServerReaderIntentType.BOOKMARK,
            baseline = baseline,
            desiredIsRead = null,
            desiredLastPageRead = null,
            desiredIsBookmarked = desiredIsBookmarked,
        )
    }

    suspend fun getForServer(serverKey: String): List<PendingServerReaderIntent> {
        return database.server_reader_intent_pendingQueries
            .getPendingReaderIntentsForServer(serverKey)
            .awaitAsList()
            .map { it.toPendingServerReaderIntent() }
    }

    suspend fun getForManga(serverKey: String, mangaId: Int): List<PendingServerReaderIntent> {
        return database.server_reader_intent_pendingQueries
            .getPendingReaderIntentsForManga(serverKey, mangaId.toLong())
            .awaitAsList()
            .map { it.toPendingServerReaderIntent() }
    }

    suspend fun count(serverKey: String): Long {
        return database.server_reader_intent_pendingQueries.countPendingReaderIntents(serverKey).awaitAsOne()
    }

    suspend fun delete(intent: PendingServerReaderIntent) {
        database.server_reader_intent_pendingQueries.deletePendingReaderIntent(
            serverKey = intent.serverKey,
            mangaId = intent.mangaId.toLong(),
            chapterId = intent.chapterId.toLong(),
            intentType = intent.type.name,
        )
    }

    private suspend fun upsert(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        type: PendingServerReaderIntentType,
        baseline: ServerReaderIntentBaseline,
        desiredIsRead: Boolean?,
        desiredLastPageRead: Int?,
        desiredIsBookmarked: Boolean?,
    ) {
        val now = System.currentTimeMillis()
        database.server_reader_intent_pendingQueries.upsertPendingReaderIntent(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
            intentType = type.name,
            baselineIsRead = baseline.isRead.toLong(),
            baselineLastPageRead = baseline.lastPageRead.toLong(),
            baselineIsBookmarked = baseline.isBookmarked.toLong(),
            desiredIsRead = desiredIsRead?.toLong(),
            desiredLastPageRead = desiredLastPageRead?.toLong(),
            desiredIsBookmarked = desiredIsBookmarked?.toLong(),
            createdAt = now,
            updatedAt = now,
        )
    }
}

private fun Boolean.toLong(): Long = if (this) 1L else 0L

private fun tachiyomi.data.Server_reader_intent_pending.toPendingServerReaderIntent(): PendingServerReaderIntent {
    return PendingServerReaderIntent(
        serverKey = server_key,
        mangaId = manga_id.toInt(),
        chapterId = chapter_id.toInt(),
        type = PendingServerReaderIntentType.valueOf(intent_type),
        baseline = ServerReaderIntentBaseline(
            isRead = baseline_is_read != 0L,
            lastPageRead = baseline_last_page_read.toInt(),
            isBookmarked = baseline_is_bookmarked != 0L,
        ),
        desiredIsRead = desired_is_read?.let { it != 0L },
        desiredLastPageRead = desired_last_page_read?.toInt(),
        desiredIsBookmarked = desired_is_bookmarked?.let { it != 0L },
        createdAt = created_at,
        updatedAt = updated_at,
    )
}
