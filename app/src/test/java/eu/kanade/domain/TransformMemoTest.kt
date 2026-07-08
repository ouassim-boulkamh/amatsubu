package eu.kanade.domain

import eu.kanade.domain.chapter.model.copyFromSChapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mihon.domain.manga.model.toDomainManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class TransformMemoTest {

    @Test
    fun `chapter transforms preserve memo`() {
        val memo = buildJsonObject { put("amatsubu.chapter", "server-value") }
        val chapter = Chapter.create().copy(
            id = 1,
            mangaId = 2,
            name = "Chapter 1",
            url = "/chapter/1",
            memo = memo,
        )

        assertEquals(memo, chapter.toSChapter().memo)
        assertEquals(memo, chapter.toDbChapter().memo)

        val sourceChapter = SChapter.create().also {
            it.name = "Chapter 1 updated"
            it.url = "/chapter/1-updated"
            it.date_upload = 123
            it.chapter_number = 1f
            it.scanlator = "Group"
            it.memo = memo
        }

        assertEquals(memo, chapter.copyFromSChapter(sourceChapter).memo)
    }

    @Test
    fun `manga transforms preserve memo`() {
        val memo = buildJsonObject { put("amatsubu.manga", "server-value") }
        val manga = Manga.create().copy(
            id = 1,
            source = 2,
            title = "Manga",
            url = "/manga",
            memo = memo,
        )

        assertEquals(memo, manga.toSManga().memo)

        val sourceManga = SManga.create().also {
            it.title = "Manga updated"
            it.url = "/manga-updated"
            it.status = SManga.ONGOING
            it.initialized = true
            it.memo = memo
        }

        assertEquals(memo, manga.copyFrom(sourceManga).memo)
        assertEquals(memo, sourceManga.toDomainManga(sourceId = 2).memo)
    }

    @Test
    fun `source model copies preserve memo`() {
        val chapterMemo = buildJsonObject { put("amatsubu.chapter", "copy") }
        val sourceChapter = SChapter.create().also {
            it.name = "Chapter"
            it.url = "/chapter"
            it.memo = chapterMemo
        }
        val copiedChapter = SChapter.create().also { it.copyFrom(sourceChapter) }
        assertEquals(chapterMemo, copiedChapter.memo)

        val mangaMemo = buildJsonObject { put("amatsubu.manga", "copy") }
        val sourceManga = SManga.create().also {
            it.title = "Manga"
            it.url = "/manga"
            it.memo = mangaMemo
        }
        assertEquals(mangaMemo, sourceManga.copy().memo)
    }
}
