package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.domain.migration.model.MigrationFlag
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import eu.kanade.domain.manga.model.Manga

class ServerMigrateMangaUseCaseTest {

    @Test
    fun `Batch G migration preserves library membership categories notes and chapter state`() = runTest {
        val client = FakeServerMigrateMangaClient(
            mangas = mapOf(
                10 to suwayomiManga(10, inLibrary = true),
                20 to suwayomiManga(20, inLibrary = false),
            ),
            categories = mapOf(10 to listOf(3, 4)),
            chapters = mapOf(
                10 to listOf(
                    suwayomiChapter(
                        id = 101,
                        mangaId = 10,
                        name = "Chapter 1",
                        chapterNumber = 1f,
                        isRead = true,
                        isBookmarked = false,
                        lastPageRead = 9,
                    ),
                    suwayomiChapter(
                        id = 102,
                        mangaId = 10,
                        name = "Extra",
                        chapterNumber = -1f,
                        isRead = false,
                        isBookmarked = true,
                        lastPageRead = 4,
                    ),
                    suwayomiChapter(
                        id = 103,
                        mangaId = 10,
                        name = "Untouched",
                        chapterNumber = 3f,
                    ),
                ),
                20 to listOf(
                    suwayomiChapter(
                        id = 201,
                        mangaId = 20,
                        name = "Chapter 1",
                        chapterNumber = 1f,
                        isRead = false,
                        isBookmarked = true,
                        lastPageRead = 2,
                    ),
                    suwayomiChapter(
                        id = 202,
                        mangaId = 20,
                        name = " extra ",
                        chapterNumber = -1f,
                    ),
                ),
            ),
        )
        val useCase = ServerMigrateMangaUseCase(
            migrationFlags = { setOf(MigrationFlag.CATEGORY, MigrationFlag.CHAPTER, MigrationFlag.NOTES) },
            client = client,
            requestSharedRefresh = client::recordSharedRefresh,
        )

        useCase(
            current = domainManga(10, notes = "Keep these notes"),
            target = domainManga(20),
            replace = true,
        )

        assertEquals(
            listOf(
                Operation.UpdateMangaLibrary(20, true),
                Operation.UpdateMangaCategories(20, listOf(3, 4)),
                Operation.UpdateChapterMigrationState(
                    chapterId = 201,
                    isRead = true,
                    isBookmarked = true,
                    lastPageRead = 9,
                ),
                Operation.UpdateChapterMigrationState(
                    chapterId = 202,
                    isRead = false,
                    isBookmarked = true,
                    lastPageRead = 4,
                ),
                Operation.SetMangaMeta(20, SERVER_MANGA_NOTES_META_KEY, "Keep these notes"),
                Operation.UpdateMangaLibrary(10, false),
            ),
            client.operations,
        )
        assertEquals(1, client.sharedRefreshes)
        assertEquals(listOf(migrationAffectedEntities(currentId = 10, targetId = 20)), client.sharedInvalidations)
    }

    @Test
    fun `Batch G migration copy does not remove source manga or carry tracker state implicitly`() = runTest {
        val client = FakeServerMigrateMangaClient(
            mangas = mapOf(
                10 to suwayomiManga(10, inLibrary = true),
                20 to suwayomiManga(20, inLibrary = false),
            ),
            categories = mapOf(10 to listOf(3)),
            chapters = emptyMap(),
        )
        val useCase = ServerMigrateMangaUseCase(
            migrationFlags = { setOf(MigrationFlag.CATEGORY) },
            client = client,
        )

        useCase(
            current = domainManga(10, notes = "Tracker carry-over remains a product decision"),
            target = domainManga(20),
            replace = false,
        )

        assertEquals(
            listOf(
                Operation.UpdateMangaLibrary(20, true),
                Operation.UpdateMangaCategories(20, listOf(3)),
            ),
            client.operations,
        )
    }

