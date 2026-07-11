package eu.kanade.tachiyomi.ui.library

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.junit.jupiter.api.Test

class ServerLibraryMembershipPropertyTest {

    @Test
    suspend fun `generated duplicate library rows collapse by manga id before grouping`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(libraryMangaArb, 0..24)) { favorites ->
            val collapsed = favorites.collapseServerLibraryMangaById()
            val expectedIds = favorites.associateBy { it.id }.values.map { it.id }

            collapsed.map { it.id } shouldContainExactly expectedIds
            collapsed.map { it.categories } shouldContainExactly favorites
                .associateBy { it.id }
                .values
                .map { it.categories }
        }
    }

    @Test
    suspend fun `generated category grouping preserves server category identity and visibility`() {
        checkAll(FuzzTestConfig.caseCount(), groupingScenarioArb) { scenario ->
            val grouped = groupServerLibraryMangaByCategory(
                favorites = scenario.favorites,
                categories = scenario.categories,
                showSystemCategory = scenario.showSystemCategory,
            )
            val collapsedFavorites = scenario.favorites.collapseServerLibraryMangaById()
            val expectedCategories = scenario.categories.filter {
                scenario.showSystemCategory || !it.isSystemCategory
            }
            val expected = expectedCategories.associateWith { category ->
                collapsedFavorites
                    .filter { category.id in it.categories }
                    .map { it.id }
            }

            grouped shouldContainExactly expected
            grouped.keys.map { it.id } shouldContainExactly expectedCategories.map { it.id }
        }
    }

    @Test
    suspend fun `generated chapter aggregates preserve bounded counts after scanlator exclusions`() {
        checkAll(FuzzTestConfig.caseCount(), chapterAggregateScenarioArb) { scenario ->
            val aggregate = scenario.chapters.toLibraryChapterAggregate(scenario.excludedScanlators)
            val included = scenario.chapters.filter { it.scanlator !in scenario.excludedScanlators }

            aggregate.totalChapters shouldBe included.size.toLong()
            aggregate.readCount shouldBe included.count { it.isRead }.toLong()
            aggregate.unreadCount shouldBe included.count { !it.isRead }.toLong()
            aggregate.bookmarkCount shouldBe included.count { it.isBookmarked }.toLong()
            aggregate.downloadCount shouldBe included.count { it.isDownloaded }
            aggregate.latestUpload shouldBe (included.maxOfOrNull { it.uploadDate } ?: 0L)
        }
    }

    private companion object {
        val safeTextArb = Arb.string(1..24).map { value ->
            value
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                .ifBlank { "value" }
        }

        val categoryIdArb = Arb.int(0..8).map { it.toLong() }

        val categoryArb: Arb<Category> = Arb.bind(
            categoryIdArb,
            safeTextArb,
            Arb.int(0..16),
        ) { id, name, order ->
            Category(
                id = id,
                name = name,
                order = order.toLong(),
                flags = 0,
            )
        }

        val libraryMangaArb: Arb<LibraryManga> = Arb.bind(
            Arb.int(1..16),
            safeTextArb,
            Arb.set(categoryIdArb, 0..4),
        ) { id, title, categories ->
            LibraryManga(
                manga = Manga.create().copy(
                    id = id.toLong(),
                    title = title,
                    favorite = true,
                    source = 1,
                ),
                categories = categories.toList(),
                totalChapters = 0,
                readCount = 0,
                bookmarkCount = 0,
                latestUpload = 0,
                chapterFetchedAt = 0,
                lastRead = 0,
                unreadCount = 0,
            )
        }

        val groupingScenarioArb: Arb<GroupingScenario> = Arb.bind(
            Arb.list(categoryArb, 1..8),
            Arb.list(libraryMangaArb, 0..24),
            Arb.boolean(),
        ) { categories, favorites, showSystemCategory ->
            GroupingScenario(
                categories = categories.distinctBy { it.id },
                favorites = favorites,
                showSystemCategory = showSystemCategory,
            )
        }

        val scanlatorArb = Arb.int(0..4).map { index ->
            when (index) {
                0 -> null
                else -> "group-$index"
            }
        }

        val chapterArb = Arb.bind(
            Arb.int(1..100_000),
            Arb.boolean(),
            Arb.boolean(),
            Arb.boolean(),
            scanlatorArb,
            Arb.int(0..100_000),
        ) { id, read, downloaded, bookmarked, scanlator, uploadDate ->
            eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto(
                id = id,
                mangaId = 1,
                name = "Chapter $id",
                url = "/chapter/$id",
                isRead = read,
                isDownloaded = downloaded,
                isBookmarked = bookmarked,
                scanlator = scanlator,
                uploadDate = uploadDate.toLong(),
                fetchedAt = uploadDate.toString(),
            )
        }

        val chapterAggregateScenarioArb: Arb<ChapterAggregateScenario> = Arb.bind(
            Arb.list(chapterArb, 0..24),
            Arb.set(Arb.int(1..4).map { "group-$it" }, 0..4),
        ) { chapters, excludedScanlators ->
            ChapterAggregateScenario(chapters, excludedScanlators)
        }
    }

    private data class GroupingScenario(
        val categories: List<Category>,
        val favorites: List<LibraryManga>,
        val showSystemCategory: Boolean,
    )

    private data class ChapterAggregateScenario(
        val chapters: List<eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto>,
        val excludedScanlators: Set<String>,
    )
}
