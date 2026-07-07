package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import kotlin.time.Duration.Companion.seconds

internal const val SERVER_EXCLUDED_SCANLATORS_META_KEY = "amatsubu.excludedScanlators"

internal data class ServerLibraryChapterAggregate(
    val totalChapters: Long,
    val readCount: Long,
    val unreadCount: Long,
    val bookmarkCount: Long,
    val downloadCount: Int,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
)

internal fun SuwayomiMangaDto.serverExcludedScanlators(json: Json): Set<String> {
    val value = meta.firstOrNull { it.key == SERVER_EXCLUDED_SCANLATORS_META_KEY }?.value ?: return emptySet()
    return runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), value).toSet()
    }.getOrDefault(emptySet())
}

internal fun SuwayomiMangaDto.toFallbackLibraryChapterAggregate(): ServerLibraryChapterAggregate {
    return ServerLibraryChapterAggregate(
        totalChapters = 0L,
        readCount = 0L,
        unreadCount = unreadCount,
        bookmarkCount = 0L,
        downloadCount = downloadCount,
        latestUpload = latestUploadedChapter?.uploadDate ?: 0L,
        chapterFetchedAt = latestFetchedChapter?.fetchedAt?.seconds?.inWholeMilliseconds ?: 0L,
    )
}

internal fun List<SuwayomiChapterDto>.toLibraryChapterAggregate(
    excludedScanlators: Set<String>,
): ServerLibraryChapterAggregate {
    val includedChapters = filter { it.scanlator !in excludedScanlators }
    return ServerLibraryChapterAggregate(
        totalChapters = includedChapters.size.toLong(),
        readCount = includedChapters.count { it.isRead }.toLong(),
        unreadCount = includedChapters.count { !it.isRead }.toLong(),
        bookmarkCount = includedChapters.count { it.isBookmarked }.toLong(),
        downloadCount = includedChapters.count { it.isDownloaded },
        latestUpload = includedChapters.maxOfOrNull { it.uploadDate } ?: 0L,
        chapterFetchedAt = includedChapters.maxOfOrNull {
            it.fetchedAt.toLongOrNull()?.seconds?.inWholeMilliseconds ?: 0L
        } ?: 0L,
    )
}

internal fun List<SuwayomiChapterDto>.deviceCopyCount(
    excludedScanlators: Set<String>,
    deviceCopyChapterIds: Set<Int>,
): Int {
    val includedChapterIds = filter { it.scanlator !in excludedScanlators }
        .map { it.id }
        .toSet()
    return deviceCopyChapterIds.intersect(includedChapterIds).size
}

internal fun groupServerLibraryMangaByCategory(
    favorites: List<LibraryManga>,
    categories: List<Category>,
    showSystemCategory: Boolean,
): Map<Category, List<Long>> {
    val groupCache = mutableMapOf<Long, MutableList<Long>>()
    favorites.forEach { item ->
        item.categories.forEach { categoryId ->
            groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
        }
    }
    return categories
        .filter { showSystemCategory || !it.isSystemCategory }
        .associateWith { groupCache[it.id]?.toList().orEmpty() }
}
