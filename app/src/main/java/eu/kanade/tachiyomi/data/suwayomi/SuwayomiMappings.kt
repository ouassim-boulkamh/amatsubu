package eu.kanade.tachiyomi.data.suwayomi

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Duration.Companion.seconds
import eu.kanade.domain.manga.model.MangaStatus as DomainMangaStatus
import eu.kanade.domain.manga.model.UpdateStrategy as DomainUpdateStrategy

internal const val SUWAYOMI_MANGA_REAL_URL_META_KEY = "amatsubu.suwayomi.realUrl"
internal const val SERVER_MANGA_NOTES_META_KEY = "amatsubu.notes"

internal fun SuwayomiMangaDto.serverNotes(): String {
    return meta.firstOrNull { it.key == SERVER_MANGA_NOTES_META_KEY }?.value.orEmpty()
}

internal fun SuwayomiMangaDto.normalizedGenre(): List<String>? {
    return genre
        .map { it.trim() }
        .filterNot { it.isBlank() }
        .distinct()
        .takeIf { it.isNotEmpty() }
}

internal fun UpdateStrategy.toDomainUpdateStrategy(): DomainUpdateStrategy {
    return when (this) {
        UpdateStrategy.ALWAYS_UPDATE -> DomainUpdateStrategy.ALWAYS_UPDATE
        UpdateStrategy.ONLY_FETCH_ONCE -> DomainUpdateStrategy.ONLY_FETCH_ONCE
    }
}

internal fun MangaStatus.toDomainMangaStatus(): DomainMangaStatus {
    return when (this) {
        MangaStatus.UNKNOWN -> DomainMangaStatus.UNKNOWN
        MangaStatus.ONGOING -> DomainMangaStatus.ONGOING
        MangaStatus.COMPLETED -> DomainMangaStatus.COMPLETED
        MangaStatus.LICENSED -> DomainMangaStatus.LICENSED
        MangaStatus.PUBLISHING_FINISHED -> DomainMangaStatus.PUBLISHING_FINISHED
        MangaStatus.CANCELLED -> DomainMangaStatus.CANCELLED
        MangaStatus.ON_HIATUS -> DomainMangaStatus.ON_HIATUS
    }
}

internal fun MangaStatus.toDomainStatus(): Long {
    return toDomainMangaStatus().value
}

internal fun SuwayomiMangaDto.serverCoverLastModified(): Long {
    return listOfNotNull(
        lastFetchedAt,
        chaptersLastFetchedAt,
        latestFetchedChapter?.fetchedAt,
    ).maxOrNull()?.seconds?.inWholeMilliseconds ?: 0L
}

internal fun List<String>.toSuwayomiPageAssets(baseUrl: String): List<SuwayomiPageAsset> {
    return mapIndexed { index, pageUrl ->
        val imageUrl = resolveServerUrl(baseUrl, pageUrl)
        SuwayomiPageAsset(index = index, url = pageUrl, imageUrl = imageUrl)
    }
}

internal data class SuwayomiPageAsset(
    val index: Int,
    val url: String,
    val imageUrl: String,
)

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

private const val MANGA_URL_PREFIX = "suwayomi://manga/"
private const val CHAPTER_URL_PREFIX = "suwayomi://chapter/"
