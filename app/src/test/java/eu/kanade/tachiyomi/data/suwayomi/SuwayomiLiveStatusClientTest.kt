package eu.kanade.tachiyomi.data.suwayomi

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class SuwayomiLiveStatusClientTest {

    @Test
    fun `monitor mode reconciles library status every five minutes while subscription is healthy`() = runTest {
        val graphQlClient = mockk<SuwayomiGraphQlClient>()
        val subscriptionClient = mockk<SuwayomiGraphQlSubscriptionClient>()
        coEvery { graphQlClient.getLibraryUpdateStatus() } returns SuwayomiLibraryUpdateStatusDto()
        every { subscriptionClient.libraryUpdateStatusChanged(any()) } returns flow { awaitCancellation() }

        val job =
            launch {
                SuwayomiLiveStatusClient(
                    graphQlClient,
                    subscriptionClient,
                ).libraryUpdateStatusFlow(monitorMode = true).collect {
                }
            }
        runCurrent()
        coVerify(exactly = 1) { graphQlClient.getLibraryUpdateStatus() }

        advanceTimeBy(5.minutes.inWholeMilliseconds)
        runCurrent()
        coVerify(exactly = 2) { graphQlClient.getLibraryUpdateStatus() }
        job.cancelAndJoin()
    }

    @Test
    fun `monitor mode backs off failed subscriptions through the fifteen minute cap`() = runTest {
        val graphQlClient = mockk<SuwayomiGraphQlClient>()
        val subscriptionClient = mockk<SuwayomiGraphQlSubscriptionClient>()
        coEvery { graphQlClient.getLibraryUpdateStatus() } returns SuwayomiLibraryUpdateStatusDto()
        every { subscriptionClient.libraryUpdateStatusChanged(any()) } returns flow { throw IOException("offline") }

        val job =
            launch {
                SuwayomiLiveStatusClient(
                    graphQlClient,
                    subscriptionClient,
                ).libraryUpdateStatusFlow(monitorMode = true).collect {
                }
            }
        runCurrent()
        coVerify(exactly = 1) { subscriptionClient.libraryUpdateStatusChanged(any()) }

        advanceTimeBy(1.minutes.inWholeMilliseconds)
        runCurrent()
        coVerify(exactly = 2) { subscriptionClient.libraryUpdateStatusChanged(any()) }

        advanceTimeBy(2.minutes.inWholeMilliseconds)
        advanceTimeBy(4.minutes.inWholeMilliseconds)
        advanceTimeBy(8.minutes.inWholeMilliseconds)
        advanceTimeBy(15.minutes.inWholeMilliseconds)
        runCurrent()
        coVerify(exactly = 6) { subscriptionClient.libraryUpdateStatusChanged(any()) }
        job.cancelAndJoin()
    }

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
