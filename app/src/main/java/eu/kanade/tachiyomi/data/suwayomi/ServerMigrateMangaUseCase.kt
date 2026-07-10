package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.domain.migration.model.MigrationFlag
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.manga.model.Manga
import kotlin.coroutines.cancellation.CancellationException

internal class ServerMigrateMangaUseCase private constructor(
    private val migrationFlags: () -> Set<MigrationFlag>,
    private val client: ServerMigrateMangaClient,
    private val requestSharedRefresh: (Set<ServerStateEntity>) -> Unit,
) {
    constructor(
        sourcePreferences: SourcePreferences,
        provider: SuwayomiClientProvider,
    ) : this(
        migrationFlags = sourcePreferences.migrationFlags::get,
        client = DefaultServerMigrateMangaClient(provider),
        requestSharedRefresh = { affected -> ServerStateSync.requestRefresh(*affected.toTypedArray()) },
    )

    internal constructor(
        migrationFlags: () -> Set<MigrationFlag>,
        client: ServerMigrateMangaClient,
        requestSharedRefresh: (Set<ServerStateEntity>) -> Unit = { _ -> },
        @Suppress("UNUSED_PARAMETER") testConstructor: Unit = Unit,
    ) : this(migrationFlags, client, requestSharedRefresh)

    suspend fun isServerManga(mangaId: Long): Boolean {
        return runCatching { client.getManga(mangaId.toInt()) }.isSuccess
    }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        val currentId = current.id.toInt()
        val targetId = target.id.toInt()
        val flags = migrationFlags()
        var acceptedMutation = false
        val affectedEntities = migrationAffectedEntities(currentId, targetId)

        try {
            val currentServerManga = runMigrationStep(ServerMigrationStep.LOAD_CURRENT_MANGA) {
                client.getManga(currentId)
            }

            if (currentServerManga.inLibrary) {
                runMigrationMutation(ServerMigrationStep.ADD_TARGET_TO_LIBRARY) {
                    client.updateMangaLibrary(targetId, inLibrary = true)
                }
                acceptedMutation = true
            }

            if (MigrationFlag.CATEGORY in flags && currentServerManga.inLibrary) {
                val categoryIds = runMigrationStep(ServerMigrationStep.LOAD_SOURCE_CATEGORIES) {
                    client.getMangaCategories(currentId).map { it.id }
                }
                runMigrationMutation(ServerMigrationStep.COPY_CATEGORIES) {
                    client.updateMangaCategories(targetId, categoryIds)
                }
                acceptedMutation = true
            }

            if (MigrationFlag.CHAPTER in flags) {
                val migratedChapterState = migrateChapterState(currentId, targetId)
                acceptedMutation = acceptedMutation || migratedChapterState
            }

            if (MigrationFlag.NOTES in flags && current.notes.isNotBlank()) {
                runMigrationMutation(ServerMigrationStep.COPY_NOTES) {
                    client.setMangaMeta(targetId, SERVER_MANGA_NOTES_META_KEY, current.notes)
                }
                acceptedMutation = true
            }

            if (replace && currentServerManga.inLibrary) {
                runMigrationMutation(ServerMigrationStep.REMOVE_SOURCE_FROM_LIBRARY) {
                    client.updateMangaLibrary(currentId, inLibrary = false)
                }
                acceptedMutation = true
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val stepFailure = error as? ServerMigrationStepFailure
            val cause = stepFailure?.cause ?: error
            if (acceptedMutation) {
                requestSharedRefresh(affectedEntities)
                throw ServerMigrationPartialFailureException(
                    failedStep = stepFailure?.step,
                    cause = cause,
                )
            }
            throw cause
        }

        if (acceptedMutation) {
            requestSharedRefresh(affectedEntities)
        }
    }

    private suspend fun migrateChapterState(currentId: Int, targetId: Int): Boolean {
        var acceptedMutation = false
        val currentChapters = runMigrationStep(ServerMigrationStep.LOAD_SOURCE_CHAPTERS) {
            client.getChapters(currentId)
        }
        val targetChapters = runMigrationStep(ServerMigrationStep.LOAD_TARGET_CHAPTERS) {
            client.getChapters(targetId)
        }
        val targetByNumber = targetChapters
            .filter { it.chapterNumber >= 0f }
            .groupBy { it.chapterNumber }
        val targetByName = targetChapters.associateBy { it.name.normalizedChapterName() }

        currentChapters
            .filter { it.isRead || it.isBookmarked || it.lastPageRead > 0 }
            .forEach { currentChapter ->
                val targetChapter = targetByNumber[currentChapter.chapterNumber]?.firstOrNull()
                    ?: targetByName[currentChapter.name.normalizedChapterName()]
                    ?: return@forEach

                runMigrationMutation(ServerMigrationStep.COPY_CHAPTER_STATE) {
                    client.updateChapterMigrationState(
                        chapterId = targetChapter.id,
                        isRead = currentChapter.isRead || targetChapter.isRead,
                        isBookmarked = currentChapter.isBookmarked || targetChapter.isBookmarked,
                        lastPageRead = maxOf(currentChapter.lastPageRead, targetChapter.lastPageRead),
                    )
                }
                acceptedMutation = true
            }
        return acceptedMutation
    }
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

internal class ServerMigrationPartialFailureException(
    failedStep: ServerMigrationStep?,
    cause: Throwable,
) : IllegalStateException(
    buildString {
        append("Suwayomi accepted part of this migration")
        if (failedStep != null) {
            append(", but ")
            append(failedStep.label)
            append(" failed")
        } else {
            append(", but a later step failed")
        }
        append(". Retry migration to repair the remaining server state.")
    },
    cause,
)

internal enum class ServerMigrationStep(val label: String) {
    LOAD_CURRENT_MANGA("loading the source manga"),
    ADD_TARGET_TO_LIBRARY("adding the target manga to the library"),
    LOAD_SOURCE_CATEGORIES("loading source manga categories"),
    COPY_CATEGORIES("copying categories"),
    LOAD_SOURCE_CHAPTERS("loading source manga chapters"),
    LOAD_TARGET_CHAPTERS("loading target manga chapters"),
    COPY_CHAPTER_STATE("copying chapter state"),
    COPY_NOTES("copying notes"),
    REMOVE_SOURCE_FROM_LIBRARY("removing the source manga from the library"),
}

private class ServerMigrationStepFailure(
    val step: ServerMigrationStep,
    cause: Throwable,
) : RuntimeException(cause)

private suspend fun <T> runMigrationStep(
    step: ServerMigrationStep,
    block: suspend () -> T,
): T {
    return try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        throw ServerMigrationStepFailure(step, error)
    }
}

