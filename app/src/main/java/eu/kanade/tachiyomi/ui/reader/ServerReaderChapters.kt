package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga

data class ServerReaderChapterList(
    val chapters: List<ReaderChapter>,
    val selectedChapter: ReaderChapter,
)

data class ServerDeviceCopyReaderChapters(
    val manga: Manga,
    val chapterList: ServerReaderChapterList,
)

/**
 * Normalizes Suwayomi chapter data before it reaches Mihon's viewer layer.
 *
 * Invariants:
 * - one [ReaderChapter] per unique chapter id
 * - the selected chapter appears exactly once when it is available after downloaded-only filtering
 * - adjacent reader windows built from this list must never point prev/current/next at the same id
 * - sorting follows Mihon's reader navigation order, not display order
 * - server page loading must preserve stable [ReaderPage] objects
 * - the reader's current page must be derived from the active `State.currentChapter.pages`
 */
fun buildServerReaderChapters(
    chapters: List<Chapter>,
    manga: Manga,
    selectedChapter: Chapter,
    skipRead: Boolean,
    skipFiltered: Boolean,
    skipDupe: Boolean,
    downloadedOnly: Boolean,
    downloadedChapterIds: Set<Long>,
    localDownloadedChapterIds: Set<Long> = emptySet(),
    excludedScanlators: Set<String> = emptySet(),
): ServerReaderChapterList {
    val normalizedChapters = chapters.uniqueById(selectedChapter)
    val normalizedSelectedChapter = normalizedChapters.firstOrNull { it.id == selectedChapter.id }
        ?: selectedChapter

    val chaptersForReader = when {
        skipRead || skipFiltered -> {
            val filteredChapters = normalizedChapters.filterNot {
                when {
                    skipRead && it.read -> true
                    skipFiltered -> it.isFilteredByMangaFlags(
                        manga = manga,
                        downloadedChapterIds = downloadedChapterIds,
                        localDownloadedChapterIds = localDownloadedChapterIds,
                        excludedScanlators = excludedScanlators,
                    )
                    else -> false
                }
            }

            if (filteredChapters.any { it.id == normalizedSelectedChapter.id }) {
                filteredChapters
            } else {
                filteredChapters + normalizedSelectedChapter
            }
        }
        else -> normalizedChapters
    }

    val readerChapters = chaptersForReader
        .sortedWith(getChapterSort(manga, sortDescending = false))
        .run {
            if (skipDupe) {
                removeDuplicates(normalizedSelectedChapter)
            } else {
                this
            }
        }
        .run {
            if (downloadedOnly) {
                filter { it.id in downloadedChapterIds || it.id in localDownloadedChapterIds }
            } else {
                this
            }
        }
        .uniqueById(normalizedSelectedChapter)
        .map(::ReaderChapter)

    val selectedReaderChapter = readerChapters.firstOrNull { it.chapter.id == normalizedSelectedChapter.id }
        ?: throw IllegalStateException("Requested chapter is unavailable in downloaded-only mode")

    return ServerReaderChapterList(
        chapters = readerChapters,
        selectedChapter = selectedReaderChapter,
    )
}

fun buildServerReaderChaptersFromDeviceCopies(
    copies: List<ClientDeviceChapterCopy>,
    selectedCopy: ClientDeviceChapterCopy,
    skipDupe: Boolean,
): ServerDeviceCopyReaderChapters {
    val manga = selectedCopy.toOfflineDomainManga()
    val selectedChapter = selectedCopy.toOfflineDomainChapter()
    val chapters = copies
        .filter { it.isComplete }
        .map { it.toOfflineDomainChapter() }
        .ifEmpty { listOf(selectedChapter) }

    return ServerDeviceCopyReaderChapters(
        manga = manga,
        chapterList = buildServerReaderChapters(
            chapters = chapters,
            manga = manga,
            selectedChapter = selectedChapter,
            skipRead = false,
            skipFiltered = false,
            skipDupe = skipDupe,
            downloadedOnly = false,
            downloadedChapterIds = emptySet(),
        ),
    )
}

private fun List<Chapter>.uniqueById(selectedChapter: Chapter): List<Chapter> {
    return groupBy { it.id }
        .map { (_, chapters) ->
            chapters.firstOrNull { it.id == selectedChapter.id } ?: chapters.first()
        }
}

private fun Chapter.isFilteredByMangaFlags(
    manga: Manga,
    downloadedChapterIds: Set<Long>,
    localDownloadedChapterIds: Set<Long>,
    excludedScanlators: Set<String>,
): Boolean {
    return scanlator in excludedScanlators ||
        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !read) ||
        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && read) ||
        (
            manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED &&
                id !in downloadedChapterIds
            ) ||
        (
            manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                id in downloadedChapterIds
            ) ||
        (
            manga.localDownloadedFilterRaw == Manga.CHAPTER_SHOW_LOCAL_DOWNLOADED &&
                id !in localDownloadedChapterIds
            ) ||
        (
            manga.localDownloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_LOCAL_DOWNLOADED &&
                id in localDownloadedChapterIds
            ) ||
        (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !bookmark) ||
        (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && bookmark)
}

private fun ClientDeviceChapterCopy.toOfflineDomainManga(): Manga {
    return Manga.create().copy(
        id = mangaId.toLong(),
        title = mangaTitle ?: "",
        source = 0L,
        favorite = true,
        chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC,
        initialized = true,
    )
}

private fun ClientDeviceChapterCopy.toOfflineDomainChapter(): Chapter {
    return Chapter.create().copy(
        id = chapterId.toLong(),
        mangaId = mangaId.toLong(),
        sourceOrder = sourceOrder.toLong(),
        url = chapterRealUrl ?: chapterUrl,
        name = chapterTitle,
        dateUpload = uploadDate,
        chapterNumber = chapterNumber.toDouble(),
        scanlator = scanlator,
    )
}
