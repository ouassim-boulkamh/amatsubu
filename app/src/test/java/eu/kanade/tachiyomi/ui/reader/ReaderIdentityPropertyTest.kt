package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyPage
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import eu.kanade.tachiyomi.ui.reader.loader.ClientDeviceChapterCopyPageLoader
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ReaderIdentityPropertyTest {

    @Test
    fun `reader source resolution only uses a local device copy when it is complete and fresh`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), copyShapeArb) { shape ->
            val copy = copy(
                status = shape.status,
                freshness = shape.freshness,
                expectedPageCount = shape.expectedPageCount,
                downloadedPageCount = shape.downloadedPageCount,
                pages = shape.pages,
            )

            val source = resolveReaderChapterSource(copy)

            if (copy.freshness == ClientChapterCopyFreshness.FRESH && copy.isComplete) {
                val deviceCopy = assertInstanceOf(ReaderChapterSource.DeviceCopy::class.java, source)
                deviceCopy.copy shouldBe copy
            } else {
                assertInstanceOf(ReaderChapterSource.Suwayomi::class.java, source)
            }
        }
    }

    @Test
    fun `device copy page loader preserves bounded manifest indices and source urls`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(pageArb, 0..12)) { generatedPages ->
            val distinctPages = generatedPages.distinctBy { it.index }
            val loader = ClientDeviceChapterCopyPageLoader(
                copy(
                    expectedPageCount = distinctPages.size,
                    downloadedPageCount = distinctPages.size,
                    pages = distinctPages,
                ),
            )

            val pages = loader.getPages()
            val expectedPages = distinctPages.sortedBy { it.index }

            pages.map { it.index } shouldContainExactly expectedPages.map { it.index }
            pages.map { it.url } shouldContainExactly expectedPages.map { it.sourceUrl }
            pages.map { it.imageUrl } shouldContainExactly expectedPages.map { it.localUri }
            pages.all { it.index >= 0 } shouldBe true
        }
    }

    @Test
    fun `requested reader page index is clamped to loaded page bounds or rejected explicitly`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), pageIndexRequestArb) { request ->
            val requestedPage = request.requestedPage
            val pageCount = request.pageCount
            val coerced = coerceRequestedReaderPageIndex(requestedPage, pageCount)

            if (pageCount <= 0) {
                coerced shouldBe null
            } else {
                (coerced!! >= 0) shouldBe true
                (coerced < pageCount) shouldBe true
                when {
                    requestedPage < 0 -> coerced shouldBe 0
                    requestedPage >= pageCount -> coerced shouldBe pageCount - 1
                    else -> coerced shouldBe requestedPage
                }
            }
        }
    }

    private companion object {
        val pageArb: Arb<ClientDeviceChapterCopyPage> = Arb.bind(
            Arb.int(0..10_000),
            Arb.string(1..48),
        ) { index, suffix ->
            ClientDeviceChapterCopyPage(
                index = index,
                sourceUrl = "/api/page/$index/$suffix",
                localUri = "file:///tmp/amatsubu-$index.img",
                fileName = "page-$index.img",
                isPresent = true,
            )
        }

        val copyShapeArb: Arb<CopyShape> = Arb.bind(
            Arb.enum<ClientChapterCopyStatus>(),
            Arb.enum<ClientChapterCopyFreshness>(),
            Arb.int(0..8),
            Arb.int(0..8),
            Arb.list(pageArb, 0..8),
        ) { status, freshness, expectedPageCount, downloadedPageCount, pages ->
            CopyShape(status, freshness, expectedPageCount, downloadedPageCount, pages.distinctBy { it.index })
        }

        val pageIndexRequestArb: Arb<PageIndexRequest> = arbitrary { random ->
            PageIndexRequest(
                requestedPage = random.random.nextInt(),
                pageCount = random.random.nextInt(from = -128, until = 10_001),
            )
        }

        fun copy(
            status: ClientChapterCopyStatus = ClientChapterCopyStatus.COMPLETE,
            freshness: ClientChapterCopyFreshness = ClientChapterCopyFreshness.FRESH,
            expectedPageCount: Int = 1,
            downloadedPageCount: Int = 1,
            pages: List<ClientDeviceChapterCopyPage> = listOf(page(0)),
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
                expectedPageCount = expectedPageCount,
                downloadedPageCount = downloadedPageCount,
                createdAt = 0,
                updatedAt = 0,
                verifiedAt = 0,
                orphanedAt = null,
                pages = pages,
            )
        }

        fun page(index: Int): ClientDeviceChapterCopyPage {
            return ClientDeviceChapterCopyPage(
                index = index,
                sourceUrl = "/api/page/$index",
                localUri = "file:///tmp/amatsubu-$index.img",
                fileName = "page-$index.img",
                isPresent = true,
            )
        }

        data class CopyShape(
            val status: ClientChapterCopyStatus,
            val freshness: ClientChapterCopyFreshness,
            val expectedPageCount: Int,
            val downloadedPageCount: Int,
            val pages: List<ClientDeviceChapterCopyPage>,
        )

        data class PageIndexRequest(
            val requestedPage: Int,
            val pageCount: Int,
        )
    }
}
