package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ServerReaderIntentBaseline
import eu.kanade.domain.chapter.model.Chapter as DomainChapter

internal fun shouldQueuePendingReadStateAfterProgressFailure(isChapterRead: Boolean): Boolean {
    return isChapterRead
}

internal fun bookmarkStateAfterReaderBookmarkFailure(requestedBookmarkState: Boolean): Boolean {
    return !requestedBookmarkState
}

internal fun DomainChapter.toReaderIntentBaseline(): ServerReaderIntentBaseline {
    return ServerReaderIntentBaseline(
        isRead = read,
        lastPageRead = lastPageRead.toInt(),
        isBookmarked = bookmark,
    )
}
