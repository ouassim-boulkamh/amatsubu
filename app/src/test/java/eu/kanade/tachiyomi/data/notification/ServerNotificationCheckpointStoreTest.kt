package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiUpdaterJobsInfoDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ServerNotificationCheckpointStoreTest {

    @Test
    fun `library update records observed start and completion times`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

        val started = store.recordLibraryUpdate(
            serverIdentity = SERVER_A,
            jobs = SuwayomiUpdaterJobsInfoDto(isRunning = true),
            nowMillis = 1_000L,
        )
        val completed = store.recordLibraryUpdate(
            serverIdentity = SERVER_A,
            jobs = SuwayomiUpdaterJobsInfoDto(isRunning = false),
            nowMillis = 2_000L,
        )

        assertFalse(started.wasRunning)
        assertTrue(started.isRunning)
        assertEquals(1_000L, started.startedAt)
        assertTrue(completed.wasRunning)
        assertFalse(completed.isRunning)
        assertTrue(completed.completed)
        assertEquals(1_000L, completed.startedAt)
        assertEquals(2_000L, completed.finishedAt)
    }

    @Test
    fun `notified chapters are filtered until the server identity changes`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())
        store.recordLibraryUpdate(SERVER_A, SuwayomiUpdaterJobsInfoDto(isRunning = false))
        store.markChaptersNotified(listOf(1, 2))

        assertEquals(setOf(3), store.filterUnnotifiedChapterIds(listOf(1, 2, 3)))

        store.recordLibraryUpdate(SERVER_B, SuwayomiUpdaterJobsInfoDto(isRunning = false))

        assertEquals(setOf(1, 2, 3), store.filterUnnotifiedChapterIds(listOf(1, 2, 3)))
    }

    @Test
    fun `download queue transition records visibility and state`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

        val shown = store.recordDownloadQueue(
            serverIdentity = SERVER_A,
            visible = true,
            state = "STARTED",
        )
        val hidden = store.recordDownloadQueue(
            serverIdentity = SERVER_A,
            visible = false,
            state = "STOPPED",
        )

        assertFalse(shown.wasVisible)
        assertTrue(shown.isVisible)
        assertFalse(shown.becameHidden)
        assertTrue(hidden.wasVisible)
        assertFalse(hidden.isVisible)
        assertTrue(hidden.becameHidden)
        assertEquals("STARTED", hidden.previousState)
        assertEquals("STOPPED", hidden.state)
    }

    @Test
    fun `download error ids are stored and reset per server`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())
        val download = download(mangaId = 10, chapterId = 20)

        store.recordDownloadQueue(SERVER_A, visible = true, state = "STARTED")
        store.markDownloadErrorsNotified(listOf(download))

        assertEquals(
            setOf(ServerNotificationCheckpointStore.downloadErrorId(download)),
            store.notifiedDownloadErrorIds(),
        )

        store.recordDownloadQueue(SERVER_B, visible = true, state = "STARTED")

        assertEquals(emptySet<String>(), store.notifiedDownloadErrorIds())
    }

    @Test
    fun `download errors are filtered until the server identity changes`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())
        val first = download(mangaId = 10, chapterId = 20)
        val second = download(mangaId = 11, chapterId = 21)

        store.recordDownloadQueue(SERVER_A, visible = true, state = "STARTED")
        store.markDownloadErrorsNotified(listOf(first))

        assertEquals(listOf(second), store.filterUnnotifiedDownloadErrors(listOf(first, second)))

        store.recordDownloadQueue(SERVER_B, visible = true, state = "STARTED")

        assertEquals(listOf(first, second), store.filterUnnotifiedDownloadErrors(listOf(first, second)))
    }

    @Test
    fun `active server job reflects library and download checkpoint state`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

        assertFalse(store.hasActiveServerJob(SERVER_A))

        store.recordLibraryUpdate(
            serverIdentity = SERVER_A,
            jobs = SuwayomiUpdaterJobsInfoDto(isRunning = true, totalJobs = 1, finishedJobs = 0),
            nowMillis = 1L,
        )
        assertTrue(store.hasActiveServerJob(SERVER_A))

        store.recordLibraryUpdate(
            serverIdentity = SERVER_A,
            jobs = SuwayomiUpdaterJobsInfoDto(isRunning = false, totalJobs = 1, finishedJobs = 1),
            nowMillis = 2L,
        )
        assertFalse(store.hasActiveServerJob(SERVER_A))

        store.recordDownloadQueue(serverIdentity = SERVER_A, visible = true, state = "STOPPED")
        assertTrue(store.hasActiveServerJob(SERVER_A))
        assertFalse(store.hasActiveServerJob(SERVER_B))
    }

    @Test
    fun `syncyomi terminal notification is emitted once after observed running state`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

        val staleTerminal = store.recordSyncYomiStatus(
            serverIdentity = SERVER_A,
            status = syncStatus(state = "SUCCESS", startDate = "1", endDate = "2"),
        )
        val running = store.recordSyncYomiStatus(
            serverIdentity = SERVER_A,
            status = syncStatus(state = "MERGING", startDate = "3"),
        )
        val terminal = store.recordSyncYomiStatus(
            serverIdentity = SERVER_A,
            status = syncStatus(state = "SUCCESS", startDate = "3", endDate = "4"),
        )
        val duplicateTerminal = store.recordSyncYomiStatus(
            serverIdentity = SERVER_A,
            status = syncStatus(state = "SUCCESS", startDate = "3", endDate = "4"),
        )

        assertFalse(staleTerminal.notifyTerminal)
        assertTrue(running.isRunning)
        assertTrue(terminal.notifyTerminal)
        assertFalse(duplicateTerminal.notifyTerminal)
    }

    @Test
    fun `syncyomi active state is reset per server`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

        store.recordSyncYomiStatus(
            serverIdentity = SERVER_A,
            status = syncStatus(state = "UPLOADING", startDate = "1"),
        )

        assertTrue(store.hasActiveServerJob(SERVER_A))
        assertFalse(store.hasActiveServerJob(SERVER_B))
    }

    @Test
    fun `extension update ids are filtered until version or server identity changes`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())
        val firstVersion = extension(pkgName = "ext.one", versionCode = 1)
        val sameVersion = extension(pkgName = "ext.one", versionCode = 1)
        val nextVersion = extension(pkgName = "ext.one", versionCode = 2)

        assertEquals(listOf(firstVersion), store.filterUnnotifiedExtensionUpdates(SERVER_A, listOf(firstVersion)))
        store.markExtensionUpdatesNotified(listOf(firstVersion))

        assertEquals(emptyList<SuwayomiExtensionDto>(), store.filterUnnotifiedExtensionUpdates(SERVER_A, listOf(sameVersion)))
        assertEquals(listOf(nextVersion), store.filterUnnotifiedExtensionUpdates(SERVER_A, listOf(nextVersion)))
        assertEquals(listOf(sameVersion), store.filterUnnotifiedExtensionUpdates(SERVER_B, listOf(sameVersion)))
    }

    @Test
    fun `extension update checkpoint ignores unavailable and up to date extensions`() {
        val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())
        val update = extension(pkgName = "ext.update", hasUpdate = true, isInstalled = true)
        val available = extension(pkgName = "ext.available", hasUpdate = true, isInstalled = false)
        val current = extension(pkgName = "ext.current", hasUpdate = false, isInstalled = true)

        assertEquals(
            listOf(update),
            store.filterUnnotifiedExtensionUpdates(SERVER_A, listOf(update, available, current)),
        )
    }

    private fun download(
        mangaId: Int,
        chapterId: Int,
    ): SuwayomiDownloadDto {
        return SuwayomiDownloadDto(
            chapter = SuwayomiDownloadChapterDto(
                id = chapterId,
                name = "Chapter $chapterId",
            ),
            manga = SuwayomiDownloadMangaDto(
                id = mangaId,
                title = "Manga $mangaId",
            ),
            state = "ERROR",
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

    private fun extension(
        pkgName: String,
        versionCode: Long = 1,
        hasUpdate: Boolean = true,
        isInstalled: Boolean = true,
    ): SuwayomiExtensionDto {
        return SuwayomiExtensionDto(
            isInstalled = isInstalled,
            hasUpdate = hasUpdate,
            lang = "en",
            name = pkgName,
            pkgName = pkgName,
            versionCode = versionCode,
            versionName = versionCode.toString(),
        )
    }

    private companion object {
        const val SERVER_A = "http://server-a"
        const val SERVER_B = "http://server-b"
    }
}
