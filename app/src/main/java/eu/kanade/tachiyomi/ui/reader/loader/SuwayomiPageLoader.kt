package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.storage.saveTo
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File

internal class SuwayomiPageLoader(
    private val chapter: ReaderChapter,
    private val client: SuwayomiGraphQlClient,
    private val baseUrl: () -> String,
    private val httpClient: OkHttpClient,
    private val pageCacheDir: File = createReaderPageCacheDir(),
) : PageLoader() {

    override var isLocal: Boolean = false
    private val cachedPageFiles = mutableMapOf<Int, File>()

    override suspend fun getPages(): List<ReaderPage> = withIOContext {
        client.getChapterPages(chapter.chapter.id.toInt())
            .mapIndexed { index, pageUrl ->
                ReaderPage(
                    index = index,
                    url = pageUrl,
                    imageUrl = resolveServerUrl(baseUrl(), pageUrl),
                )
            }
    }

    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        try {
            val imageUrl = page.imageUrl ?: error("No page URL")
            page.status = ReaderPage.State.DownloadImage
            val cachedPageFile = createCachedPageFile(page)
            httpClient.newCall(GET(imageUrl))
                .awaitSuccess()
                .use { it.body.source().saveTo(cachedPageFile) }
            cachedPageFiles[page.index] = cachedPageFile
            page.stream = { cachedPageFile.inputStream() }
            page.status = ReaderPage.State.Ready
        } catch (e: Throwable) {
            page.status = ReaderPage.State.Error(e)
        }
    }

    override fun retryPage(page: ReaderPage) {
        page.stream = null
        cachedPageFiles.remove(page.index)?.delete()
        page.status = ReaderPage.State.Queue
    }

    override fun recycle() {
        super.recycle()
        cachedPageFiles.values.forEach { it.delete() }
        cachedPageFiles.clear()
        pageCacheDir.deleteRecursively()
    }

    private fun createCachedPageFile(page: ReaderPage): File {
        pageCacheDir.mkdirs()
        cachedPageFiles.remove(page.index)?.delete()
        return File.createTempFile("page-${page.index}-", ".img", pageCacheDir)
    }
}

private fun createReaderPageCacheDir(): File {
    return File.createTempFile("amatsubu-reader-pages-", "").apply {
        delete()
        mkdirs()
    }
}
