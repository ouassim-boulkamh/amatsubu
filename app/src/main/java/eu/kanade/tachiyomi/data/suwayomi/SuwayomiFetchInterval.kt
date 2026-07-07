package eu.kanade.tachiyomi.data.suwayomi

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.FetchIntervalCalculator
import tachiyomi.domain.manga.model.Manga
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

internal data class SuwayomiFetchEstimate(
    val nextUpdate: Long,
    val fetchInterval: Int,
)

internal fun List<SuwayomiChapterDto>.estimateFetchInterval(
    manga: Manga,
    now: ZonedDateTime = ZonedDateTime.now(),
): SuwayomiFetchEstimate? {
    if (isEmpty()) return null

    val chapters = map { it.toFetchIntervalChapter() }
    val interval = FetchIntervalCalculator.calculateInterval(
        chapters = chapters,
        zone = now.zone,
    )
    val nextUpdate = FetchIntervalCalculator.calculateNextUpdate(
        manga = manga,
        interval = interval,
        dateTime = now,
        window = FetchIntervalCalculator.getWindow(now),
    )

    return SuwayomiFetchEstimate(
        nextUpdate = nextUpdate,
        fetchInterval = interval,
    )
}

private fun SuwayomiChapterDto.toFetchIntervalChapter(): Chapter {
    return Chapter.create().copy(
        dateFetch = fetchedAt.toLongOrNull()?.seconds?.inWholeMilliseconds ?: 0L,
        dateUpload = uploadDate,
    )
}