    @Test
    fun `migration reports partial failure and requests shared refresh after an accepted mutation`() = runTest {
        val failure = IllegalStateException("category update failed")
        val client = FakeServerMigrateMangaClient(
            mangas = mapOf(
                10 to suwayomiManga(10, inLibrary = true),
                20 to suwayomiManga(20, inLibrary = false),
            ),
            categories = mapOf(10 to listOf(3)),
            chapters = emptyMap(),
            failures = mutableMapOf(Operation.UpdateMangaCategories(20, listOf(3)) to failure),
        )
        val useCase = ServerMigrateMangaUseCase(
            migrationFlags = { setOf(MigrationFlag.CATEGORY) },
            client = client,
            requestSharedRefresh = client::recordSharedRefresh,
        )

        val thrown = assertInstanceOf(
            ServerMigrationPartialFailureException::class.java,
            runCatching {
                useCase(
                    current = domainManga(10),
                    target = domainManga(20),
                    replace = false,
                )
            }.exceptionOrNull(),
        )

        assertEquals(failure, thrown.cause)
        assertEquals(
            listOf(
                Operation.UpdateMangaLibrary(20, true),
                Operation.UpdateMangaCategories(20, listOf(3)),
            ),
            client.operations,
        )
        assertEquals(1, client.sharedRefreshes)
        assertEquals(listOf(migrationAffectedEntities(currentId = 10, targetId = 20)), client.sharedInvalidations)
    }

    @Test
    fun `retrying after partial migration replays idempotent repair operations`() = runTest {
        val failure = IllegalStateException("category update failed")
        val failingClient = FakeServerMigrateMangaClient(
            mangas = mapOf(
                10 to suwayomiManga(10, inLibrary = true),
                20 to suwayomiManga(20, inLibrary = false),
            ),
            categories = mapOf(10 to listOf(3)),
            chapters = emptyMap(),
            failures = mutableMapOf(Operation.UpdateMangaCategories(20, listOf(3)) to failure),
        )
        val repairingClient = FakeServerMigrateMangaClient(
            mangas = mapOf(
                10 to suwayomiManga(10, inLibrary = true),
                20 to suwayomiManga(20, inLibrary = true),
            ),
            categories = mapOf(10 to listOf(3)),
            chapters = emptyMap(),
        )

        val failingUseCase = ServerMigrateMangaUseCase(
            migrationFlags = { setOf(MigrationFlag.CATEGORY) },
            client = failingClient,
        )
        val repairingUseCase = ServerMigrateMangaUseCase(
            migrationFlags = { setOf(MigrationFlag.CATEGORY) },
            client = repairingClient,
            requestSharedRefresh = repairingClient::recordSharedRefresh,
        )

        runCatching {
            failingUseCase(
                current = domainManga(10),
                target = domainManga(20),
                replace = false,
            )
        }

        repairingUseCase(
            current = domainManga(10),
            target = domainManga(20),
            replace = false,
        )

        assertEquals(
            listOf(
                Operation.UpdateMangaLibrary(20, true),
                Operation.UpdateMangaCategories(20, listOf(3)),
            ),
            repairingClient.operations,
        )
        assertEquals(1, repairingClient.sharedRefreshes)
        assertEquals(listOf(migrationAffectedEntities(currentId = 10, targetId = 20)), repairingClient.sharedInvalidations)
    }

