package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.SuwayomiPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import tachiyomi.domain.chapter.model.Chapter

class SuwayomiPageLoaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `loaded server page exposes repeatable stream from cache file`() = runBlocking {
        val loader = loader(responses = listOf("first image"))
        val page = page()

        loader.loadPage(page)

        assertEquals(Page.State.Ready, page.status)
        assertEquals("first image", page.stream!!.invoke().bufferedReader().use { it.readText() })
        assertEquals("first image", page.stream!!.invoke().bufferedReader().use { it.readText() })
    }

    @Test
    fun `retry clears cached stream and reload fetches fresh server bytes`() = runBlocking {
        val loader = loader(responses = listOf("stale image", "fresh image"))
        val page = page()

        loader.loadPage(page)
        loader.retryPage(page)

        assertEquals(Page.State.Queue, page.status)
        assertNull(page.stream)

        loader.loadPage(page)

        assertEquals(Page.State.Ready, page.status)
        assertEquals("fresh image", page.stream!!.invoke().bufferedReader().use { it.readText() })
        assertTrue(tempDir.listFiles().orEmpty().single().isFile)
    }

    private fun loader(responses: List<String>): SuwayomiPageLoader {
        val responseIndex = AtomicInteger()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = responses[responseIndex.getAndIncrement()]
                    .toResponseBody("image/png".toMediaType())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        return SuwayomiPageLoader(
            chapter = ReaderChapter(chapter(id = 1, number = 1.0)),
            client = mockk<SuwayomiGraphQlClient>(),
            baseUrl = { "http://127.0.0.1:4567/" },
            httpClient = httpClient,
            pageCacheDir = tempDir,
        )
    }

    private fun page(): ReaderPage {
        return ReaderPage(index = 0, url = "/api/page/1", imageUrl = "http://127.0.0.1:4567/api/page/1")
    }

    private fun chapter(id: Long, number: Double): Chapter {
        return Chapter(
            id = id,
            mangaId = 1,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = id,
            url = "/chapters/$id",
            name = "Chapter $number",
            dateUpload = 0,
            chapterNumber = number,
            scanlator = null,
            lastModifiedAt = 0,
            version = 0,
            memo = JsonObject.EMPTY,
        )
    }
}
