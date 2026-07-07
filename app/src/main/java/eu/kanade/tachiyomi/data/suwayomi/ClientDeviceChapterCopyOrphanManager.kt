package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

internal class ClientDeviceChapterCopyOrphanManager(
    private val store: ClientDeviceChapterCopyStore,
    private val client: SuwayomiGraphQlClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(serverKey: String): ClientDeviceChapterCopyOrphanResult {
        val copies = store.getCopiesForServer(serverKey)
        if (copies.isEmpty()) {
            return ClientDeviceChapterCopyOrphanResult(scanned = 0, marked = 0)
        }

        val librarySnapshot = currentLibraryMangaIds() ?: return ClientDeviceChapterCopyOrphanResult(
            scanned = copies.size,
            marked = 0,
            skipped = true,
        )
        var marked = 0
        val orphanedAt = now()
        val chapterIdsByMangaId = mutableMapOf<Int, Set<Int>?>()

        copies.forEach { copy ->
            val chapterIds = if (copy.mangaId in librarySnapshot) {
                chapterIdsByMangaId.getOrPut(copy.mangaId) {
                    currentChapterIds(copy.mangaId)
                }
            } else {
                null
            }
            if (isClientDeviceChapterCopyOrphan(copy, librarySnapshot, chapterIds)) {
                store.markOrphaned(copy, orphanedAt)
                marked++
            }
        }

        return ClientDeviceChapterCopyOrphanResult(scanned = copies.size, marked = marked)
    }

    private suspend fun currentLibraryMangaIds(): Set<Int>? {
        return runCatching {
            client.getLibraryMangas().map { it.id }.toSet()
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load live library before device-copy orphan reconciliation" }
            client.getLibraryMangasSnapshot()?.value?.map { it.id }?.toSet()
        }.getOrNull()
    }

    private suspend fun currentChapterIds(mangaId: Int): Set<Int>? {
        return runCatching {
            client.getCachedChapters(mangaId).map { it.id }.toSet()
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load live chapters before device-copy orphan reconciliation" }
            client.getChaptersSnapshot(mangaId)?.value?.map { it.id }?.toSet()
        }.getOrNull()
    }
}

internal data class ClientDeviceChapterCopyOrphanResult(
    val scanned: Int,
    val marked: Int,
    val skipped: Boolean = false,
)

internal fun isClientDeviceChapterCopyOrphan(
    copy: ClientDeviceChapterCopy,
    currentLibraryMangaIds: Set<Int>,
    currentChapterIds: Set<Int>?,
): Boolean {
    if (copy.mangaId !in currentLibraryMangaIds) return true
    return currentChapterIds != null && copy.chapterId !in currentChapterIds
}
