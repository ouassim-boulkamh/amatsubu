package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.AllCategoriesQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.CreateCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchMangaAndChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetCategoryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryTrackingDataQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaCategoriesQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaTrackSummaryQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetNamedCategoryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetReadingHistoryIdsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetReadingHistoryQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetRecentChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetServerStatsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryOrderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangaCategoriesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangaLibraryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangasCategoriesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.CreateCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchMangaAndChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.IncludeOrExclude
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryOrderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaCategoriesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaCategoriesPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangasCategoriesInput

internal class SuwayomiLibraryGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
    private val snapshotCache: SuwayomiSnapshotCache?,
    private val serverKey: () -> String,
) {

    suspend fun getCategories(): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().query(AllCategoriesQuery()).execute()
        return response.data?.categories?.nodes?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no categories")
    }

    suspend fun createCategory(name: String, order: Int? = null): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            CreateCategoryMutation(CreateCategoryInput(name = name, order = Optional.presentIfNotNull(order))),
        ).execute()
        return response.data?.createCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryName(categoryId: Int, name: String): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMutation(
                UpdateCategoryInput(id = categoryId, patch = UpdateCategoryPatchInput(name = Optional.present(name))),
            ),
        ).execute()
        return response.data?.updateCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryFlags(
        categoryId: Int,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMutation(
                UpdateCategoryInput(
                    id = categoryId,
                    patch = UpdateCategoryPatchInput(
                        includeInUpdate = Optional.present(IncludeOrExclude.valueOf(includeInUpdate.name)),
                        includeInDownload = Optional.present(IncludeOrExclude.valueOf(includeInDownload.name)),
                    ),
                ),
            ),
        ).execute()
        return response.data?.updateCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryOrder(categoryId: Int, position: Int): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryOrderMutation(UpdateCategoryOrderInput(id = categoryId, position = position)),
        ).execute()
        return response.data?.updateCategoryOrder?.categories?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no categories")
    }

    suspend fun deleteCategory(categoryId: Int): SuwayomiCategoryDto? {
        val response = apolloClientFactory.create().mutation(
            DeleteCategoryMutation(DeleteCategoryInput(categoryId)),
        ).execute()
        return response.data?.deleteCategory?.category?.amatsubuCategory?.toSuwayomiDto()
    }

    suspend fun getCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> {
        val mangas = if (categoryId == 0) {
            getDefaultCategoryMangas()
        } else {
            getNamedCategoryMangas(categoryId)
        }.filterInLibraryMangas()
        snapshotCache?.storeCategoryMangas(
            serverKey = serverKey(),
            categoryId = categoryId,
            mangas = mangas,
            mirrorToLibrary = categoryId == 0,
        )
        return mangas
    }

    private suspend fun getNamedCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(
            GetNamedCategoryMangasQuery(categoryIds = listOf(categoryId), inLibrary = true),
        ).execute()
        return response.data?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category mangas")
    }

    private suspend fun getDefaultCategoryMangas(): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(GetCategoryMangasQuery(id = 0)).execute()
        return response.data?.category?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category mangas")
    }

    suspend fun getCategoryMangasSnapshot(categoryId: Int): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getCategoryMangas(serverKey(), categoryId)
    }

    suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().query(GetMangaCategoriesQuery(mangaId)).execute()
        return response.data?.manga?.categories?.nodes?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga categories")
    }

    suspend fun getLibraryMangas(): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(GetLibraryMangasQuery()).execute()
        val mangas =
            response.data?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }?.filterInLibraryMangas()
                ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library mangas")
        snapshotCache?.storeLibraryMangas(serverKey(), mangas)
        return mangas
    }

    suspend fun getLibraryMangasSnapshot(): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getLibraryMangas(serverKey())
    }

    suspend fun getLibraryChapters(): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().query(GetLibraryChaptersQuery()).execute()
        val chapters = response.data?.chapters?.nodes?.map { it.amatsubuLibraryChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library chapters")
        snapshotCache?.storeLibraryChapters(serverKey(), chapters)
        return chapters
    }

    suspend fun getLibraryTrackingData(): LibraryTrackingData {
        val response = apolloClientFactory.create().query(GetLibraryTrackingDataQuery()).execute()
        val data = response.data
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library tracking data")
        return LibraryTrackingData(
            libraryTrackingMangas = LibraryTrackingMangaNodeList(
                data.libraryTrackingMangas.nodes.map { manga ->
                    SuwayomiLibraryTrackingMangaDto(
                        id = manga.id,
                        trackRecords = StatsTrackRecordNodeList(
                            manga.trackRecords.nodes.map { SuwayomiStatsTrackRecordDto(it.trackerId, it.score) },
                        ),
                    )
                },
            ),
            trackers = TrackerNodeList(
                data.trackers.nodes.map { SuwayomiTrackerDto(id = it.id, name = it.name, isLoggedIn = it.isLoggedIn) },
            ),
        )
    }

    suspend fun getServerStats(): ServerStatsData {
        val response = apolloClientFactory.create().query(GetServerStatsQuery()).execute()
        val data =
            response.data
                ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no server stats")
        return data.toServerStatsData()
    }

    suspend fun updateMangaCategories(
        mangaId: Int,
        categoryIds: List<Int>,
    ): List<SuwayomiCategoryDto> {
        val currentCategoryIds = getMangaCategories(mangaId).map { it.id }.toSet()
        val targetCategoryIds = categoryIds.distinct()
        val addCategoryIds = targetCategoryIds.filterNot { it in currentCategoryIds }
        val removeCategoryIds = currentCategoryIds.filterNot { it in targetCategoryIds.toSet() }

        if (addCategoryIds.isEmpty() && removeCategoryIds.isEmpty()) {
            return getMangaCategories(mangaId)
        }

        val patch = if (targetCategoryIds.isEmpty()) {
            UpdateMangaCategoriesPatchInput(clearCategories = Optional.present(true))
        } else {
            UpdateMangaCategoriesPatchInput(
                addToCategories = Optional.presentIfNotNull(addCategoryIds.takeIf { it.isNotEmpty() }),
                removeFromCategories = Optional.presentIfNotNull(removeCategoryIds.takeIf { it.isNotEmpty() }),
            )
        }
        val response = apolloClientFactory.create().mutation(
            UpdateMangaCategoriesMutation(UpdateMangaCategoriesInput(id = mangaId, patch = patch)),
        ).execute()
        return response.data?.updateMangaCategories?.manga?.categories?.nodes
            ?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga categories")
    }

    suspend fun updateMangasCategories(
        mangaIds: List<Int>,
        addCategoryIds: List<Int> = emptyList(),
        removeCategoryIds: List<Int> = emptyList(),
        clearCategories: Boolean = false,
    ): List<SuwayomiMangaDto> {
        val ids = mangaIds.distinct()
        val addIds = addCategoryIds.distinct()
        val removeIds = removeCategoryIds.distinct()
        if (ids.isEmpty()) return emptyList()
        if (!clearCategories && addIds.isEmpty() && removeIds.isEmpty()) return emptyList()

        val patch = if (clearCategories) {
            UpdateMangaCategoriesPatchInput(clearCategories = Optional.present(true))
        } else {
            UpdateMangaCategoriesPatchInput(
                addToCategories = Optional.presentIfNotNull(addIds.takeIf { it.isNotEmpty() }),
                removeFromCategories = Optional.presentIfNotNull(removeIds.takeIf { it.isNotEmpty() }),
            )
        }
        val response = apolloClientFactory.create().mutation(
            UpdateMangasCategoriesMutation(UpdateMangasCategoriesInput(ids = ids, patch = patch)),
        ).execute()
        return response.data?.updateMangasCategories?.mangas?.map { it.amatsubuManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated mangas")
    }

    suspend fun getManga(mangaId: Int): SuwayomiMangaDto {
        val response = apolloClientFactory.create().query(GetMangaQuery(mangaId)).execute()
        val manga = response.data?.manga?.amatsubuManga?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga")
        snapshotCache?.storeManga(serverKey(), manga)
        return manga
    }

    suspend fun getMangaSnapshot(mangaId: Int): SuwayomiSnapshot<SuwayomiMangaDto>? {
        return snapshotCache?.getManga(serverKey(), mangaId)
    }

    suspend fun fetchMangaAndChaptersPartial(
        mangaId: Int,
        fetchManga: Boolean,
        fetchChapters: Boolean,
    ): GraphQlPartialResponse<FetchMangaAndChaptersPayload> {
        val response = apolloClientFactory.create().mutation(
            FetchMangaAndChaptersMutation(
                FetchMangaAndChaptersInput(
                    id = mangaId,
                    fetchManga = fetchManga,
                    fetchChapters = fetchChapters,
                ),
            ),
        ).execute()
        val payload = response.data?.fetchMangaAndChapters
        val errors = response.errors.orEmpty()
        if (payload == null && errors.isNotEmpty()) {
            error(errors.joinToString("; ") { it.message })
        }
        return GraphQlPartialResponse(
            data = payload?.let {
                FetchMangaAndChaptersPayload(
                    manga = it.manga.amatsubuManga.toSuwayomiDto(),
                    chapters = it.chapters.map { chapter -> chapter.amatsubuChapter.toSuwayomiDto() },
                )
            } ?: error("Suwayomi server returned no manga or chapters"),
            errors = errors.map { GraphQlError(it.message) },
        )
    }

    suspend fun getMangaTrackSummary(mangaId: Int): SuwayomiMangaTrackSummaryDto {
        val response = apolloClientFactory.create().query(GetMangaTrackSummaryQuery(mangaId)).execute()
        val manga = response.data?.manga
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga track summary")
        return SuwayomiMangaTrackSummaryDto(
            description = manga.description,
            id = manga.id,
            status = MangaStatus.valueOf(manga.status.rawValue),
            thumbnailUrl = manga.thumbnailUrl,
            title = manga.title,
            chapters = SuwayomiTrackSummaryChapterCountDto(manga.chapters.totalCount.toLong()),
            latestReadChapter = manga.latestReadChapter?.let {
                SuwayomiTrackSummaryLatestReadChapterDto(it.chapterNumber.toDouble())
            },
            unreadCount = manga.unreadCount.toLong(),
        )
    }

    suspend fun getRecentChapters(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val response = apolloClientFactory.create().query(GetRecentChaptersQuery(limit)).execute()
        return response.data?.chapters?.nodes?.map { it.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no recent chapters")
    }

    suspend fun getReadingHistory(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val response = apolloClientFactory.create().query(GetReadingHistoryQuery(limit)).execute()
        return response.data?.chapters?.nodes?.map { it.toSuwayomiDto() }
            ?.filter { it.isRead || it.lastPageRead > 0 }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reading history")
    }

    suspend fun getReadingHistoryChapterIds(pageSize: Int = 500): List<Int> {
        val ids = mutableListOf<Int>()
        var offset = 0
        do {
            val response = apolloClientFactory.create().query(GetReadingHistoryIdsQuery(pageSize, offset)).execute()
            val page = response.data?.chapters
                ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reading history ids")

            ids += page.nodes.map { it.id }
            offset += page.nodes.size
        } while (page.nodes.isNotEmpty() && offset < page.totalCount)

        return ids
    }

    suspend fun updateMangaLibrary(
        mangaId: Int,
        inLibrary: Boolean,
    ): SuwayomiMangaDto {
        val response = apolloClientFactory.create().mutation(
            UpdateMangaLibraryMutation(
                UpdateMangaInput(id = mangaId, patch = UpdateMangaPatchInput(inLibrary = Optional.present(inLibrary))),
            ),
        ).execute()
        return response.data?.updateManga?.manga?.amatsubuManga?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated manga")
    }
}
