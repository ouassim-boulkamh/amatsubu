package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import eu.kanade.domain.chapter.model.Chapter
import kotlinx.serialization.json.JsonObject

class ReaderServerStatePolicyTest {

    @Test
    fun `progress failure queues pending read-state only after chapter is marked read`() {
        assertTrue(shouldQueuePendingReadStateAfterProgressFailure(isChapterRead = true))
        assertFalse(shouldQueuePendingReadStateAfterProgressFailure(isChapterRead = false))
    }

    @Test
    fun `bookmark mutation failure rolls optimistic state back to previous value`() {
        assertFalse(bookmarkStateAfterReaderBookmarkFailure(requestedBookmarkState = true))
        assertTrue(bookmarkStateAfterReaderBookmarkFailure(requestedBookmarkState = false))
    }

    @Test
    fun `reader intent baseline captures confirmed server chapter state before optimistic edits`() {
        val baseline = chapter(
            read = false,
            lastPageRead = 4,
            bookmark = true,
        ).toReaderIntentBaseline()

        assertFalse(baseline.isRead)
        assertEquals(4, baseline.lastPageRead)
        assertTrue(baseline.isBookmarked)
    }

    private fun chapter(
        read: Boolean,
        lastPageRead: Long,
        bookmark: Boolean,
    ): Chapter {
        return Chapter(
            id = 10,
            mangaId = 1,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = 0,
            sourceOrder = 0,
            url = "chapter-url",
            name = "Chapter",
            dateUpload = 0,
            chapterNumber = 1.0,
            scanlator = null,
            lastModifiedAt = 0,
            version = 0,
            memo = JsonObject(emptyMap()),
        )
    }
}
