package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.data.Database

internal data class SuwayomiSnapshot<T>(
    val value: T,
    val syncedAt: Long,
)

internal class SuwayomiSnapshotCache(
    private val database: Database,
    private val json: Json,
) {
    suspend fun storeLibraryMangas(serverKey: String, mangas: List<SuwayomiMangaDto>) {
        val syncedAt = now()
        val libraryMangas = mangas.filterInLibraryMangas()
        database.transaction {
            database.suwayomi_snapshotsQueries.clearLibraryMangas(serverKey)
            libraryMangas.forEach { manga ->
                upsertManga(serverKey, manga, syncedAt)
                database.suwayomi_snapshotsQueries.insertLibraryManga(
                    serverKey = serverKey,
                    mangaId = manga.id.toLong(),
                    syncedAt = syncedAt,
                )
            }
        }
    }

    suspend fun getLibraryMangas(serverKey: String): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        val rows = database.suwayomi_snapshotsQueries
            .getLibraryMangaSnapshots(serverKey)
            .awaitAsList()
        if (rows.isEmpty()) return null
        return SuwayomiSnapshot(
            value = rows
                .map { row -> json.decodeFromString<SuwayomiMangaDto>(row.payload) }
                .filterInLibraryMangas(),
            syncedAt = rows.minOf { it.synced_at },
        )
    }

    suspend fun storeCategoryMangas(
        serverKey: String,
        categoryId: Int,
        mangas: List<SuwayomiMangaDto>,
        mirrorToLibrary: Boolean = false,
    ) {
        val syncedAt = now()
        val libraryMangas = mangas.filterInLibraryMangas()
        database.transaction {
            database.suwayomi_snapshotsQueries.clearCategoryMangas(serverKey, categoryId.toLong())
            if (mirrorToLibrary) {
                database.suwayomi_snapshotsQueries.clearLibraryMangas(serverKey)
            }
            libraryMangas.forEach { manga ->
                upsertManga(serverKey, manga, syncedAt)
                database.suwayomi_snapshotsQueries.insertCategoryManga(
                    serverKey = serverKey,
                    categoryId = categoryId.toLong(),
                    mangaId = manga.id.toLong(),
                    syncedAt = syncedAt,
                )
                if (mirrorToLibrary) {
                    database.suwayomi_snapshotsQueries.insertLibraryManga(
                        serverKey = serverKey,
                        mangaId = manga.id.toLong(),
                        syncedAt = syncedAt,
                    )
                }
            }
        }
    }

    suspend fun getCategoryMangas(serverKey: String, categoryId: Int): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        val rows = database.suwayomi_snapshotsQueries
            .getCategoryMangaSnapshots(serverKey, categoryId.toLong())
            .awaitAsList()
        if (rows.isEmpty()) return null
        return SuwayomiSnapshot(
            value = rows
                .map { row -> json.decodeFromString<SuwayomiMangaDto>(row.payload) }
                .filterInLibraryMangas(),
            syncedAt = rows.minOf { it.synced_at },
        )
    }

    suspend fun storeManga(serverKey: String, manga: SuwayomiMangaDto) {
        upsertManga(serverKey, manga, now())
    }

    suspend fun getManga(serverKey: String, mangaId: Int): SuwayomiSnapshot<SuwayomiMangaDto>? {
        return database.suwayomi_snapshotsQueries
            .getMangaSnapshot(serverKey, mangaId.toLong())
            .awaitAsOneOrNull()
            ?.let { row ->
                SuwayomiSnapshot(
                    value = json.decodeFromString<SuwayomiMangaDto>(row.payload),
                    syncedAt = row.synced_at,
                )
            }
    }

    suspend fun storeChapters(serverKey: String, mangaId: Int, chapters: List<SuwayomiChapterDto>) {
        val syncedAt = now()
        database.transaction {
            database.suwayomi_snapshotsQueries.clearChapters(serverKey, mangaId.toLong())
            insertChapters(serverKey, mangaId, chapters, syncedAt)
        }
    }

    suspend fun storeLibraryChapters(serverKey: String, chapters: List<SuwayomiChapterDto>) {
        val syncedAt = now()
        val chaptersByMangaId = chapters.groupBy { it.mangaId }
        database.transaction {
            chaptersByMangaId.forEach { (mangaId, mangaChapters) ->
                database.suwayomi_snapshotsQueries.clearChapters(serverKey, mangaId.toLong())
                insertChapters(serverKey, mangaId, mangaChapters, syncedAt)
            }
        }
    }

    suspend fun getChapters(serverKey: String, mangaId: Int): SuwayomiSnapshot<List<SuwayomiChapterDto>>? {
        val rows = database.suwayomi_snapshotsQueries
            .getChapterSnapshots(serverKey, mangaId.toLong())
            .awaitAsList()
        if (rows.isEmpty()) return null
        return SuwayomiSnapshot(
            value = rows.map { row -> json.decodeFromString<SuwayomiChapterDto>(row.payload) },
            syncedAt = rows.minOf { it.synced_at },
        )
    }

    suspend fun storeDownloadStatus(serverKey: String, status: SuwayomiDownloadStatusDto) {
        database.suwayomi_snapshotsQueries.upsertDownloadStatus(
            serverKey = serverKey,
            payload = json.encodeToString(status),
            syncedAt = now(),
        )
    }

    suspend fun getDownloadStatus(serverKey: String): SuwayomiSnapshot<SuwayomiDownloadStatusDto>? {
        return database.suwayomi_snapshotsQueries
            .getDownloadStatusSnapshot(serverKey)
            .awaitAsOneOrNull()
            ?.let { row ->
                SuwayomiSnapshot(
                    value = json.decodeFromString<SuwayomiDownloadStatusDto>(row.payload),
                    syncedAt = row.synced_at,
                )
            }
    }

    private suspend fun upsertManga(serverKey: String, manga: SuwayomiMangaDto, syncedAt: Long) {
        database.suwayomi_snapshotsQueries.upsertManga(
            serverKey = serverKey,
            mangaId = manga.id.toLong(),
            payload = json.encodeToString(manga),
            syncedAt = syncedAt,
        )
    }

    private suspend fun insertChapters(
        serverKey: String,
        mangaId: Int,
        chapters: List<SuwayomiChapterDto>,
        syncedAt: Long,
    ) {
        chapters.forEach { chapter ->
            database.suwayomi_snapshotsQueries.insertChapter(
                serverKey = serverKey,
                mangaId = mangaId.toLong(),
                chapterId = chapter.id.toLong(),
                payload = json.encodeToString(chapter),
                syncedAt = syncedAt,
            )
        }
    }

    private fun now(): Long = System.currentTimeMillis()
}
