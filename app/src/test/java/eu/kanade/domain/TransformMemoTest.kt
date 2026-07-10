package eu.kanade.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaStatus
import eu.kanade.domain.manga.model.UpdateStrategy

class TransformMemoTest {

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

        val updatedManga = manga.copy(
            title = "Manga updated",
            url = "/manga-updated",
            status = MangaStatus.ONGOING.value,
            updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE,
            initialized = true,
            memo = memo,
        )

        assertEquals(memo, updatedManga.memo)
    }
}
