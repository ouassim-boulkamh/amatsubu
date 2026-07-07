package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.browse.migration.SERVER_MIGRATION_NOTES_META_KEY
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class ServerMigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val provider: SuwayomiClientProvider = SuwayomiClientProvider(),
) {
    suspend fun isServerManga(mangaId: Long): Boolean {
        return runCatching { provider.graphQlClient.getManga(mangaId.toInt()) }.isSuccess
    }

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        val client = provider.graphQlClient
        val currentId = current.id.toInt()
        val targetId = target.id.toInt()
        val flags = sourcePreferences.migrationFlags.get()
        val currentServerManga = client.getManga(currentId)

        if (currentServerManga.inLibrary) {
            client.updateMangaLibrary(targetId, inLibrary = true)
        }

        if (MigrationFlag.CATEGORY in flags && currentServerManga.inLibrary) {
            val categoryIds = client.getMangaCategories(currentId).map { it.id }
            client.updateMangaCategories(targetId, categoryIds)
        }

        if (MigrationFlag.CHAPTER in flags) {
            migrateChapterState(currentId, targetId)
        }

        if (MigrationFlag.NOTES in flags && current.notes.isNotBlank()) {
            client.setMangaMeta(targetId, SERVER_MIGRATION_NOTES_META_KEY, current.notes)
        }

        if (replace && currentServerManga.inLibrary) {
            client.updateMangaLibrary(currentId, inLibrary = false)
        }
    }

    private suspend fun migrateChapterState(currentId: Int, targetId: Int) {
        val client = provider.graphQlClient
        val currentChapters = client.getChapters(currentId)
        val targetChapters = client.getChapters(targetId)
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

                client.updateChapterMigrationState(
                    chapterId = targetChapter.id,
                    isRead = currentChapter.isRead || targetChapter.isRead,
                    isBookmarked = currentChapter.isBookmarked || targetChapter.isBookmarked,
                    lastPageRead = maxOf(currentChapter.lastPageRead, targetChapter.lastPageRead),
                )
            }
    }
}

private fun String.normalizedChapterName(): String {
    return trim().lowercase()
}
