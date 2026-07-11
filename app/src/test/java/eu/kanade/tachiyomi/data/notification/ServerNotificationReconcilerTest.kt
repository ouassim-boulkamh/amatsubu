package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.ServerStateInvalidation
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGlobalMetaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLibraryUpdateStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiUpdaterJobsInfoDto
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationDownloadAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationLibraryUpdateAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationSyncAffectedEntities
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ServerNotificationReconcilerTest {

    @Test
    fun `library update completion emits typed invalidation`() = runTest {
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns emptyList()
        val reconciler = ServerNotificationReconciler(
            renderer = renderer,
            checkpoints = ServerNotificationCheckpointStore(InMemoryPreferenceStore()),
        )
        reconciler.reconcileLibraryUpdate(
            client = client,
            serverIdentity = SERVER,
            status = SuwayomiLibraryUpdateStatusDto(
                jobsInfo = SuwayomiUpdaterJobsInfoDto(isRunning = true, totalJobs = 1, finishedJobs = 0),
            ),
        )

        val invalidation = async { withTimeout(1_000) { ServerStateSync.invalidations.first() } }
        yield()

        reconciler.reconcileLibraryUpdate(
            client = client,
            serverIdentity = SERVER,
            status = SuwayomiLibraryUpdateStatusDto(
                jobsInfo = SuwayomiUpdaterJobsInfoDto(isRunning = false, totalJobs = 1, finishedJobs = 1),
            ),
        )

        assertEquals(
            ServerStateInvalidation(serverNotificationLibraryUpdateAffectedEntities()),
            invalidation.await(),
        )
    }

    @Test
    fun `download queue becoming hidden emits typed invalidation`() = runTest {
        val reconciler = ServerNotificationReconciler(
            renderer = mockk(relaxed = true),
            checkpoints = ServerNotificationCheckpointStore(InMemoryPreferenceStore()),
        )
        reconciler.reconcileDownloadStatus(
            serverIdentity = SERVER,
            status = SuwayomiDownloadStatusDto(
                state = "STARTED",
                queue = listOf(download(state = "DOWNLOADING")),
            ),
        )

        val invalidation = async { withTimeout(1_000) { ServerStateSync.invalidations.first() } }
        yield()

        reconciler.reconcileDownloadStatus(
            serverIdentity = SERVER,
            status = SuwayomiDownloadStatusDto(state = "STOPPED", queue = emptyList()),
        )

        assertEquals(
            ServerStateInvalidation(serverNotificationDownloadAffectedEntities()),
            invalidation.await(),
        )
    }

    @Test
    fun `syncyomi terminal transition emits typed invalidation`() = runTest {
        val reconciler = ServerNotificationReconciler(
            renderer = mockk(relaxed = true),
            checkpoints = ServerNotificationCheckpointStore(InMemoryPreferenceStore()),
        )
        reconciler.reconcileSyncYomiStatus(
            serverIdentity = SERVER,
            status = syncStatus(state = "UPLOADING", startDate = "1"),
        )

        val invalidation = async { withTimeout(1_000) { ServerStateSync.invalidations.first() } }
        yield()

        reconciler.reconcileSyncYomiStatus(
            serverIdentity = SERVER,
            status = syncStatus(state = "SUCCESS", startDate = "1", endDate = "2"),
        )

        assertEquals(
            ServerStateInvalidation(serverNotificationSyncAffectedEntities()),
            invalidation.await(),
        )
    }

    @Test
    fun `suwayomi epoch timestamps are normalized to milliseconds`() {
        assertEquals(1_783_466_771_000L, "1783466771".toSuwayomiEpochMillis())
        assertEquals(1_783_466_771_000L, "1783466771000".toSuwayomiEpochMillis())
        assertEquals(0L, "not-a-timestamp".toSuwayomiEpochMillis())
    }

    @Test
    fun `recent chapters allow client-observed library update start skew`() {
        val observedStart = 1_783_467_003_844L

        assertEquals(true, "1783467003".wasFetchedDuringObservedLibraryUpdate(observedStart))
        assertEquals(true, "1783467003844".wasFetchedDuringObservedLibraryUpdate(observedStart))
        assertEquals(false, "1783466943".wasFetchedDuringObservedLibraryUpdate(observedStart))
    }

    @Test
    fun `first new chapter reconciliation seeds global checkpoint without notification`() = runTest {
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns listOf(
            chapter(id = 10, fetchedAt = "1000", isRead = false),
            chapter(id = 11, fetchedAt = "2000", isRead = false),
        )
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns null
        coEvery { client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, any()) } answers {
            SuwayomiGlobalMetaDto(NEW_CHAPTER_CHECKPOINT_KEY, secondArg())
        }

        val result = reconciler(renderer).completeLibraryUpdate(client)

        assertEquals(0, result.unnotifiedChapterCount)
        verify { renderer.showNewChapters(emptyList()) }
        coVerify {
            client.setGlobalMeta(
                NEW_CHAPTER_CHECKPOINT_KEY,
                NewChapterCheckpoint(lastFetchedAt = 2_000_000L, lastChapterId = 11).encode(),
            )
        }
    }

    @Test
    fun `global checkpoint suppresses duplicate new chapter notifications across restart`() = runTest {
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns listOf(chapter(id = 10, fetchedAt = "1000", isRead = false))
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns checkpointMeta(
            NewChapterCheckpoint(lastFetchedAt = 1_000_000L, lastChapterId = 10),
        )

        val result = reconciler(renderer).completeLibraryUpdate(client)

        assertEquals(0, result.unnotifiedChapterCount)
        verify { renderer.showNewChapters(emptyList()) }
        coVerify(exactly = 0) { client.setGlobalMeta(any(), any()) }
    }

    @Test
    fun `timestamp ties use chapter id as deterministic checkpoint tiebreaker`() = runTest {
        val rendered = slot<List<SuwayomiChapterWithMangaDto>>()
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        every { renderer.showNewChapters(capture(rendered)) } returns Unit
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns listOf(
            chapter(id = 3, fetchedAt = "1000", isRead = false),
            chapter(id = 2, fetchedAt = "1000", isRead = false),
        )
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns checkpointMeta(
            NewChapterCheckpoint(lastFetchedAt = 1_000_000L, lastChapterId = 2),
        )
        coEvery { client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, any()) } answers {
            SuwayomiGlobalMetaDto(NEW_CHAPTER_CHECKPOINT_KEY, secondArg())
        }

        val result = reconciler(renderer).completeLibraryUpdate(client)

        assertEquals(1, result.unnotifiedChapterCount)
        assertEquals(listOf(3), rendered.captured.map { it.id })
        coVerify {
            client.setGlobalMeta(
                NEW_CHAPTER_CHECKPOINT_KEY,
                NewChapterCheckpoint(lastFetchedAt = 1_000_000L, lastChapterId = 3).encode(),
            )
        }
    }

    @Test
    fun `new chapter notification ignores chapters fetched before manga entered library`() = runTest {
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns listOf(
            chapter(id = 10, fetchedAt = "1000", isRead = false, inLibraryAt = 2_000_000L),
        )
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns checkpointMeta(
            NewChapterCheckpoint(lastFetchedAt = 500L, lastChapterId = 9),
        )
        coEvery { client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, any()) } answers {
            SuwayomiGlobalMetaDto(NEW_CHAPTER_CHECKPOINT_KEY, secondArg())
        }

        val result = reconciler(renderer).completeLibraryUpdate(client)

        assertEquals(0, result.unnotifiedChapterCount)
        verify { renderer.showNewChapters(emptyList()) }
        coVerify {
            client.setGlobalMeta(
                NEW_CHAPTER_CHECKPOINT_KEY,
                NewChapterCheckpoint(lastFetchedAt = 1_000_000L, lastChapterId = 10).encode(),
            )
        }
    }

    @Test
    fun `new chapter notifications are capped to a bounded batch`() = runTest {
        val rendered = slot<List<SuwayomiChapterWithMangaDto>>()
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        every { renderer.showNewChapters(capture(rendered)) } returns Unit
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns (1..150).map { id ->
            chapter(id = id, fetchedAt = (1_000L + id).toString(), isRead = false)
        }
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns checkpointMeta(
            NewChapterCheckpoint(lastFetchedAt = 0L, lastChapterId = 0),
        )
        coEvery { client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, any()) } answers {
            SuwayomiGlobalMetaDto(NEW_CHAPTER_CHECKPOINT_KEY, secondArg())
        }

        val result = reconciler(renderer).completeLibraryUpdate(client)

        assertEquals(MAX_NEW_CHAPTER_NOTIFICATION_BATCH, result.unnotifiedChapterCount)
        assertEquals(MAX_NEW_CHAPTER_NOTIFICATION_BATCH, rendered.captured.size)
    }

    @Test
    fun `global checkpoint is not advanced when new chapter notification posting fails`() = runTest {
        val renderer = mockk<ServerNotificationRenderer>(relaxed = true)
        every { renderer.showNewChapters(any()) } throws IllegalStateException("notification blocked")
        val client = mockk<SuwayomiGraphQlClient>()
        coEvery { client.getRecentChapters() } returns listOf(chapter(id = 10, fetchedAt = "1000", isRead = false))
        coEvery { client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY) } returns checkpointMeta(
            NewChapterCheckpoint(lastFetchedAt = 500L, lastChapterId = 9),
        )

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            reconciler(renderer).completeLibraryUpdate(client)
        }
        coVerify(exactly = 0) { client.setGlobalMeta(any(), any()) }
    }

    private fun download(state: String): SuwayomiDownloadDto {
        return SuwayomiDownloadDto(
            chapter = SuwayomiDownloadChapterDto(id = 20, name = "Chapter 20"),
            manga = SuwayomiDownloadMangaDto(id = 10, title = "Manga 10"),
            state = state,
        )
    }

    private fun syncStatus(
        state: String,
        startDate: String,
        endDate: String? = null,
    ): SuwayomiSyncStatusDto {
        return SuwayomiSyncStatusDto(
            state = state,
            startDate = startDate,
            endDate = endDate,
        )
    }

    private suspend fun ServerNotificationReconciler.completeLibraryUpdate(
        client: SuwayomiGraphQlClient,
    ): LibraryReconciliationResult {
        reconcileLibraryUpdate(
            client = client,
            serverIdentity = SERVER,
            status = SuwayomiLibraryUpdateStatusDto(
                jobsInfo = SuwayomiUpdaterJobsInfoDto(isRunning = true, totalJobs = 1, finishedJobs = 0),
            ),
        )
        return reconcileLibraryUpdate(
            client = client,
            serverIdentity = SERVER,
            status = SuwayomiLibraryUpdateStatusDto(
                jobsInfo = SuwayomiUpdaterJobsInfoDto(isRunning = false, totalJobs = 1, finishedJobs = 1),
            ),
        )
    }

    private fun reconciler(renderer: ServerNotificationRenderer): ServerNotificationReconciler {
        return ServerNotificationReconciler(
            renderer = renderer,
            checkpoints = ServerNotificationCheckpointStore(InMemoryPreferenceStore()),
        )
    }

    private fun checkpointMeta(checkpoint: NewChapterCheckpoint): SuwayomiGlobalMetaDto {
        return SuwayomiGlobalMetaDto(NEW_CHAPTER_CHECKPOINT_KEY, checkpoint.encode())
    }

    private fun chapter(
        id: Int,
        fetchedAt: String,
        isRead: Boolean,
        inLibraryAt: Long? = 0L,
    ): SuwayomiChapterWithMangaDto {
        return SuwayomiChapterWithMangaDto(
            id = id,
            fetchedAt = fetchedAt,
            isRead = isRead,
            mangaId = 100 + id,
            manga = SuwayomiMangaDto(
                id = 100 + id,
                inLibrary = true,
                inLibraryAt = inLibraryAt,
                sourceId = "1",
                title = "Manga $id",
                url = "/manga/$id",
            ),
            name = "Chapter $id",
            url = "/chapter/$id",
        )
    }

    private companion object {
        const val SERVER = "http://server-a"
    }
}
