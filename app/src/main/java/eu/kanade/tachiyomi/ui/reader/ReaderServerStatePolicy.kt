package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ServerReaderIntentBaseline
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateInvalidation
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

internal fun ServerStateInvalidation.affectsReaderManga(mangaId: Int): Boolean {
    return affectsAny(
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Chapters(mangaId),
        ServerStateEntity.History,
        ServerStateEntity.Updates,
        ServerStateEntity.Trackers(mangaId),
        ServerStateEntity.Downloads,
    )
}

internal fun shouldApplyExternalReaderChapterState(
    chapterId: Long,
    pendingReaderIntentChapterIds: Set<Long>,
    pendingReadStateChapterIds: Set<Long>,
): Boolean {
    return chapterId !in pendingReaderIntentChapterIds && chapterId !in pendingReadStateChapterIds
}