    private class FakeServerMigrateMangaClient(
        private val mangas: Map<Int, SuwayomiMangaDto>,
        private val categories: Map<Int, List<Int>>,
        private val chapters: Map<Int, List<SuwayomiChapterDto>>,
        private val failures: MutableMap<Operation, Throwable> = mutableMapOf(),
    ) : ServerMigrateMangaClient {
        val operations = mutableListOf<Operation>()
        val sharedInvalidations = mutableListOf<Set<ServerStateEntity>>()
        var sharedRefreshes = 0

        override suspend fun getManga(mangaId: Int): SuwayomiMangaDto {
            return mangas.getValue(mangaId)
        }

        override suspend fun updateMangaLibrary(mangaId: Int, inLibrary: Boolean) {
            record(Operation.UpdateMangaLibrary(mangaId, inLibrary))
        }

        override suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> {
            return categories[mangaId].orEmpty().map { id ->
                SuwayomiCategoryDto(id = id, name = "Category $id")
            }
        }

        override suspend fun updateMangaCategories(mangaId: Int, categoryIds: List<Int>) {
            record(Operation.UpdateMangaCategories(mangaId, categoryIds))
        }

        override suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> {
            return chapters[mangaId].orEmpty()
        }

        override suspend fun updateChapterMigrationState(
            chapterId: Int,
            isRead: Boolean,
            isBookmarked: Boolean,
            lastPageRead: Int,
        ) {
            record(
                Operation.UpdateChapterMigrationState(
                    chapterId = chapterId,
                    isRead = isRead,
                    isBookmarked = isBookmarked,
                    lastPageRead = lastPageRead,
                ),
            )
        }

        override suspend fun setMangaMeta(mangaId: Int, key: String, value: String) {
            record(Operation.SetMangaMeta(mangaId, key, value))
        }

        private fun record(operation: Operation) {
            operations += operation
            failures.remove(operation)?.let { throw it }
        }

        fun recordSharedRefresh(affected: Set<ServerStateEntity>) {
            sharedRefreshes += 1
            sharedInvalidations += affected
        }
    }

    private sealed interface Operation {
        data class UpdateMangaLibrary(val mangaId: Int, val inLibrary: Boolean) : Operation
        data class UpdateMangaCategories(val mangaId: Int, val categoryIds: List<Int>) : Operation
        data class UpdateChapterMigrationState(
            val chapterId: Int,
            val isRead: Boolean,
            val isBookmarked: Boolean,
            val lastPageRead: Int,
        ) : Operation
        data class SetMangaMeta(val mangaId: Int, val key: String, val value: String) : Operation
    }

    private fun suwayomiManga(id: Int, inLibrary: Boolean): SuwayomiMangaDto {
        return SuwayomiMangaDto(
            id = id,
            inLibrary = inLibrary,
            sourceId = "1",
            title = "Manga $id",
            url = "/manga/$id",
        )
    }

    private fun suwayomiChapter(
        id: Int,
        mangaId: Int,
        name: String,
        chapterNumber: Float,
        isRead: Boolean = false,
        isBookmarked: Boolean = false,
        lastPageRead: Int = 0,
    ): SuwayomiChapterDto {
        return SuwayomiChapterDto(
            id = id,
            mangaId = mangaId,
            name = name,
            chapterNumber = chapterNumber,
            isRead = isRead,
            isBookmarked = isBookmarked,
            lastPageRead = lastPageRead,
            url = "/chapter/$id",
        )
    }

    private fun domainManga(id: Long, notes: String = ""): Manga {
        return Manga(
            id = id,
            source = 1,
            favorite = true,
            lastUpdate = 0,
            nextUpdate = 0,
            fetchInterval = 0,
            dateAdded = 0,
            viewerFlags = 0,
            chapterFlags = 0,
            coverLastModified = 0,
            url = "/manga/$id",
            title = "Manga $id",
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0,
            thumbnailUrl = null,
            updateStrategy = eu.kanade.domain.manga.model.UpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
            lastModifiedAt = 0,
            favoriteModifiedAt = null,
            version = 0,
            notes = notes,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun migrationAffectedEntities(currentId: Int, targetId: Int): Set<ServerStateEntity> {
        return setOf(
            ServerStateEntity.Library,
            ServerStateEntity.Categories,
            ServerStateEntity.Manga(currentId),
            ServerStateEntity.Manga(targetId),
            ServerStateEntity.Chapters(currentId),
            ServerStateEntity.Chapters(targetId),
            ServerStateEntity.Trackers(targetId),
        )
    }
}
