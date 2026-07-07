package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuwayomiTrackerProgressSyncTest {

    @Test
    fun `read changes deduplicate manga ids for tracker progress`() {
        val mangaIds = trackerProgressMangaIdsForReadStateChange(
            read = true,
            changedMangaIds = listOf(10, 10, 20),
        )

        assertEquals(listOf(10, 20), mangaIds)
    }

    @Test
    fun `unread changes do not sync tracker progress`() {
        val mangaIds = trackerProgressMangaIdsForReadStateChange(
            read = false,
            changedMangaIds = listOf(10, 20),
        )

        assertEquals(emptyList<Int>(), mangaIds)
    }

    @Test
    fun `tracker sync failures do not stop remaining manga sync`() = runTest {
        val syncedMangaIds = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()

        syncTrackerProgressAfterReadStateChange(
            read = true,
            changedMangaIds = listOf(10, 20),
            trackProgress = { mangaId ->
                syncedMangaIds += mangaId
                if (mangaId == 10) error("tracker unavailable")
            },
            onFailure = failures::add,
        )

        assertEquals(listOf(10, 20), syncedMangaIds)
        assertEquals(1, failures.size)
        assertEquals("tracker unavailable", failures.single().message)
    }
}
