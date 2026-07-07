package tachiyomi.domain.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Instant

class MangaTest {

    @Test
    fun `expected next update is null when next update is unknown`() {
        Manga.create()
            .copy(nextUpdate = 0L)
            .expectedNextUpdate shouldBe null
    }

    @Test
    fun `expected next update is null when manga is completed`() {
        Manga.create()
            .copy(
                status = SManga.COMPLETED.toLong(),
                nextUpdate = 1_700_000_000_000L,
            )
            .expectedNextUpdate shouldBe null
    }

    @Test
    fun `expected next update exposes positive next update for ongoing manga`() {
        Manga.create()
            .copy(nextUpdate = 1_700_000_000_000L)
            .expectedNextUpdate shouldBe Instant.ofEpochMilli(1_700_000_000_000L)
    }

    @Test
    fun `java serialization preserves manga through kotlinx bridge`() {
        val manga = Manga.create().copy(
            id = 42,
            source = 7,
            title = "Serialized",
            notes = "Client note",
            memo = buildJsonObject { put("key", "value") },
        )

        val bytes = ByteArrayOutputStream().use { byteStream ->
            ObjectOutputStream(byteStream).use { objectStream ->
                objectStream.writeObject(manga)
            }
            byteStream.toByteArray()
        }

        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            stream.readObject() as Manga
        }

        restored shouldBe manga
    }
}
