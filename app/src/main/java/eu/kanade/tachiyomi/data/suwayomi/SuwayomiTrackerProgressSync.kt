package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.CancellationException

internal fun trackerProgressMangaIdsForReadStateChange(
    read: Boolean,
    changedMangaIds: Iterable<Int>,
): List<Int> {
    if (!read) return emptyList()
    return changedMangaIds.distinct()
}

internal suspend fun syncTrackerProgressAfterReadStateChange(
    read: Boolean,
    changedMangaIds: Iterable<Int>,
    trackProgress: suspend (Int) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    trackerProgressMangaIdsForReadStateChange(read, changedMangaIds).forEach { mangaId ->
        runCatching {
            trackProgress(mangaId)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            onFailure(error)
        }
    }
}
