package eu.kanade.tachiyomi.data.suwayomi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

class SuwayomiFetchIntervalTest {

    private val now = ZonedDateTime.parse("2020-01-02T12:00:00Z")
    private val manga = Manga.create().copy(
        lastUpdate = epochMillis("2020-01-01T00:00:00Z"),
    )

    @Test
    fun `uses Mihon default interval when chapters have no usable timestamps`() {
        val estimate = listOf(
            chapter(id = 1, uploadDate = 0L, fetchedAt = "0"),
        ).estimateFetchInterval(manga, now)

        estimate shouldBe SuwayomiFetchEstimate(
            nextUpdate = epochMillis("2020-01-08T00:00:00Z"),
            fetchInterval = 7,
        )
    }

    @Test
    fun `estimates next update from upload dates`() {
        val estimate = listOf(
            chapter(id = 1, uploadDate = epochMillis("2020-01-01T00:00:00Z")),
            chapter(id = 2, uploadDate = epochMillis("2019-12-25T00:00:00Z")),
            chapter(id = 3, uploadDate = epochMillis("2019-12-18T00:00:00Z")),
        ).estimateFetchInterval(manga, now)

        estimate shouldBe SuwayomiFetchEstimate(
            nextUpdate = epochMillis("2020-01-08T00:00:00Z"),
            fetchInterval = 7,
        )
    }

    @Test
    fun `uses manga last update as next update base`() {
        val estimate = listOf(
            chapter(id = 1, uploadDate = epochMillis("2020-01-01T00:00:00Z")),
            chapter(id = 2, uploadDate = epochMillis("2019-12-25T00:00:00Z")),
            chapter(id = 3, uploadDate = epochMillis("2019-12-18T00:00:00Z")),
        ).estimateFetchInterval(
            manga = manga.copy(lastUpdate = epochMillis("2020-01-02T00:00:00Z")),
            now = now,
        )

        estimate shouldBe SuwayomiFetchEstimate(
            nextUpdate = epochMillis("2020-01-09T00:00:00Z"),
            fetchInterval = 7,
        )
    }

    @Test
    fun `falls back to fetch dates when upload dates are missing`() {
        val estimate = listOf(
            chapter(id = 1, uploadDate = 0L, fetchedAt = epochSeconds("2020-01-01T00:00:00Z").toString()),
            chapter(id = 2, uploadDate = 0L, fetchedAt = epochSeconds("2019-12-30T00:00:00Z").toString()),
            chapter(id = 3, uploadDate = 0L, fetchedAt = epochSeconds("2019-12-28T00:00:00Z").toString()),
        ).estimateFetchInterval(manga, now)

        estimate shouldBe SuwayomiFetchEstimate(
            nextUpdate = epochMillis("2020-01-03T00:00:00Z"),
            fetchInterval = 2,
        )
    }

    private fun chapter(
        id: Int,
        uploadDate: Long,
        fetchedAt: String = uploadDate.toString(),
    ): SuwayomiChapterDto {
        return SuwayomiChapterDto(
            id = id,
            mangaId = 1,
            name = "Chapter $id",
            uploadDate = uploadDate,
            fetchedAt = fetchedAt,
            url = "/chapter/$id",
        )
    }

    private fun epochMillis(value: String): Long {
        return ZonedDateTime.parse(value)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    private fun epochSeconds(value: String): Long {
        return epochMillis(value).milliseconds.inWholeSeconds
    }
}
