package eu.kanade.tachiyomi.ui.browse.migration

import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverCoverLastModified
import eu.kanade.tachiyomi.data.suwayomi.serverNotes
import eu.kanade.tachiyomi.data.suwayomi.toDomainStatus
import eu.kanade.tachiyomi.data.suwayomi.toDomainUpdateStrategy
import kotlinx.serialization.json.JsonObject
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
        updateStrategy = updateStrategy.toDomainUpdateStrategy(),
        initialized = initialized,
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 0L,
        notes = serverNotes(),
        memo = JsonObject(emptyMap()),
    )
}
