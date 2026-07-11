package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ServerNotificationContentPropertyTest {

    @Test
    fun `generated hidden notification content never exposes manga chapter or extension text`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(notificationItemArb, 0..20)) { items ->
            val chapters = items.mapIndexed { index, item -> chapter(index, item.manga, item.chapter) }
            val extensions = items.map { extension(it.extension) }
            val redacted = "Download queue"

            ServerNotificationContent.newChapterLines(chapters, hideContent = true, maxLines = 10) shouldBe emptyList()
            ServerNotificationContent.extensionUpdateLines(extensions, hideContent = true, maxLines = 10) shouldBe
                emptyList()
            items.forEach { item ->
                ServerNotificationContent.downloadDetail(
                    item.manga,
                    item.chapter,
                    hideContent = true,
                    redactedText = redacted,
                ) shouldBe
                    redacted
            }
        }
    }

    private companion object {
        val safeTextArb = Arb.string(1..36).map { it.filter(Char::isLetterOrDigit).ifBlank { "secret" } }
        val notificationItemArb = Arb.bind(safeTextArb, safeTextArb, safeTextArb) { manga, chapter, extension ->
            NotificationItem(manga, chapter, extension)
        }

        fun chapter(id: Int, mangaTitle: String, chapterName: String) = SuwayomiChapterWithMangaDto(
            id = id,
            mangaId = id,
            manga = SuwayomiMangaDto(id = id, sourceId = "source", title = mangaTitle, url = "/manga/$id"),
            name = chapterName,
            url = "/chapter/$id",
        )

        fun extension(name: String) = SuwayomiExtensionDto(
            isInstalled = true,
            hasUpdate = true,
            lang = "en",
            name = name,
            pkgName = "pkg.$name",
        )

        data class NotificationItem(val manga: String, val chapter: String, val extension: String)
    }
}
