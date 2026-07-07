package eu.kanade.tachiyomi.ui.browse.migration

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverCoverLastModified
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.seconds

internal fun SuwayomiMangaDto.toMigrationManga(
    provider: SuwayomiClientProvider,
): Manga {
    return Manga(
        id = id.toLong(),
        source = sourceId.toLongOrNull() ?: 0L,
        favorite = inLibrary,
        lastUpdate = chaptersLastFetchedAt?.seconds?.inWholeMilliseconds
            ?: latestFetchedChapter?.fetchedAt?.seconds?.inWholeMilliseconds
            ?: 0L,
        nextUpdate = 0L,
        fetchInterval = 0,
        dateAdded = inLibraryAt ?: 0L,
        viewerFlags = 0L,
        chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC,
        coverLastModified = serverCoverLastModified(),
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = normalizedGenre(),
        status = status.toDomainStatus(),
        thumbnailUrl = thumbnailUrl?.let { resolveServerUrl(provider.baseUrl(), it) },
        updateStrategy = when (updateStrategy) {
            eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy.ALWAYS_UPDATE -> UpdateStrategy.ALWAYS_UPDATE
            eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy.ONLY_FETCH_ONCE -> UpdateStrategy.ONLY_FETCH_ONCE
        },
        initialized = initialized,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
        notes = meta.firstOrNull { it.key == SERVER_MIGRATION_NOTES_META_KEY }?.value.orEmpty(),
        memo = JsonObject(emptyMap()),
    )
}

private fun eu.kanade.tachiyomi.data.suwayomi.MangaStatus.toDomainStatus(): Long {
    return when (this) {
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.UNKNOWN -> SManga.UNKNOWN
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.ONGOING -> SManga.ONGOING
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.COMPLETED -> SManga.COMPLETED
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.LICENSED -> SManga.LICENSED
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.PUBLISHING_FINISHED -> SManga.PUBLISHING_FINISHED
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.CANCELLED -> SManga.CANCELLED
        eu.kanade.tachiyomi.data.suwayomi.MangaStatus.ON_HIATUS -> SManga.ON_HIATUS
    }.toLong()
}

internal const val SERVER_MIGRATION_NOTES_META_KEY = "amatsubu.notes"
