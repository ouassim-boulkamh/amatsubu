package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyPage
import eu.kanade.tachiyomi.ui.reader.loader.ClientDeviceChapterCopyPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ClientDeviceChapterCopyPageLoaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `local copy loader exposes pages from device copy manifest`() = runBlocking {
        val first = File(tempDir, "page-0000.img").apply { writeText("first") }
        val second = File(tempDir, "page-0001.img").apply { writeText("second") }
        val loader = ClientDeviceChapterCopyPageLoader(
            copy(
                pages = listOf(
                    page(index = 1, sourceUrl = "/page/1", file = second),
                    page(index = 0, sourceUrl = "/page/0", file = first),
                ),
            ),
        )

        val pages = loader.getPages()

        assertTrue(loader.isLocal)
        assertEquals(listOf(0, 1), pages.map { it.index })
        assertEquals(listOf("/page/0", "/page/1"), pages.map { it.url })

        loader.loadPage(pages[0])

        assertEquals(ReaderPage.State.Ready, pages[0].status)
        assertEquals("first", pages[0].stream!!.invoke().bufferedReader().use { it.readText() })
    }

    @Test
    fun `retry clears stream and queues page again`() = runBlocking {
        val file = File(tempDir, "page-0000.img").apply { writeText("image") }
        val loader = ClientDeviceChapterCopyPageLoader(copy(pages = listOf(page(0, "/page/0", file))))
        val page = loader.getPages().single()

        loader.loadPage(page)
        loader.retryPage(page)

        assertEquals(ReaderPage.State.Queue, page.status)
        assertNull(page.stream)
    }

    private fun page(index: Int, sourceUrl: String, file: File): ClientDeviceChapterCopyPage {
        return ClientDeviceChapterCopyPage(
            index = index,
            sourceUrl = sourceUrl,
            localUri = file.toURI().toString(),
            fileName = file.name,
            isPresent = true,
        )
    }

    private fun copy(pages: List<ClientDeviceChapterCopyPage>): ClientDeviceChapterCopy {
        return ClientDeviceChapterCopy(
            serverKey = "http://127.0.0.1:4567/api/graphql",
            mangaId = 1,
            chapterId = 2,
            mangaTitle = "Manga",
            chapterTitle = "Chapter",
            chapterUrl = "/chapter/2",
            chapterRealUrl = null,
            sourceOrder = 1,
            chapterNumber = 1f,
            uploadDate = 0,
            fetchedAt = "",
            scanlator = null,
            storagePath = tempDir.absolutePath,
            manifestHash = "hash",
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            expectedPageCount = pages.size,
            downloadedPageCount = pages.size,
            createdAt = 0,
            updatedAt = 0,
            verifiedAt = 0,
            orphanedAt = null,
            pages = pages,
        )
    }
}
