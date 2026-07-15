package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceCopySummaryTest {

    @Test
    fun `aggregates copies by manga with byte totals`() {
        val summaries = listOf(
            copy(mangaId = 1, chapterId = 1, expectedPageCount = 2, byteSizes = listOf(100, 200)),
            copy(mangaId = 1, chapterId = 2, byteSizes = listOf(300)),
            copy(mangaId = 2, chapterId = 3, byteSizes = listOf(400)),
        ).toDeviceCopyMangaSummaries()

        val summary = summaries.single { it.mangaId == 1 }
        assertEquals("Manga 1", summary.mangaTitle)
        assertEquals(2, summary.totalChapterCopyCount)
        assertEquals(2, summary.completeFreshCount)
        assertEquals(600L, summary.totalBytes)
        assertEquals(DeviceCopyReadiness.READY, summary.readiness)
    }

    @Test
    fun `classifies mixed ready and not ready copies as partial`() {
        val summary = listOf(
            copy(chapterId = 1),
            copy(
                chapterId = 2,
                status = ClientChapterCopyStatus.INCOMPLETE,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
                downloadedPageCount = 1,
                expectedPageCount = 2,
            ),
        ).toDeviceCopyMangaSummaries().single()

        assertEquals(DeviceCopyReadiness.PARTIAL, summary.readiness)
        assertEquals(1, summary.completeFreshCount)
        assertEquals(1, summary.incompleteCount)
    }

    @Test
    fun `classifies stale unverified incomplete and failed copies as not ready`() {
        val summary = listOf(
            copy(chapterId = 1, freshness = ClientChapterCopyFreshness.STALE),
            copy(chapterId = 2, freshness = ClientChapterCopyFreshness.UNVERIFIED),
            copy(
                chapterId = 3,
                status = ClientChapterCopyStatus.INCOMPLETE,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
            ),
            copy(chapterId = 4, status = ClientChapterCopyStatus.FAILED),
        ).toDeviceCopyMangaSummaries().single()

        assertEquals(DeviceCopyReadiness.NEEDS_ATTENTION, summary.readiness)
        assertEquals(1, summary.staleCount)
        assertEquals(1, summary.unverifiedCount)
        assertEquals(1, summary.incompleteCount)
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun `classifies all orphaned copies as orphaned`() {
        val summary = listOf(
            copy(chapterId = 1, freshness = ClientChapterCopyFreshness.ORPHANED),
            copy(chapterId = 2, freshness = ClientChapterCopyFreshness.ORPHANED),
        ).toDeviceCopyMangaSummaries().single()

        assertEquals(DeviceCopyReadiness.ORPHANED, summary.readiness)
        assertEquals(2, summary.orphanedCount)
    }

    @Test
    fun `filters summaries by readiness and orphan membership`() {
        val summaries = listOf(
            copy(mangaId = 1, chapterId = 1),
            copy(
                mangaId = 2,
                chapterId = 2,
                status = ClientChapterCopyStatus.INCOMPLETE,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
            ),
            copy(mangaId = 3, chapterId = 3, freshness = ClientChapterCopyFreshness.ORPHANED),
        ).toDeviceCopyMangaSummaries()

        assertEquals(listOf(1), summaries.filter { it.matches(DeviceCopyFilter.READY) }.map { it.mangaId })
        assertEquals(listOf(2), summaries.filter { it.matches(DeviceCopyFilter.NEEDS_ATTENTION) }.map { it.mangaId })
        assertEquals(listOf(3), summaries.filter { it.matches(DeviceCopyFilter.ORPHANED) }.map { it.mangaId })
    }

    @Test
    fun `bulk targets retry incomplete and failed copies`() {
        val summary = listOf(
            copy(chapterId = 1),
            copy(
                chapterId = 2,
                status = ClientChapterCopyStatus.INCOMPLETE,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
            ),
            copy(chapterId = 3, status = ClientChapterCopyStatus.FAILED),
        ).toDeviceCopyMangaSummaries().single()

        assertEquals(
            listOf(3, 2),
            summary.bulkActionCopies(DeviceCopyBulkActionTarget.RETRY).map { it.chapterId },
        )
    }

    @Test
    fun `bulk targets remove orphaned copies only`() {
        val summary = listOf(
            copy(chapterId = 1),
            copy(chapterId = 2, freshness = ClientChapterCopyFreshness.ORPHANED),
        ).toDeviceCopyMangaSummaries().single()

        assertEquals(
            listOf(2),
            summary.bulkActionCopies(DeviceCopyBulkActionTarget.REMOVE_ORPHANED).map { it.chapterId },
        )
    }

    @Test
    fun `chapter status labels preserve manager readiness semantics`() {
        assertEquals("Ready", copy(chapterId = 1).deviceCopyChapterStatusLabel())
        assertEquals(
            "Stale",
            copy(chapterId = 2, freshness = ClientChapterCopyFreshness.STALE).deviceCopyChapterStatusLabel(),
        )
        assertEquals(
            "Unverified",
            copy(chapterId = 3, freshness = ClientChapterCopyFreshness.UNVERIFIED).deviceCopyChapterStatusLabel(),
        )
        assertEquals(
            "Incomplete",
            copy(
                chapterId = 4,
                status = ClientChapterCopyStatus.INCOMPLETE,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
                expectedPageCount = 3,
                downloadedPageCount = 1,
            ).deviceCopyChapterStatusLabel(),
        )
        assertEquals(
            "Failed",
            copy(chapterId = 5, status = ClientChapterCopyStatus.FAILED).deviceCopyChapterStatusLabel(),
        )
        assertEquals(
            "Orphaned",
            copy(chapterId = 6, freshness = ClientChapterCopyFreshness.ORPHANED).deviceCopyChapterStatusLabel(),
        )
    }

    @Test
    fun `chapter progress label shows downloaded pages against expected pages`() {
        assertEquals(
            "2/5 pages",
            copy(
                chapterId = 1,
                expectedPageCount = 5,
                downloadedPageCount = 2,
                byteSizes = List(5) { 100L },
            ).deviceCopyChapterProgressLabel(),
        )
    }

    private fun copy(
        serverKey: String = "server",
        mangaId: Int = 1,
        chapterId: Int,
        mangaTitle: String? = "Manga $mangaId",
        status: ClientChapterCopyStatus = ClientChapterCopyStatus.COMPLETE,
        freshness: ClientChapterCopyFreshness = ClientChapterCopyFreshness.FRESH,
        expectedPageCount: Int = 1,
        downloadedPageCount: Int = expectedPageCount,
        byteSizes: List<Long> = List(expectedPageCount) { 100L },
    ): ClientDeviceChapterCopy {
        val pages = byteSizes.mapIndexed { index, byteSize ->
            ClientDeviceChapterCopyPage(
                index = index,
                sourceUrl = "/page/$index",
                localUri = "file:///page-$index",
                fileName = "page-$index",
                byteSize = byteSize,
                isPresent = true,
            )
        }
        return ClientDeviceChapterCopy(
            serverKey = serverKey,
            mangaId = mangaId,
            chapterId = chapterId,
            mangaTitle = mangaTitle,
            chapterTitle = "Chapter $chapterId",
            chapterUrl = "/chapter/$chapterId",
            chapterRealUrl = null,
            sourceOrder = chapterId,
            chapterNumber = chapterId.toFloat(),
            uploadDate = chapterId.toLong(),
            fetchedAt = chapterId.toString(),
            scanlator = null,
            storagePath = null,
            manifestHash = "hash-$chapterId",
            status = status,
            freshness = freshness,
            expectedPageCount = expectedPageCount,
            downloadedPageCount = downloadedPageCount,
            createdAt = chapterId.toLong(),
            updatedAt = chapterId.toLong(),
            verifiedAt = null,
            orphanedAt = if (freshness == ClientChapterCopyFreshness.ORPHANED) chapterId.toLong() else null,
            pages = pages,
        )
    }
}
