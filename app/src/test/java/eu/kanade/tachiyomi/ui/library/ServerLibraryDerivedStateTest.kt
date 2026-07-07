package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLatestFetchedChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLatestUploadedChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaMetaDto
import eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class ServerLibraryDerivedStateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chapter aggregate excludes saved scanlator filters from badges and filters`() {
        val chapters = listOf(
            chapter(id = 1, read = false, downloaded = true, bookmarked = true, scanlator = "kept", uploadDate = 10),
            chapter(id = 2, read = false, downloaded = true, bookmarked = true, scanlator = "hidden", uploadDate = 20),
            chapter(id = 3, read = true, downloaded = false, bookmarked = false, scanlator = null, uploadDate = 30),
        )

        val aggregate = chapters.toLibraryChapterAggregate(excludedScanlators = setOf("hidden"))

        assertEquals(2L, aggregate.totalChapters)
        assertEquals(1L, aggregate.readCount)
        assertEquals(1L, aggregate.unreadCount)
        assertEquals(1L, aggregate.bookmarkCount)
        assertEquals(1, aggregate.downloadCount)
        assertEquals(30L, aggregate.latestUpload)
    }

    @Test
    fun `device copy count includes only local device copies`() {
        val chapters = listOf(
            chapter(id = 1, read = false, downloaded = false, bookmarked = false, scanlator = "kept", uploadDate = 10),
            chapter(id = 2, read = false, downloaded = false, bookmarked = false, scanlator = "kept", uploadDate = 20),
        )

        val count = chapters.deviceCopyCount(
            excludedScanlators = emptySet(),
            deviceCopyChapterIds = setOf(2),
        )

        assertEquals(1, count)
    }

    @Test
    fun `device copy count stays separate from server downloaded chapters`() {
        val chapters = listOf(
            chapter(id = 1, read = false, downloaded = true, bookmarked = false, scanlator = "kept", uploadDate = 10),
            chapter(id = 2, read = false, downloaded = false, bookmarked = false, scanlator = "kept", uploadDate = 20),
        )

        val count = chapters.deviceCopyCount(
            excludedScanlators = emptySet(),
            deviceCopyChapterIds = setOf(1, 2),
        )

        assertEquals(2, count)
    }

    @Test
    fun `device copy count excludes hidden scanlators`() {
        val chapters = listOf(
            chapter(id = 1, read = false, downloaded = false, bookmarked = false, scanlator = "kept", uploadDate = 10),
            chapter(
                id = 2,
                read = false,
                downloaded = false,
                bookmarked = false,
                scanlator = "hidden",
                uploadDate = 20,
            ),
        )

        val count = chapters.deviceCopyCount(
            excludedScanlators = setOf("hidden"),
            deviceCopyChapterIds = setOf(1, 2),
        )

        assertEquals(1, count)
    }

    @Test
    fun `manga meta decodes excluded scanlator list tolerantly`() {
        val manga = manga(
            meta = listOf(
                SuwayomiMangaMetaDto(
                    key = SERVER_EXCLUDED_SCANLATORS_META_KEY,
                    mangaId = 1,
                    value = """["hidden","other"]""",
                ),
            ),
        )

        assertEquals(setOf("hidden", "other"), manga.serverExcludedScanlators(json))
    }

    @Test
    fun `invalid excluded scanlator meta falls back to no exclusions`() {
        val manga = manga(
            meta = listOf(
                SuwayomiMangaMetaDto(
                    key = SERVER_EXCLUDED_SCANLATORS_META_KEY,
                    mangaId = 1,
                    value = "not-json",
                ),
            ),
        )

        assertEquals(emptySet<String>(), manga.serverExcludedScanlators(json))
    }

    @Test
    fun `fallback aggregate preserves server row counts when chapter rows are unavailable`() {
        val manga = manga(
            unreadCount = 7,
            downloadCount = 3,
            latestFetchedAt = 12,
            latestUploadedAt = 45,
        )

        val aggregate = manga.toFallbackLibraryChapterAggregate()

        assertEquals(0L, aggregate.totalChapters)
        assertEquals(7L, aggregate.unreadCount)
        assertEquals(3, aggregate.downloadCount)
        assertEquals(45L, aggregate.latestUpload)
        assertEquals(12_000L, aggregate.chapterFetchedAt)
    }

    @Test
    fun `Sorayomi issue 323 grouping uses server category ids instead of tab positions`() {
        val categories = listOf(
            category(id = 0, name = "Default", order = 0),
            category(id = 20, name = "Second", order = 1),
            category(id = 10, name = "First", order = 2),
        )
        val favorites = listOf(
            libraryManga(id = 1, categories = listOf(10)),
            libraryManga(id = 2, categories = listOf(20)),
            libraryManga(id = 3, categories = listOf(10, 20)),
        )

        val grouped = groupServerLibraryMangaByCategory(
            favorites = favorites,
            categories = categories,
            showSystemCategory = false,
        )

        assertEquals(listOf(2L, 3L), grouped.getValue(categories[1]))
        assertEquals(listOf(1L, 3L), grouped.getValue(categories[2]))
        assertEquals(listOf("Second", "First"), grouped.keys.map { it.name })
    }

    @Test
    fun `Sorayomi issue 323 default category stays hidden unless visible library rows use id zero`() {
        val categories = listOf(
            category(id = 0, name = "Default", order = 0),
            category(id = 5, name = "Reading", order = 1),
        )
        val favorites = listOf(
            libraryManga(id = 1, categories = listOf(5)),
        )

        val grouped = groupServerLibraryMangaByCategory(
            favorites = favorites,
            categories = categories,
            showSystemCategory = favorites.any { 0L in it.categories },
        )

        assertEquals(listOf("Reading"), grouped.keys.map { it.name })
        assertEquals(listOf(1L), grouped.getValue(categories[1]))
    }

    @Test
    fun `category counts derive from visible in-library rows only`() {
        val categories = listOf(category(id = 7, name = "Kept", order = 0))
        val favorites = listOf(libraryManga(id = 1, categories = listOf(7)))

        val grouped = groupServerLibraryMangaByCategory(
            favorites = favorites,
            categories = categories,
            showSystemCategory = false,
        )

        assertEquals(1, grouped.getValue(categories.single()).size)
    }

    private fun chapter(
        id: Int,
        read: Boolean,
        downloaded: Boolean,
        bookmarked: Boolean,
        scanlator: String?,
        uploadDate: Long,
    ): SuwayomiChapterDto {
        return SuwayomiChapterDto(
            id = id,
            mangaId = 1,
            name = "Chapter $id",
            url = "/chapter/$id",
            isRead = read,
            isDownloaded = downloaded,
            isBookmarked = bookmarked,
            scanlator = scanlator,
            uploadDate = uploadDate,
            fetchedAt = uploadDate.toString(),
        )
    }

    private fun manga(
        unreadCount: Long = 0,
        downloadCount: Int = 0,
        latestFetchedAt: Long = 0,
        latestUploadedAt: Long = 0,
        meta: List<SuwayomiMangaMetaDto> = emptyList(),
    ): SuwayomiMangaDto {
        return SuwayomiMangaDto(
            id = 1,
            sourceId = "1",
            title = "Manga",
            url = "/manga/1",
            status = MangaStatus.UNKNOWN,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            unreadCount = unreadCount,
            downloadCount = downloadCount,
            latestFetchedChapter = SuwayomiLatestFetchedChapterDto(fetchedAt = latestFetchedAt),
            latestUploadedChapter = SuwayomiLatestUploadedChapterDto(uploadDate = latestUploadedAt),
            meta = meta,
        )
    }

    private fun category(
        id: Long,
        name: String,
        order: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = 0,
        )
    }

    private fun libraryManga(
        id: Long,
        categories: List<Long>,
    ): LibraryManga {
        return LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = "Manga $id",
                favorite = true,
                source = 1,
            ),
            categories = categories,
            totalChapters = 0,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
            unreadCount = 0,
        )
    }
}
