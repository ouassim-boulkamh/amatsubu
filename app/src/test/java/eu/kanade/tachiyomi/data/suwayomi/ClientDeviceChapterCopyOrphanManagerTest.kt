package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientDeviceChapterCopyOrphanManagerTest {

    @Test
    fun `copy is orphaned when manga is missing from current library snapshot`() {
        val copy = copy(mangaId = 20, chapterId = 10)

        assertTrue(
            isClientDeviceChapterCopyOrphan(
                copy = copy,
                currentLibraryMangaIds = setOf(21),
                currentChapterIds = null,
            ),
        )
    }

    @Test
    fun `copy is orphaned when chapter is missing from current chapter snapshot`() {
        val copy = copy(mangaId = 20, chapterId = 10)

        assertTrue(
            isClientDeviceChapterCopyOrphan(
                copy = copy,
                currentLibraryMangaIds = setOf(20),
                currentChapterIds = setOf(11),
            ),
        )
    }

    @Test
    fun `copy is not orphaned when chapter snapshot is unavailable`() {
        val copy = copy(mangaId = 20, chapterId = 10)

        assertFalse(
            isClientDeviceChapterCopyOrphan(
                copy = copy,
                currentLibraryMangaIds = setOf(20),
                currentChapterIds = null,
            ),
        )
    }

    @Test
    fun `copy is not orphaned when manga and chapter are present`() {
        val copy = copy(mangaId = 20, chapterId = 10)

        assertFalse(
            isClientDeviceChapterCopyOrphan(
                copy = copy,
                currentLibraryMangaIds = setOf(20),
                currentChapterIds = setOf(10, 11),
            ),
        )
    }

    private fun copy(mangaId: Int, chapterId: Int): ClientDeviceChapterCopy {
        return ClientDeviceChapterCopy(
            serverKey = "http://server.test/api/graphql",
            mangaId = mangaId,
            chapterId = chapterId,
            mangaTitle = "Manga",
            chapterTitle = "Chapter",
            chapterUrl = "/chapter",
            chapterRealUrl = null,
            sourceOrder = 1,
            chapterNumber = 1f,
            uploadDate = 0L,
            fetchedAt = "0",
            scanlator = null,
            storagePath = null,
            manifestHash = "hash",
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            expectedPageCount = 1,
            downloadedPageCount = 1,
            createdAt = 0L,
            updatedAt = 0L,
            verifiedAt = 0L,
            orphanedAt = null,
        )
    }
}
