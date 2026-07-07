package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ClientDeviceChapterCopyStoreTest {

    @Test
    fun `manifest hash is stable for identical manifest`() {
        val manifest = manifest(pageCount = 2)

        assertEquals(buildManifestHash(manifest), buildManifestHash(manifest))
    }

    @Test
    fun `manifest hash changes when page order changes`() {
        val manifest = manifest(pageCount = 2)
        val reordered = manifest.copy(pages = manifest.pages.reversed())

        assertNotEquals(buildManifestHash(manifest), buildManifestHash(reordered))
    }

    @Test
    fun `manifest hash changes when chapter freshness fields change`() {
        val manifest = manifest(pageCount = 2)
        val renamed = manifest.copy(chapter = manifest.chapter.copy(name = "Chapter 1.1"))

        assertNotEquals(buildManifestHash(manifest), buildManifestHash(renamed))
    }

    @Test
    fun `build copy pages keeps source order and leaves local files absent`() {
        val pages = buildClientDeviceCopyPages(manifest(pageCount = 3))

        assertEquals(listOf(0, 1, 2), pages.map { it.index })
        assertEquals(listOf("page-0", "page-1", "page-2"), pages.map { it.sourceUrl })
        assertEquals(listOf(false, false, false), pages.map { it.isPresent })
    }

    private fun manifest(pageCount: Int): SuwayomiChapterPageManifest {
        return SuwayomiChapterPageManifest(
            pages = List(pageCount) { index -> "page-$index" },
            chapter = SuwayomiChapterDto(
                id = 10,
                mangaId = 20,
                name = "Chapter 1",
                url = "/chapter/1",
                realUrl = "https://example.test/chapter/1",
                sourceOrder = 30,
                chapterNumber = 1f,
                uploadDate = 40L,
                fetchedAt = "50",
                scanlator = "scanlator",
                pageCount = pageCount,
            ),
        )
    }
}
