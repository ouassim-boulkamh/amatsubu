package eu.kanade.domain.manga.model

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import tachiyomi.core.metadata.comicinfo.ComicInfo.SourceMihon as ComicInfoSource

// TODO: move these into the domain model
val Manga.readingMode: Long
    get() = viewerFlags and ReadingMode.MASK.toLong()

val Manga.readerOrientation: Long
    get() = viewerFlags and ReaderOrientation.MASK.toLong()

fun Manga.downloadedFilter(basePreferences: BasePreferences): TriState {
    if (basePreferences.downloadedOnly.get()) return TriState.ENABLED_IS
    return when (downloadedFilterRaw) {
        Manga.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
        Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
        else -> TriState.DISABLED
    }
}

val Manga.localDownloadedFilter: TriState
    get() {
        return when (localDownloadedFilterRaw) {
            Manga.CHAPTER_SHOW_LOCAL_DOWNLOADED -> TriState.ENABLED_IS
            Manga.CHAPTER_SHOW_NOT_LOCAL_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

fun Manga.chaptersFiltered(basePreferences: BasePreferences): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter(basePreferences) != TriState.DISABLED ||
        localDownloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

fun Manga.hasCustomCover(coverCache: CoverCache): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

/**
 * Creates a ComicInfo instance based on the manga and chapter metadata.
 */
fun getComicInfo(
    manga: Manga,
    chapter: Chapter,
    urls: List<String>,
    categories: List<String>?,
    sourceName: String,
) = ComicInfo(
    title = ComicInfo.Title(chapter.name),
    series = ComicInfo.Series(manga.title),
    number = chapter.chapterNumber.takeIf { it >= 0 }?.let {
        if ((it.rem(1) == 0.0)) {
            ComicInfo.Number(it.toInt().toString())
        } else {
            ComicInfo.Number(it.toString())
        }
    },
    web = ComicInfo.Web(urls.joinToString(" ")),
    summary = manga.description?.let { ComicInfo.Summary(it) },
    writer = manga.author?.let { ComicInfo.Writer(it) },
    penciller = manga.artist?.let { ComicInfo.Penciller(it) },
    translator = chapter.scanlator?.let { ComicInfo.Translator(it) },
    genre = manga.genre?.let { ComicInfo.Genre(it.joinToString()) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(manga.status),
    ),
    categories = categories?.let { ComicInfo.CategoriesTachiyomi(it.joinToString()) },
    source = ComicInfoSource(sourceName),
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
)
