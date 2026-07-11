package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.data.suwayomi.generated.ClearDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteDownloadedChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DequeueChapterDownloadMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DequeueChapterDownloadsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.EnqueueChapterDownloadsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetDownloadStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ReorderChapterDownloadMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StartDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StopDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ClearDownloaderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteDownloadedChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DequeueChapterDownloadInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DequeueChapterDownloadsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.EnqueueChapterDownloadsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ReorderChapterDownloadInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StartDownloaderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StopDownloaderInput

internal class SuwayomiDownloadGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
    private val snapshotCache: SuwayomiSnapshotCache?,
    private val serverKey: () -> String,
) {

    suspend fun getDownloadStatus(): SuwayomiDownloadStatusDto {
        val response = apolloClientFactory.create().query(GetDownloadStatusQuery()).execute()
        val status = response.data?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no download status")
        snapshotCache?.storeDownloadStatus(serverKey(), status)
        return status
    }

    suspend fun getDownloadStatusSnapshot(): SuwayomiSnapshot<SuwayomiDownloadStatusDto>? {
        return snapshotCache?.getDownloadStatus(serverKey())
    }

    suspend fun enqueueChapterDownloads(chapterIds: List<Int>) {
        val response = apolloClientFactory.create().mutation(
            EnqueueChapterDownloadsMutation(EnqueueChapterDownloadsInput(ids = chapterIds)),
        ).execute()
        if (response.data?.enqueueChapterDownloads == null) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no enqueue result")
        }
    }

    suspend fun dequeueChapterDownload(chapterId: Int) {
        val response = apolloClientFactory.create().mutation(
            DequeueChapterDownloadMutation(DequeueChapterDownloadInput(id = chapterId)),
        ).execute()
        if (response.data?.dequeueChapterDownload == null) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no dequeue result")
        }
    }

    suspend fun dequeueChapterDownloads(chapterIds: List<Int>): SuwayomiDownloadStatusDto {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return getDownloadStatus()

        val response = apolloClientFactory.create().mutation(
            DequeueChapterDownloadsMutation(DequeueChapterDownloadsInput(ids = ids)),
        ).execute()
        return response.data?.dequeueChapterDownloads?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no dequeue status")
    }

    suspend fun startDownloader() {
        val response = apolloClientFactory.create().mutation(StartDownloaderMutation(StartDownloaderInput())).execute()
        if (response.data?.startDownloader ==
            null
        ) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no start result")
        }
    }

    suspend fun stopDownloader() {
        val response = apolloClientFactory.create().mutation(StopDownloaderMutation(StopDownloaderInput())).execute()
        if (response.data?.stopDownloader ==
            null
        ) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no stop result")
        }
    }

    suspend fun clearDownloader() {
        val response = apolloClientFactory.create().mutation(ClearDownloaderMutation(ClearDownloaderInput())).execute()
        if (response.data?.clearDownloader ==
            null
        ) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no clear result")
        }
    }

    suspend fun reorderChapterDownload(chapterId: Int, to: Int): SuwayomiDownloadStatusDto {
        val response = apolloClientFactory.create().mutation(
            ReorderChapterDownloadMutation(ReorderChapterDownloadInput(chapterId = chapterId, to = to)),
        ).execute()
        return response.data?.reorderChapterDownload?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reordered download status")
    }

    suspend fun deleteDownloadedChapters(chapterIds: List<Int>): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().mutation(
            DeleteDownloadedChaptersMutation(DeleteDownloadedChaptersInput(ids = chapterIds)),
        ).execute()
        return response.data?.deleteDownloadedChapters?.chapters?.map { it.amatsubuChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no deleted chapters")
    }
}
