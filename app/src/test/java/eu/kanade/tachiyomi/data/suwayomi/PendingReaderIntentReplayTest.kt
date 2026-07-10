package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingReaderIntentReplayTest {

    @Test
    fun `accepted progress replay pushes desired state and deletes pending intent`() = runTest {
        val intent = progressIntent(
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsRead = true,
            desiredLastPageRead = 9,
        )
        val updatedProgress = mutableListOf<PendingServerReaderIntent>()
        val deleted = mutableListOf<PendingServerReaderIntent>()

        val result = replayPendingReaderIntents(
            pending = listOf(intent),
            currentBaseline = { it.baseline },
            updateProgress = { updatedProgress += it },
            updateBookmark = { error("bookmark should not be updated") },
            deletePendingIntent = { deleted += it },
        )

        assertEquals(1, result.pushed)
        assertEquals(emptyList<PendingServerReaderIntent>(), result.conflicted)
        assertEquals(listOf(intent), updatedProgress)
        assertEquals(listOf(intent), deleted)
    }

    @Test
    fun `accepted bookmark replay pushes desired state and deletes pending intent`() = runTest {
        val intent = bookmarkIntent(
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsBookmarked = true,
        )
        val updatedBookmarks = mutableListOf<PendingServerReaderIntent>()
        val deleted = mutableListOf<PendingServerReaderIntent>()

        val result = replayPendingReaderIntents(
            pending = listOf(intent),
            currentBaseline = { it.baseline },
            updateProgress = { error("progress should not be updated") },
            updateBookmark = { updatedBookmarks += it },
            deletePendingIntent = { deleted += it },
        )

        assertEquals(1, result.pushed)
        assertEquals(emptyList<PendingServerReaderIntent>(), result.conflicted)
        assertEquals(listOf(intent), updatedBookmarks)
        assertEquals(listOf(intent), deleted)
    }

    @Test
    fun `progress replay refuses changed server read history and retains pending intent`() = runTest {
        val intent = progressIntent(
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsRead = true,
            desiredLastPageRead = 9,
        )
        val updatedProgress = mutableListOf<PendingServerReaderIntent>()
        val deleted = mutableListOf<PendingServerReaderIntent>()

        val result = replayPendingReaderIntents(
            pending = listOf(intent),
            currentBaseline = { baseline(isRead = true, lastPageRead = 3, isBookmarked = false) },
            updateProgress = { updatedProgress += it },
            updateBookmark = { error("bookmark should not be updated") },
            deletePendingIntent = { deleted += it },
        )

        assertEquals(0, result.pushed)
        assertEquals(listOf(intent), result.conflicted)
        assertEquals(emptyList<PendingServerReaderIntent>(), updatedProgress)
        assertEquals(emptyList<PendingServerReaderIntent>(), deleted)
    }

    @Test
    fun `progress replay refuses changed server progress and retains pending intent`() = runTest {
        val intent = progressIntent(
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsRead = false,
            desiredLastPageRead = 9,
        )

        val result = replayPendingReaderIntents(
            pending = listOf(intent),
            currentBaseline = { baseline(isRead = false, lastPageRead = 4, isBookmarked = false) },
            updateProgress = { error("progress should not be updated") },
            updateBookmark = { error("bookmark should not be updated") },
            deletePendingIntent = { error("conflicted intent should be retained") },
        )

        assertEquals(0, result.pushed)
        assertEquals(listOf(intent), result.conflicted)
    }

    @Test
    fun `bookmark replay refuses changed server bookmark and retains pending intent`() = runTest {
        val intent = bookmarkIntent(
            baseline = baseline(isRead = false, lastPageRead = 3, isBookmarked = false),
            desiredIsBookmarked = true,
        )

        val result = replayPendingReaderIntents(
            pending = listOf(intent),
            currentBaseline = { baseline(isRead = false, lastPageRead = 3, isBookmarked = true) },
            updateProgress = { error("progress should not be updated") },
            updateBookmark = { error("bookmark should not be updated") },
            deletePendingIntent = { error("conflicted intent should be retained") },
        )

        assertEquals(0, result.pushed)
        assertEquals(listOf(intent), result.conflicted)
    }

    @Test
    fun `mutation failure stops replay and keeps remaining intents for retry`() = runTest {
        val first = progressIntent(chapterId = 10)
        val second = progressIntent(chapterId = 11)
        val deleted = mutableListOf<PendingServerReaderIntent>()

        runCatching {
            replayPendingReaderIntents(
                pending = listOf(first, second),
                currentBaseline = { it.baseline },
                updateProgress = {
                    if (it.chapterId == first.chapterId) {
                        throw IllegalStateException("server unavailable")
                    }
                },
                updateBookmark = { error("bookmark should not be updated") },
                deletePendingIntent = { deleted += it },
            )
        }

        assertEquals(emptyList<PendingServerReaderIntent>(), deleted)
    }

    private fun progressIntent(
        chapterId: Int = 10,
        baseline: ServerReaderIntentBaseline = baseline(isRead = false, lastPageRead = 0, isBookmarked = false),
        desiredIsRead: Boolean = false,
        desiredLastPageRead: Int = 1,
    ): PendingServerReaderIntent {
        return PendingServerReaderIntent(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = chapterId,
            type = PendingServerReaderIntentType.PROGRESS,
            baseline = baseline,
            desiredIsRead = desiredIsRead,
            desiredLastPageRead = desiredLastPageRead,
            desiredIsBookmarked = null,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }

    private fun bookmarkIntent(
        baseline: ServerReaderIntentBaseline,
        desiredIsBookmarked: Boolean,
    ): PendingServerReaderIntent {
        return PendingServerReaderIntent(
            serverKey = "server-a",
            mangaId = 1,
            chapterId = 10,
            type = PendingServerReaderIntentType.BOOKMARK,
            baseline = baseline,
            desiredIsRead = null,
            desiredLastPageRead = null,
            desiredIsBookmarked = desiredIsBookmarked,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }

    private fun baseline(
        isRead: Boolean,
        lastPageRead: Int,
        isBookmarked: Boolean,
    ): ServerReaderIntentBaseline {
        return ServerReaderIntentBaseline(
            isRead = isRead,
            lastPageRead = lastPageRead,
            isBookmarked = isBookmarked,
        )
    }
}
