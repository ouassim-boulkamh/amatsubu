package eu.kanade.domain.library.model

import eu.kanade.domain.manga.model.Manga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LibraryMangaTest {

    @Test
    fun `unread count defaults to total chapters minus read count`() {
        libraryManga(
            totalChapters = 10,
            readCount = 4,
        ).unreadCount shouldBe 6
    }

    @Test
    fun `unread count can be provided independently from chapter aggregates`() {
        libraryManga(
            totalChapters = 0,
            readCount = 0,
            unreadCount = 7,
        ).unreadCount shouldBe 7
    }

    private fun libraryManga(
        totalChapters: Long,
        readCount: Long,
        unreadCount: Long = totalChapters - readCount,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create(),
            categories = emptyList(),
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
            unreadCount = unreadCount,
        )
    }
}
