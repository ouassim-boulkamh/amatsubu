package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File
import java.net.URI

internal class ClientDeviceChapterCopyPageLoader(
    private val copy: ClientDeviceChapterCopy,
) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> = withIOContext {
        copy.pages
            .sortedBy { it.index }
            .map { page ->
                ReaderPage(
                    index = page.index,
                    url = page.sourceUrl,
                    imageUrl = page.localUri,
                )
            }
    }

    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        try {
            page.status = ReaderPage.State.DownloadImage
            val localUri = page.imageUrl ?: error("No local device-copy URI")
            val file = File(URI(localUri))
            check(file.isFile) { "Device-copy page is missing: $localUri" }
            page.stream = { file.inputStream() }
            page.status = ReaderPage.State.Ready
        } catch (error: Throwable) {
            page.status = ReaderPage.State.Error(error)
        }
    }

    override fun retryPage(page: ReaderPage) {
        page.stream = null
        page.status = ReaderPage.State.Queue
    }
}
