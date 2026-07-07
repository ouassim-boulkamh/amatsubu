package eu.kanade.domain.chapter.model

import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.localDownloadedFilter
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter
    val localDownloadedFilter = manga.localDownloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter
    return asSequence()
        .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded } }
        .filter { applyFilter(localDownloadedFilter) { it.isLocallyDownloaded } }
        .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
}
