package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SuwayomiDownloadStatusReconcilerTest {

    @Test
    fun `finished and dequeued updates remove rows from active queue`() {
        val reconciler = SuwayomiDownloadStatusReconciler(
            downloadStatus(
                download(id = 1, state = "DOWNLOADING", position = 0),
                download(id = 2, state = "QUEUED", position = 1),
                download(id = 3, state = "QUEUED", position = 2),
            ),
        )

        val status = reconciler.reconcile(
            downloadUpdates(
                state = "STARTED",
                update(SuwayomiDownloadUpdateType.FINISHED, download(id = 1, state = "FINISHED", position = 0)),
                update(SuwayomiDownloadUpdateType.DEQUEUED, download(id = 3, state = "DEQUEUED", position = 2)),
            ),
        )

        assertEquals(listOf(2), status.queue.map { it.chapter.id })
    }

    @Test
    fun `error rows stay visible for retry`() {
        val reconciler = SuwayomiDownloadStatusReconciler(
            downloadStatus(download(id = 1, state = "DOWNLOADING", position = 0)),
        )

        val status = reconciler.reconcile(
            downloadUpdates(
                state = "STARTED",
                update(
                    SuwayomiDownloadUpdateType.ERROR,
                    download(id = 1, state = "ERROR", tries = 2, position = 0),
                ),
            ),
        )

        assertEquals(listOf("ERROR"), status.queue.map { it.state })
        assertEquals(listOf(2), status.queue.map { it.tries })
    }

    @Test
    fun `initial snapshot replaces stale queue rows`() {
        val reconciler = SuwayomiDownloadStatusReconciler(
            downloadStatus(
                download(id = 1, state = "DOWNLOADING", position = 0),
                download(id = 2, state = "QUEUED", position = 1),
            ),
        )

        val status = reconciler.reconcile(
            SuwayomiDownloadUpdatesDto(
                state = "STOPPED",
                initial = listOf(download(id = 3, state = "QUEUED", position = 0)),
            ),
        )

        assertEquals("STOPPED", status.state)
        assertEquals(listOf(3), status.queue.map { it.chapter.id })
    }

    @Test
    fun `omitted updates refetch and replace local queue`() {
        val reconciler = SuwayomiDownloadStatusReconciler(
            downloadStatus(
                download(id = 1, state = "DOWNLOADING", position = 0),
                download(id = 2, state = "QUEUED", position = 1),
            ),
        )

        val refetched = downloadStatus(download(id = 4, state = "QUEUED", position = 0))
        val status = reconciler.replaceWith(refetched)

        assertEquals(refetched, status)
        assertEquals(
            listOf(4, 5),
            reconciler.reconcile(
                downloadUpdates(
                    state = "STARTED",
                    update(SuwayomiDownloadUpdateType.QUEUED, download(id = 5, state = "QUEUED", position = 1)),
                ),
            ).queue.map { it.chapter.id },
        )
    }

    @Test
    fun `large queue updates stay position ordered`() {
        val initialQueue = (1..600)
            .map { id -> download(id = id, state = "QUEUED", position = id + 10) }
        val reconciler = SuwayomiDownloadStatusReconciler(downloadStatus(*initialQueue.toTypedArray()))

        val status = reconciler.reconcile(
            downloadUpdates(
                state = "STARTED",
                update(SuwayomiDownloadUpdateType.PROGRESS, download(id = 500, state = "DOWNLOADING", position = 0)),
                update(SuwayomiDownloadUpdateType.ERROR, download(id = 100, state = "ERROR", position = 1)),
                update(SuwayomiDownloadUpdateType.FINISHED, download(id = 600, state = "FINISHED", position = 0)),
            ),
        )

        assertEquals(599, status.queue.size)
        assertFalse(status.queue.any { it.chapter.id == 600 })
        assertEquals(listOf(500, 100), status.queue.take(2).map { it.chapter.id })
    }

    private fun downloadStatus(
        vararg downloads: SuwayomiDownloadDto,
        state: String = "STARTED",
    ): SuwayomiDownloadStatusDto {
        return SuwayomiDownloadStatusDto(
            state = state,
            queue = downloads.toList(),
        )
    }

    private fun downloadUpdates(
        state: String,
        vararg updates: SuwayomiDownloadUpdateDto,
    ): SuwayomiDownloadUpdatesDto {
        return SuwayomiDownloadUpdatesDto(
            state = state,
            updates = updates.toList(),
        )
    }

    private fun update(
        type: SuwayomiDownloadUpdateType,
        download: SuwayomiDownloadDto,
    ): SuwayomiDownloadUpdateDto {
        return SuwayomiDownloadUpdateDto(
            type = type,
            download = download,
        )
    }

    private fun download(
        id: Int,
        state: String,
        position: Int,
        tries: Int = 0,
    ): SuwayomiDownloadDto {
        return SuwayomiDownloadDto(
            chapter = SuwayomiDownloadChapterDto(
                id = id,
                name = "Chapter $id",
                isDownloaded = state.equals("FINISHED", ignoreCase = true),
            ),
            manga = SuwayomiDownloadMangaDto(
                id = id,
                title = "Manga $id",
            ),
            progress = if (state.equals("DOWNLOADING", ignoreCase = true)) 0.5 else 0.0,
            state = state,
            tries = tries,
            position = position,
        )
    }
}
