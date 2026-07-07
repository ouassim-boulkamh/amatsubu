package eu.kanade.tachiyomi.data.suwayomi

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class SuwayomiLiveStatusClientTest {

    @Test
    fun `download status polling continues when websocket subscription fails`() = runTest {
        val initial = downloadStatus("STARTED", download(id = 1, state = "DOWNLOADING", position = 0))
        val polled = downloadStatus("STOPPED")
        val graphQlClient = mockk<SuwayomiGraphQlClient>()
        val subscriptionClient = mockk<SuwayomiGraphQlSubscriptionClient>()
        coEvery { graphQlClient.getDownloadStatus() } returnsMany listOf(initial, polled)
        every { subscriptionClient.downloadStatusChanged(any()) } returns flow {
            throw IOException("websocket unavailable")
        }

        val statuses = SuwayomiLiveStatusClient(graphQlClient, subscriptionClient)
            .downloadStatusFlow(pollInterval = 1.milliseconds)
            .take(2)
            .toList()

        assertEquals(listOf(initial, polled), statuses)
    }

    private fun downloadStatus(
        state: String,
        vararg downloads: SuwayomiDownloadDto,
    ): SuwayomiDownloadStatusDto {
        return SuwayomiDownloadStatusDto(
            state = state,
            queue = downloads.toList(),
        )
    }

    private fun download(
        id: Int,
        state: String,
        position: Int,
    ): SuwayomiDownloadDto {
        return SuwayomiDownloadDto(
            chapter = SuwayomiDownloadChapterDto(
                id = id,
                name = "Chapter $id",
            ),
            manga = SuwayomiDownloadMangaDto(
                id = id,
                title = "Manga $id",
            ),
            progress = 0.0,
            state = state,
            position = position,
        )
    }
}
