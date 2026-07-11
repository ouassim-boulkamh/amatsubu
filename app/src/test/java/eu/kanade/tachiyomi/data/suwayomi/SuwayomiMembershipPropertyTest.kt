package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SuwayomiMembershipPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    suspend fun `generated non-library manga rows never survive membership filtering`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(mangaRowArb, 0..24)) { rows ->
            val filtered = rows.filterInLibraryMangas()

            filtered.all { it.inLibrary } shouldBe true
            filtered.map { it.id } shouldContainExactly rows
                .filter { manga -> manga.inLibrary && rows.count { it.id == manga.id } == 1 }
                .map { it.id }
        }
    }

    @Test
    suspend fun `snapshot round trip cannot resurrect generated server-removed library rows`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(mangaRowArb, 0..24)) { rows ->
            val decodedRows = rows
                .map { json.encodeToString(it) }
                .map { payload -> json.decodeFromString<SuwayomiMangaDto>(payload) }

            val offlineRows = decodedRows.filterInLibraryMangas()

            offlineRows.all { it.inLibrary } shouldBe true
            offlineRows.map { it.id } shouldContainExactly rows
                .filter { manga -> manga.inLibrary && rows.count { it.id == manga.id } == 1 }
                .map { it.id }
        }
    }

    @Test
    suspend fun `generated sparse manga payloads preserve explicit defaults and nullable server fields`() {
        checkAll(FuzzTestConfig.caseCount(), sparseMangaArb) { manga ->
            val payload = """
                {
                  "id": ${manga.id},
                  "sourceId": "${manga.sourceId}",
                  "title": "${manga.title}",
                  "url": "${manga.url}"
                }
            """.trimIndent()

            val decoded = json.decodeFromString<SuwayomiMangaDto>(payload)

            decoded.id shouldBe manga.id
            decoded.sourceId shouldBe manga.sourceId
            decoded.title shouldBe manga.title
            decoded.url shouldBe manga.url
            decoded.inLibrary shouldBe false
            decoded.artist shouldBe null
            decoded.author shouldBe null
            decoded.thumbnailUrl shouldBe null
            decoded.genre shouldBe emptyList()
            decoded.status shouldBe MangaStatus.UNKNOWN
            decoded.updateStrategy shouldBe UpdateStrategy.ALWAYS_UPDATE
        }
    }

    @Test
    suspend fun `hostile raw manga JSON fails closed for absent membership and duplicate identities`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(mangaRowArb, 1..24)) { rows ->
            val hostileRows = rows.flatMap { manga ->
                listOf(
                    rawMangaJson(manga, includeMembership = manga.inLibrary, futureField = true),
                    rawMangaJson(manga.copy(inLibrary = false), includeMembership = false),
                )
            }
            val decoded = hostileRows.map { json.decodeFromString<SuwayomiMangaDto>(it) }

            decoded.filterInLibraryMangas() shouldBe emptyList()
        }
    }

    @Test
    fun `conflicting duplicate manga identities are excluded from library visibility`() {
        val rows = listOf(
            json.decodeFromString<SuwayomiMangaDto>(
                """{"id":42,"sourceId":"1","title":"stale","url":"/manga/42","inLibrary":true}""",
            ),
            json.decodeFromString<SuwayomiMangaDto>(
                """{"id":42,"sourceId":"1","title":"removed","url":"/manga/42","inLibrary":false}""",
            ),
        )

        rows.filterInLibraryMangas() shouldBe emptyList()
    }

    @Test
    suspend fun `unknown additive fields preserve mapping while incompatible values are rejected`() {
        checkAll(FuzzTestConfig.caseCount(), mangaRowArb) { manga ->
            val additive = json.decodeFromString<SuwayomiMangaDto>(
                rawMangaJson(manga, includeMembership = manga.inLibrary, futureField = true),
            )

            additive shouldBe manga

            val incompatible = rawMangaJson(manga, includeMembership = manga.inLibrary)
                .replace("}", ",\"status\":\"FUTURE_STATUS\"}")
            kotlin.runCatching { json.decodeFromString<SuwayomiMangaDto>(incompatible) }
                .exceptionOrNull().shouldBeInstanceOf<SerializationException>()
        }
    }

    @Test
    fun `partial GraphQL data and errors are represented without invented payload data`() {
        val errorOnly = json.decodeFromString(
            GraphQlResponse.serializer(FetchSourceMangaData.serializer()),
            """
            {"data":null,"errors":[{"message":"partial failure"}]}
            """.trimIndent(),
        )

        errorOnly.data shouldBe null
        errorOnly.errors.map { it.message } shouldContainExactly listOf("partial failure")
    }

    private fun rawMangaJson(
        manga: SuwayomiMangaDto,
        includeMembership: Boolean,
        futureField: Boolean = false,
    ): String {
        val membership = if (includeMembership) ",\"inLibrary\":true" else ""
        val future = if (futureField) ",\"futureServerField\":{\"nested\":[1,2,3]}" else ""
        val identity = """"id":${manga.id},"sourceId":"${manga.sourceId}""""
        val text = """"title":"${manga.title}","url":"${manga.url}""""
        return "{$identity,$text$membership$future}"
    }

    private companion object {
        val safeTextArb = Arb.string(1..32).map { value ->
            value
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                .ifBlank { "value" }
        }

        val sparseMangaArb: Arb<SuwayomiMangaDto> = Arb.bind(
            Arb.int(1..100_000),
            Arb.int(1..10_000),
            safeTextArb,
        ) { id, sourceId, title ->
            SuwayomiMangaDto(
                id = id,
                sourceId = sourceId.toString(),
                title = title,
                url = "/manga/$id",
            )
        }

        val mangaRowArb: Arb<SuwayomiMangaDto> = Arb.bind(
            Arb.int(1..100_000),
            Arb.int(1..10_000),
            safeTextArb,
            Arb.boolean(),
        ) { id, sourceId, title, inLibrary ->
            SuwayomiMangaDto(
                id = id,
                sourceId = sourceId.toString(),
                title = title,
                url = "/manga/$id",
                inLibrary = inLibrary,
            )
        }
    }
}
