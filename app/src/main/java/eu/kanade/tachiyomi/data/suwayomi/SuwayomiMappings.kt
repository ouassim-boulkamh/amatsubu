package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.source.model.UpdateStrategy as LocalUpdateStrategy

internal const val SUWAYOMI_MANGA_REAL_URL_META_KEY = "amatsubu.suwayomi.realUrl"

internal fun SuwayomiMangaDto.toSManga(baseUrl: String): SManga {
    return SManga.create().also {
        it.url = mangaUrl(id)
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = normalizedGenre()?.joinToString(", ")
        it.status = status.toSMangaStatus()
        it.thumbnail_url = thumbnailUrl?.let { thumbnail -> resolveServerUrl(baseUrl, thumbnail) }
        it.update_strategy = when (updateStrategy) {
            UpdateStrategy.ALWAYS_UPDATE -> LocalUpdateStrategy.ALWAYS_UPDATE
            UpdateStrategy.ONLY_FETCH_ONCE -> LocalUpdateStrategy.ONLY_FETCH_ONCE
        }
        it.initialized = initialized
        it.memo = buildJsonObject {
            put("amatsubu.suwayomi.mangaId", id)
            put("amatsubu.suwayomi.sourceId", sourceId)
            put("amatsubu.suwayomi.url", url)
            realUrl?.let { put(SUWAYOMI_MANGA_REAL_URL_META_KEY, it) }
        }
    }
}

internal fun SuwayomiMangaDto.normalizedGenre(): List<String>? {
    return genre
        .map { it.trim() }
        .filterNot { it.isBlank() }
        .distinct()
        .takeIf { it.isNotEmpty() }
}

internal fun SuwayomiMangaDto.serverCoverLastModified(): Long {
    return listOfNotNull(
        lastFetchedAt,
        chaptersLastFetchedAt,
        latestFetchedChapter?.fetchedAt,
    ).maxOrNull()?.seconds?.inWholeMilliseconds ?: 0L
}

internal fun SuwayomiChapterDto.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = chapterUrl(id)
        it.name = name
        it.chapter_number = chapterNumber
        it.scanlator = scanlator
        it.date_upload = uploadDate
        it.memo = buildJsonObject {
            put("amatsubu.suwayomi.chapterId", id)
            put("amatsubu.suwayomi.mangaId", mangaId)
            put("amatsubu.suwayomi.isBookmarked", isBookmarked)
            put("amatsubu.suwayomi.isRead", isRead)
            put("amatsubu.suwayomi.lastPageRead", lastPageRead)
            put("amatsubu.suwayomi.url", url)
        }
    }
}

internal fun List<String>.toPages(baseUrl: String): List<Page> {
    return mapIndexed { index, pageUrl ->
        val imageUrl = resolveServerUrl(baseUrl, pageUrl)
        Page(index = index, url = imageUrl, imageUrl = imageUrl)
    }
}

internal fun mangaUrl(id: Int): String = "$MANGA_URL_PREFIX$id"

internal fun chapterUrl(id: Int): String = "$CHAPTER_URL_PREFIX$id"

internal fun parseMangaId(url: String): Int {
    return url.substringAfter(MANGA_URL_PREFIX).toInt()
}

internal fun parseChapterId(url: String): Int {
    return url.substringAfter(CHAPTER_URL_PREFIX).toInt()
}

internal fun resolveServerUrl(baseUrl: String, value: String): String {
    if (value.toHttpUrlOrNull() != null) {
        return value
    }
    val normalizedBase = baseUrl.trimEnd('/')
    val normalizedValue = value.trimStart('/')
    return "$normalizedBase/$normalizedValue"
}

private fun MangaStatus.toSMangaStatus(): Int {
    return when (this) {
        MangaStatus.UNKNOWN -> SManga.UNKNOWN
        MangaStatus.ONGOING -> SManga.ONGOING
        MangaStatus.COMPLETED -> SManga.COMPLETED
        MangaStatus.LICENSED -> SManga.LICENSED
        MangaStatus.PUBLISHING_FINISHED -> SManga.PUBLISHING_FINISHED
        MangaStatus.CANCELLED -> SManga.CANCELLED
        MangaStatus.ON_HIATUS -> SManga.ON_HIATUS
    }
}

private const val MANGA_URL_PREFIX = "suwayomi://manga/"
private const val CHAPTER_URL_PREFIX = "suwayomi://chapter/"
