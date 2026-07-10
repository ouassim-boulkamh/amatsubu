package eu.kanade.domain.library.model

import eu.kanade.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val categories: List<Long>,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
    val unreadCount: Long = totalChapters - readCount,
) {
    val id: Long = manga.id

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0
}
