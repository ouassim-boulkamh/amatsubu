package eu.kanade.tachiyomi.data.suwayomi

suspend fun replayPendingReadStates(
    pending: List<PendingServerReadState>,
    updateChaptersRead: suspend (chapterIds: List<Int>, isRead: Boolean) -> Unit,
    deletePendingReadState: suspend (PendingServerReadState) -> Unit,
): Int {
    if (pending.isEmpty()) return 0

    var pushed = 0
    pending
        .groupBy { it.isRead }
        .forEach { (isRead, states) ->
            updateChaptersRead(states.map { it.chapterId }, isRead)
            states.forEach { state ->
                deletePendingReadState(state)
            }
            pushed += states.size
        }
    return pushed
}
