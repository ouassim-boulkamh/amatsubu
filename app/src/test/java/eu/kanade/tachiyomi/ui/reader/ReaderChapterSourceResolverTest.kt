package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyPage
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ReaderChapterSourceResolverTest {

    @Test
    fun `complete fresh device copy is preferred`() {
        val source = resolveReaderChapterSource(copy())

        assertInstanceOf(ReaderChapterSource.DeviceCopy::class.java, source)
    }

    @Test
    fun `missing device copy falls back to Suwayomi`() {
        val source = resolveReaderChapterSource(null)

        assertInstanceOf(ReaderChapterSource.Suwayomi::class.java, source)
    }

    @Test
    fun `incomplete device copy falls back to Suwayomi`() {
        val source = resolveReaderChapterSource(
            copy(status = ClientChapterCopyStatus.INCOMPLETE, freshness = ClientChapterCopyFreshness.INCOMPLETE),
        )

        assertInstanceOf(ReaderChapterSource.Suwayomi::class.java, source)
    }

    @Test
    fun `stale or unverified device copy falls back to Suwayomi`() {
        val stale = resolveReaderChapterSource(copy(freshness = ClientChapterCopyFreshness.STALE))
        val unverified = resolveReaderChapterSource(copy(freshness = ClientChapterCopyFreshness.UNVERIFIED))

        assertInstanceOf(ReaderChapterSource.Suwayomi::class.java, stale)
        assertInstanceOf(ReaderChapterSource.Suwayomi::class.java, unverified)
    }

    private fun copy(
        status: ClientChapterCopyStatus = ClientChapterCopyStatus.COMPLETE,
        freshness: ClientChapterCopyFreshness = ClientChapterCopyFreshness.FRESH,
    ): ClientDeviceChapterCopy {
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
            storagePath = null,
            manifestHash = "hash",
            status = status,
            freshness = freshness,
            expectedPageCount = 1,
            downloadedPageCount = 1,
            createdAt = 0,
            updatedAt = 0,
            verifiedAt = 0,
            orphanedAt = null,
            pages = listOf(
                ClientDeviceChapterCopyPage(
                    index = 0,
                    sourceUrl = "/page/0",
                    localUri = "file:///tmp/page-0000.img",
                    isPresent = true,
                ),
            ),
        )
    }
}
