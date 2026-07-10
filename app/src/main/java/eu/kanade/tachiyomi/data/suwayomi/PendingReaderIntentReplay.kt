package eu.kanade.tachiyomi.data.suwayomi

data class PendingReaderIntentReplayResult(
    val pushed: Int,
    val conflicted: List<PendingServerReaderIntent>,
)

suspend fun replayPendingReaderIntents(
    pending: List<PendingServerReaderIntent>,
    currentBaseline: suspend (PendingServerReaderIntent) -> ServerReaderIntentBaseline,
    updateProgress: suspend (PendingServerReaderIntent) -> Unit,
    updateBookmark: suspend (PendingServerReaderIntent) -> Unit,
    deletePendingIntent: suspend (PendingServerReaderIntent) -> Unit,
): PendingReaderIntentReplayResult {
    var pushed = 0
    val conflicted = mutableListOf<PendingServerReaderIntent>()

    pending.forEach { intent ->
        val current = currentBaseline(intent)
        if (intent.hasConflict(current)) {
            conflicted += intent
            return@forEach
        }

        when (intent.type) {
            PendingServerReaderIntentType.PROGRESS -> updateProgress(intent)
            PendingServerReaderIntentType.BOOKMARK -> updateBookmark(intent)
        }
        deletePendingIntent(intent)
        pushed += 1
    }

    return PendingReaderIntentReplayResult(
        pushed = pushed,
        conflicted = conflicted,
    )
}

private fun PendingServerReaderIntent.hasConflict(current: ServerReaderIntentBaseline): Boolean {
    return when (type) {
        PendingServerReaderIntentType.PROGRESS -> {
            current.isRead != baseline.isRead ||
                current.lastPageRead != baseline.lastPageRead
        }
        PendingServerReaderIntentType.BOOKMARK -> current.isBookmarked != baseline.isBookmarked
    }
}
