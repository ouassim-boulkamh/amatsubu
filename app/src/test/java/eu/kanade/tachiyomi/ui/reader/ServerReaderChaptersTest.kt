package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyPage
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga

class ServerReaderChaptersTest {

    @Test
    fun `selected chapter already present after filters is not appended again`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
        )

        val result = build(chapters, selectedChapter = chapters[1], skipRead = true)

        assertEquals(listOf(1L, 2L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `selected chapter filtered out is appended exactly once`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0, read = true),
        )

        val result = build(chapters, selectedChapter = chapters[1], skipRead = true)

        assertEquals(listOf(1L, 2L), result.chapterIds())
        assertEquals(1, result.chapterIds().count { it == 2L })
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip read keeps selected read chapter available`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0, read = true),
            chapter(id = 3, number = 3.0, read = true),
        )

        val result = build(chapters, selectedChapter = chapters[1], skipRead = true)

        assertEquals(listOf(1L, 2L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip filtered keeps selected filtered chapter available`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0, read = true),
            chapter(id = 3, number = 3.0),
        )
        val manga = manga(
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or
                Manga.CHAPTER_SORT_DESC or
                Manga.CHAPTER_SHOW_UNREAD,
        )

        val result = build(
            chapters,
            selectedChapter = chapters[1],
            skipFiltered = true,
            manga = manga,
        )

        assertEquals(listOf(1L, 2L, 3L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip duplicate preserves selected scanlator and id`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0, scanlator = "a"),
            chapter(id = 2, number = 1.0, scanlator = "b"),
            chapter(id = 3, number = 2.0, scanlator = "a"),
        )

        val result = build(chapters, selectedChapter = chapters[1], skipDupe = true)

        assertEquals(listOf(2L, 3L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
        assertEquals("b", result.selectedChapter.chapter.scanlator)
    }

    @Test
    fun `downloaded only uses server downloaded ids`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
            chapter(id = 3, number = 3.0),
        )

        val result = build(
            chapters,
            selectedChapter = chapters[1],
            downloadedOnly = true,
            downloadedChapterIds = setOf(2L, 3L),
        )

        assertEquals(listOf(2L, 3L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `downloaded only includes fresh local device copy ids`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
            chapter(id = 3, number = 3.0),
        )

        val result = build(
            chapters,
            selectedChapter = chapters[1],
            downloadedOnly = true,
            downloadedChapterIds = setOf(3L),
            localDownloadedChapterIds = setOf(2L),
        )

        assertEquals(listOf(2L, 3L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `downloaded only rejects unavailable selected chapter`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
        )

        assertThrows(IllegalStateException::class.java) {
            build(
                chapters,
                selectedChapter = chapters[1],
                downloadedOnly = true,
                downloadedChapterIds = setOf(1L),
            )
        }
    }

    @Test
    fun `skip filtered uses local downloaded ids independently from server downloaded ids`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
            chapter(id = 3, number = 3.0),
        )
        val manga = manga(
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or
                Manga.CHAPTER_SORT_DESC or
                Manga.CHAPTER_SHOW_LOCAL_DOWNLOADED,
        )

        val result = build(
            chapters = chapters,
            selectedChapter = chapters[1],
            skipFiltered = true,
            manga = manga,
            downloadedChapterIds = setOf(3L),
            localDownloadedChapterIds = setOf(1L, 2L),
        )

        assertEquals(listOf(1L, 2L), result.chapterIds())
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip filtered excludes local downloaded chapters with not locally downloaded filter`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0),
            chapter(id = 2, number = 2.0),
            chapter(id = 3, number = 3.0),
        )
        val manga = manga(
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or
                Manga.CHAPTER_SORT_DESC or
                Manga.CHAPTER_SHOW_NOT_LOCAL_DOWNLOADED,
        )

        val result = build(
            chapters = chapters,
            selectedChapter = chapters[2],
            skipFiltered = true,
            manga = manga,
            localDownloadedChapterIds = setOf(1L, 2L),
        )

        assertEquals(listOf(3L), result.chapterIds())
        assertEquals(3L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip filtered excludes server scanlator exclusions from reader navigation`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0, scanlator = "wanted"),
            chapter(id = 2, number = 2.0, scanlator = "hidden"),
            chapter(id = 3, number = 3.0, scanlator = "wanted"),
        )

        val result = build(
            chapters = chapters,
            selectedChapter = chapters[0],
            skipFiltered = true,
            excludedScanlators = setOf("hidden"),
        )

        assertEquals(listOf(1L, 3L), result.chapterIds())
        assertEquals(1L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `skip filtered keeps explicitly selected excluded scanlator chapter once`() {
        val chapters = listOf(
            chapter(id = 1, number = 1.0, scanlator = "wanted"),
            chapter(id = 2, number = 2.0, scanlator = "hidden"),
            chapter(id = 3, number = 3.0, scanlator = "wanted"),
        )

        val result = build(
            chapters = chapters,
            selectedChapter = chapters[1],
            skipFiltered = true,
            excludedScanlators = setOf("hidden"),
        )

        assertEquals(listOf(1L, 2L, 3L), result.chapterIds())
        assertEquals(1, result.chapterIds().count { it == 2L })
        assertEquals(2L, result.selectedChapter.chapter.id)
    }

    @Test
    fun `adjacent windows around selected chapter use distinct ids`() {
        val chapters = listOf(
            chapter(id = 347, number = 347.0),
            chapter(id = 347, number = 347.0, name = "duplicate payload"),
            chapter(id = 348, number = 348.0),
            chapter(id = 349, number = 349.0),
        )

        val result = build(chapters, selectedChapter = chapters[0])
        val selectedIndex = result.chapters.indexOf(result.selectedChapter)
        val current = result.chapters[selectedIndex]
        val next = result.chapters.getOrNull(selectedIndex + 1)

        assertEquals(347L, current.chapter.id)
        assertEquals(348L, next?.chapter?.id)
        assertNotEquals(current.chapter.id, next?.chapter?.id)
    }

    @Test
    fun `device copy reader chapters use reader navigation order`() {
        val copies = listOf(
            copy(chapterId = 3, sourceOrder = 30, chapterNumber = 3f),
            copy(chapterId = 2, sourceOrder = 20, chapterNumber = 2f),
            copy(chapterId = 1, sourceOrder = 10, chapterNumber = 1f),
        )

        val result = buildServerReaderChaptersFromDeviceCopies(
            copies = copies,
            selectedCopy = copies[1],
            skipDupe = false,
        ).chapterList
        val selectedIndex = result.chapters.indexOf(result.selectedChapter)

        assertEquals(listOf(1L, 2L, 3L), result.chapterIds())
        assertEquals(1L, result.chapters.getOrNull(selectedIndex - 1)?.chapter?.id)
        assertEquals(3L, result.chapters.getOrNull(selectedIndex + 1)?.chapter?.id)
    }

    private fun build(
        chapters: List<Chapter>,
        selectedChapter: Chapter,
        skipRead: Boolean = false,
        skipFiltered: Boolean = false,
        skipDupe: Boolean = false,
        downloadedOnly: Boolean = false,
        downloadedChapterIds: Set<Long> = emptySet(),
        localDownloadedChapterIds: Set<Long> = emptySet(),
        excludedScanlators: Set<String> = emptySet(),
        manga: Manga = manga(),
    ): ServerReaderChapterList {
        return buildServerReaderChapters(
            chapters = chapters,
            manga = manga,
            selectedChapter = selectedChapter,
            skipRead = skipRead,
            skipFiltered = skipFiltered,
            skipDupe = skipDupe,
            downloadedOnly = downloadedOnly,
            downloadedChapterIds = downloadedChapterIds,
            localDownloadedChapterIds = localDownloadedChapterIds,
            excludedScanlators = excludedScanlators,
        )
    }

    private fun ServerReaderChapterList.chapterIds(): List<Long> {
        return chapters.map { it.chapter.id }
    }

    private fun chapter(
        id: Long,
        number: Double,
        read: Boolean = false,
        bookmark: Boolean = false,
        scanlator: String? = null,
        name: String = "Chapter $number",
    ): Chapter {
        return Chapter(
            id = id,
            mangaId = 1,
            read = read,
            bookmark = bookmark,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = id,
            url = "/chapters/$id",
            name = name,
            dateUpload = 0,
            chapterNumber = number,
            scanlator = scanlator,
            lastModifiedAt = 0,
            version = 0,
            memo = JsonObject.EMPTY,
        )
    }

    private fun manga(chapterFlags: Long = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC): Manga {
        return Manga.create().copy(
            id = 1,
            source = 1,
            title = "Manga",
            chapterFlags = chapterFlags,
        )
    }

    private fun copy(
        chapterId: Int,
        sourceOrder: Int,
        chapterNumber: Float,
    ): ClientDeviceChapterCopy {
        return ClientDeviceChapterCopy(
            serverKey = "server",
            mangaId = 1,
            chapterId = chapterId,
            mangaTitle = "Manga",
            chapterTitle = "Chapter $chapterNumber",
            chapterUrl = "/chapters/$chapterId",
            chapterRealUrl = null,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            uploadDate = 0,
            fetchedAt = "now",
            scanlator = null,
            storagePath = null,
            manifestHash = "hash-$chapterId",
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            expectedPageCount = 1,
            downloadedPageCount = 1,
            createdAt = 0,
            updatedAt = 0,
            verifiedAt = 0,
            orphanedAt = null,
            pages = listOf(
                ClientDeviceChapterCopyPage(
                    index = 0,
                    sourceUrl = "https://example.invalid/$chapterId/1.jpg",
                    localUri = "file:///chapter-$chapterId/page-1.jpg",
                    isPresent = true,
                ),
            ),
        )
    }
}
