package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PendingReadStateReplayTest {

    @Test
    fun `accepted pending read-state replay deletes only after server update succeeds`() = runTest {
        val pending = listOf(
            pending(chapterId = 10, isRead = true),
            pending(chapterId = 11, isRead = true),
            pending(chapterId = 12, isRead = false),
        )
        val client = FakePendingReadStateReplayClient()
        val deleted = mutableListOf<PendingServerReadState>()

        val pushed = replayPendingReadStates(
            pending = pending,
            updateChaptersRead = client::updateChaptersRead,
            deletePendingReadState = deleted::add,
        )

        assertEquals(3, pushed)
        assertEquals(
            listOf(
                ReadStateBatch(chapterIds = listOf(10, 11), isRead = true),
                ReadStateBatch(chapterIds = listOf(12), isRead = false),
            ),
            client.batches,
        )
        assertEquals(pending, deleted)
    }

    @Test
    fun `failed pending read-state replay retains rows for retry`() {
        val pending = listOf(
            pending(chapterId = 10, isRead = true),
            pending(chapterId = 11, isRead = true),
        )
        val replayError = IllegalStateException("server unavailable")
        val client = FakePendingReadStateReplayClient(error = replayError)
        val deleted = mutableListOf<PendingServerReadState>()

        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest {
                replayPendingReadStates(
                    pending = pending,
                    updateChaptersRead = client::updateChaptersRead,
                    deletePendingReadState = deleted::add,
                )
            }
        }

        assertEquals(replayError, thrown)
        assertEquals(listOf(ReadStateBatch(chapterIds = listOf(10, 11), isRead = true)), client.batches)
        assertEquals(emptyList<PendingServerReadState>(), deleted)
    }

    @Test
    fun `empty pending read-state replay does not call server`() = runTest {
        val client = FakePendingReadStateReplayClient()

        val pushed = replayPendingReadStates(
            pending = emptyList(),
            updateChaptersRead = client::updateChaptersRead,
            deletePendingReadState = {},
        )

        assertEquals(0, pushed)
        assertEquals(emptyList<ReadStateBatch>(), client.batches)
    }

    private fun pending(chapterId: Int, isRead: Boolean): PendingServerReadState {
        return PendingServerReadState(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = chapterId,
            isRead = isRead,
            createdAt = 1,
            updatedAt = 2,
        )
    }
}

private data class ReadStateBatch(
    val chapterIds: List<Int>,
    val isRead: Boolean,
)

private class FakePendingReadStateReplayClient(
    private val error: Throwable? = null,
) {
    val batches = mutableListOf<ReadStateBatch>()

    suspend fun updateChaptersRead(chapterIds: List<Int>, isRead: Boolean) {
        batches += ReadStateBatch(chapterIds, isRead)
        error?.let { throw it }
    }
}
