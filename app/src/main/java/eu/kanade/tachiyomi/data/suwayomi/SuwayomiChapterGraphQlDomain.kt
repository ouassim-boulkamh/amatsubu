package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchChapterPagesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetCachedChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateReaderChapterMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateReaderChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchChapterPagesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChapterInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChapterPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChaptersInput

internal class SuwayomiChapterGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
    private val snapshotCache: SuwayomiSnapshotCache?,
    private val serverKey: () -> String,
) {

    suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val chapters = runCatching {
            apolloClientFactory.create()
                .mutation(FetchChaptersMutation(FetchChaptersInput(mangaId = mangaId)))
                .execute()
                .data
                ?.fetchChapters
                ?.chapters
                ?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
                .orEmpty()
        }.getOrElse { error ->
            if (error.message?.contains("No chapters found", ignoreCase = true) == true) {
                emptyList()
            } else {
                throw error
            }
        }
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getCachedChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().query(GetCachedChaptersQuery(mangaId)).execute()
        val chapters = response.data?.chapters?.nodes?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no cached chapters")
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getChaptersSnapshot(mangaId: Int): SuwayomiSnapshot<List<SuwayomiChapterDto>>? {
        return snapshotCache?.getChapters(serverKey(), mangaId)
    }

    suspend fun getChapterPages(chapterId: Int): List<String> {
        val response = apolloClientFactory.create()
            .mutation(FetchChapterPagesMutation(FetchChapterPagesInput(chapterId = chapterId)))
            .execute()
        return response.data?.fetchChapterPages?.pages
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no chapter pages")
    }

    suspend fun getChapterPageManifest(chapterId: Int): SuwayomiChapterPageManifest {
        val response = apolloClientFactory.create()
            .mutation(FetchChapterPagesMutation(FetchChapterPagesInput(chapterId = chapterId)))
            .execute()
        return response.data?.fetchChapterPages
            ?.let { payload ->
                SuwayomiChapterPageManifest(
                    pages = payload.pages,
                    chapter = payload.chapter?.amatsubuReaderChapter?.toSuwayomiDto()
                        ?: error("Suwayomi server returned no manifest chapter"),
                )
            }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no chapter page manifest")
    }

    suspend fun updateChapterProgress(
        chapterId: Int,
        isRead: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(isRead = Optional.present(isRead), lastPageRead = Optional.present(lastPageRead)),
        )
    }

    suspend fun updateChapterRead(
        chapterId: Int,
        isRead: Boolean,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(
                isRead = Optional.present(isRead),
                lastPageRead = Optional.presentIfNotNull(0.takeIf { !isRead }),
            ),
        )
    }

    suspend fun updateChapterBookmark(
        chapterId: Int,
        isBookmarked: Boolean,
    ): SuwayomiChapterDto {
        return updateReaderChapter(chapterId, UpdateChapterPatchInput(isBookmarked = Optional.present(isBookmarked)))
    }

    suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(
                isRead = Optional.present(isRead),
                isBookmarked = Optional.present(isBookmarked),
                lastPageRead = Optional.present(lastPageRead),
            ),
        )
    }

    suspend fun updateChaptersRead(
        chapterIds: List<Int>,
        isRead: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isRead = isRead,
            lastPageRead = 0.takeIf { !isRead },
        )
    }

    suspend fun updateChaptersBookmark(
        chapterIds: List<Int>,
        isBookmarked: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isBookmarked = isBookmarked,
        )
    }

    private suspend fun updateChapters(
        chapterIds: List<Int>,
        isRead: Boolean? = null,
        isBookmarked: Boolean? = null,
        lastPageRead: Int? = null,
    ): List<SuwayomiChapterDto> {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return emptyList()

        val response = apolloClientFactory.create().mutation(
            UpdateReaderChaptersMutation(
                UpdateChaptersInput(
                    ids = ids,
                    patch = UpdateChapterPatchInput(
                        isRead = Optional.presentIfNotNull(isRead),
                        isBookmarked = Optional.presentIfNotNull(isBookmarked),
                        lastPageRead = Optional.presentIfNotNull(lastPageRead),
                    ),
                ),
            ),
        ).execute()
        return response.data?.updateChapters?.chapters?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated chapters")
    }

    private suspend fun updateReaderChapter(
        chapterId: Int,
        patch: UpdateChapterPatchInput,
    ): SuwayomiChapterDto {
        val response = apolloClientFactory.create().mutation(
            UpdateReaderChapterMutation(UpdateChapterInput(id = chapterId, patch = patch)),
        ).execute()
        return response.data?.updateChapter?.chapter?.amatsubuReaderChapter?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated chapter")
    }
}