private suspend fun runMigrationMutation(
    step: ServerMigrationStep,
    block: suspend () -> Unit,
) {
    runMigrationStep(step, block)
}

internal interface ServerMigrateMangaClient {
    suspend fun getManga(mangaId: Int): SuwayomiMangaDto
    suspend fun updateMangaLibrary(mangaId: Int, inLibrary: Boolean)
    suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto>
    suspend fun updateMangaCategories(mangaId: Int, categoryIds: List<Int>)
    suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto>
    suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    )
    suspend fun setMangaMeta(mangaId: Int, key: String, value: String)
}

private class DefaultServerMigrateMangaClient(
    provider: SuwayomiClientProvider,
) : ServerMigrateMangaClient {
    private val client = provider.graphQlClient

    override suspend fun getManga(mangaId: Int): SuwayomiMangaDto {
        return client.getManga(mangaId)
    }

    override suspend fun updateMangaLibrary(mangaId: Int, inLibrary: Boolean) {
        client.updateMangaLibrary(mangaId, inLibrary)
    }

    override suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> {
        return client.getMangaCategories(mangaId)
    }

    override suspend fun updateMangaCategories(mangaId: Int, categoryIds: List<Int>) {
        client.updateMangaCategories(mangaId, categoryIds)
    }

    override suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> {
        return client.getChapters(mangaId)
    }

    override suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    ) {
        client.updateChapterMigrationState(
            chapterId = chapterId,
            isRead = isRead,
            isBookmarked = isBookmarked,
            lastPageRead = lastPageRead,
        )
    }

    override suspend fun setMangaMeta(mangaId: Int, key: String, value: String) {
        client.setMangaMeta(mangaId, key, value)
    }
}

private fun String.normalizedChapterName(): String {
    return trim().lowercase()
}
