package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerNotificationContentTest {

    @Test
    fun `new chapter lines include manga and chapter names when content is visible`() {
        val chapters = listOf(
            chapter(mangaId = 1, mangaTitle = "Visible Manga", chapterName = "Chapter 1"),
            chapter(mangaId = 1, mangaTitle = "Visible Manga", chapterName = "Chapter 2"),
        )

        val lines = ServerNotificationContent.newChapterLines(
            chapters = chapters,
            hideContent = false,
            maxLines = 5,
        )

        assertEquals(listOf("Visible Manga - Chapter 1, Chapter 2"), lines)
    }

    @Test
    fun `new chapter lines are omitted when content is hidden`() {
        val lines = ServerNotificationContent.newChapterLines(
            chapters = listOf(chapter(mangaId = 1, mangaTitle = "Hidden Manga", chapterName = "Hidden Chapter")),
            hideContent = true,
            maxLines = 5,
        )

        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `download detail redacts manga and chapter names when content is hidden`() {
        val detail = ServerNotificationContent.downloadDetail(
            mangaTitle = "Hidden Manga",
            chapterName = "Hidden Chapter",
            hideContent = true,
            redactedText = "Download queue",
        )

        assertEquals("Download queue", detail)
    }

    @Test
    fun `download detail includes manga and chapter names when content is visible`() {
        val detail = ServerNotificationContent.downloadDetail(
            mangaTitle = "Visible Manga",
            chapterName = "Visible Chapter",
            hideContent = false,
            redactedText = "Download queue",
        )

        assertEquals("Visible Manga - Visible Chapter", detail)
    }

    @Test
    fun `extension update lines include extension names when content is visible`() {
        val lines = ServerNotificationContent.extensionUpdateLines(
            extensions = listOf(extension("Visible Extension")),
            hideContent = false,
            maxLines = 5,
        )

        assertEquals(listOf("Visible Extension"), lines)
    }

    @Test
    fun `extension update lines are omitted when content is hidden`() {
        val lines = ServerNotificationContent.extensionUpdateLines(
            extensions = listOf(extension("Hidden Extension")),
            hideContent = true,
            maxLines = 5,
        )

        assertEquals(emptyList<String>(), lines)
    }

    private fun chapter(
        mangaId: Int,
        mangaTitle: String,
        chapterName: String,
    ): SuwayomiChapterWithMangaDto {
        return SuwayomiChapterWithMangaDto(
            id = mangaId * 100,
            mangaId = mangaId,
            manga = SuwayomiMangaDto(
                id = mangaId,
                sourceId = "source-$mangaId",
                title = mangaTitle,
                url = "/manga/$mangaId",
            ),
            name = chapterName,
            url = "/chapter/$mangaId",
        )
    }

    private fun extension(name: String): SuwayomiExtensionDto {
        return SuwayomiExtensionDto(
            isInstalled = true,
            hasUpdate = true,
            lang = "en",
            name = name,
            pkgName = "pkg.${name.lowercase().replace(' ', '.')}",
        )
    }
}
