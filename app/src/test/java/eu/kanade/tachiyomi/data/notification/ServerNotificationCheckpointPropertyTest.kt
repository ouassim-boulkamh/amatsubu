package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadMangaDto
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ServerNotificationCheckpointPropertyTest {

    @Test
    fun `generated notification checkpoints are idempotent and isolated by server`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(downloadArb, 0..48)) { downloads ->
            val unique = downloads.distinctBy { ServerNotificationCheckpointStore.downloadErrorId(it) }
            val store = ServerNotificationCheckpointStore(InMemoryPreferenceStore())

            store.recordDownloadQueue(SERVER_A, visible = true, state = "STARTED")
            store.markDownloadErrorsNotified(downloads)
            store.markDownloadErrorsNotified(downloads)

            store.filterUnnotifiedDownloadErrors(unique) shouldBe emptyList()
            store.recordDownloadQueue(SERVER_B, visible = true, state = "STARTED")
            store.filterUnnotifiedDownloadErrors(unique) shouldBe unique
        }
    }

    private companion object {
        const val SERVER_A = "http://server-a"
        const val SERVER_B = "http://server-b"

        val downloadArb = Arb.bind(Arb.int(1..32), Arb.int(1..128)) { mangaId, chapterId ->
            SuwayomiDownloadDto(
                chapter = SuwayomiDownloadChapterDto(id = chapterId, name = "Chapter $chapterId"),
                manga = SuwayomiDownloadMangaDto(id = mangaId, title = "Manga $mangaId"),
                state = "ERROR",
            )
        }
    }
}
