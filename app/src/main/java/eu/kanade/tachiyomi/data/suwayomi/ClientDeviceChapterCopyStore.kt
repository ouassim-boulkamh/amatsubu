package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import tachiyomi.data.Client_chapter_copies
import tachiyomi.data.Client_chapter_copy_pages
import tachiyomi.data.Database
import java.security.MessageDigest

enum class ClientChapterCopyStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETE,
    INCOMPLETE,
    FAILED,
}

enum class ClientChapterCopyFreshness {
    FRESH,
    STALE,
    UNVERIFIED,
    INCOMPLETE,
    ORPHANED,
}

data class ClientDeviceChapterCopy(
    val serverKey: String,
    val mangaId: Int,
    val chapterId: Int,
    val mangaTitle: String?,
    val chapterTitle: String,
    val chapterUrl: String,
    val chapterRealUrl: String?,
    val sourceOrder: Int,
    val chapterNumber: Float,
    val uploadDate: Long,
    val fetchedAt: String,
    val scanlator: String?,
    val storagePath: String?,
    val manifestHash: String,
    val status: ClientChapterCopyStatus,
    val freshness: ClientChapterCopyFreshness,
    val expectedPageCount: Int,
    val downloadedPageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val verifiedAt: Long?,
    val orphanedAt: Long?,
    val pages: List<ClientDeviceChapterCopyPage> = emptyList(),
) {
    val isComplete: Boolean
        get() = status == ClientChapterCopyStatus.COMPLETE &&
            downloadedPageCount == expectedPageCount &&
            pages.size == expectedPageCount &&
            pages.all { it.isPresent && !it.localUri.isNullOrBlank() }
}

data class ClientDeviceChapterCopyPage(
    val index: Int,
    val sourceUrl: String,
    val localUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val byteSize: Long? = null,
    val sha256: String? = null,
    val isPresent: Boolean = false,
)

data class ClientDeviceChapterCopyUpsert(
    val serverKey: String,
    val mangaTitle: String?,
    val manifest: SuwayomiChapterPageManifest,
    val storagePath: String?,
    val pages: List<ClientDeviceChapterCopyPage>,
    val status: ClientChapterCopyStatus,
    val freshness: ClientChapterCopyFreshness,
    val verifiedAt: Long? = null,
    val orphanedAt: Long? = null,
)

internal interface ClientDeviceChapterCopyRepository {
    suspend fun upsert(upsert: ClientDeviceChapterCopyUpsert): ClientDeviceChapterCopy
    suspend fun getCopy(serverKey: String, mangaId: Int, chapterId: Int): ClientDeviceChapterCopy?
    suspend fun deleteCopy(serverKey: String, mangaId: Int, chapterId: Int)
    suspend fun updateDownloadProgress(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        downloadedPageCount: Int,
    )
}

internal class ClientDeviceChapterCopyStore(
    private val database: Database,
) : ClientDeviceChapterCopyRepository {
    override suspend fun upsert(upsert: ClientDeviceChapterCopyUpsert): ClientDeviceChapterCopy {
        val now = now()
        val existing = getCopy(
            serverKey = upsert.serverKey,
            mangaId = upsert.manifest.chapter.mangaId,
            chapterId = upsert.manifest.chapter.id,
        )
        val createdAt = existing?.createdAt ?: now
        val downloadedPageCount = upsert.pages.count { it.isPresent && !it.localUri.isNullOrBlank() }
        val manifestHash = buildManifestHash(upsert.manifest)

        database.transaction {
            database.client_chapter_copiesQueries.upsertCopy(
                serverKey = upsert.serverKey,
                mangaId = upsert.manifest.chapter.mangaId.toLong(),
                chapterId = upsert.manifest.chapter.id.toLong(),
                mangaTitle = upsert.mangaTitle,
                chapterTitle = upsert.manifest.chapter.name,
                chapterUrl = upsert.manifest.chapter.url,
                chapterRealUrl = upsert.manifest.chapter.realUrl,
                sourceOrder = upsert.manifest.chapter.sourceOrder.toLong(),
                chapterNumber = upsert.manifest.chapter.chapterNumber.toDouble(),
                uploadDate = upsert.manifest.chapter.uploadDate,
                fetchedAt = upsert.manifest.chapter.fetchedAt,
                scanlator = upsert.manifest.chapter.scanlator,
                storagePath = upsert.storagePath,
                manifestHash = manifestHash,
                status = upsert.status.name,
                freshness = upsert.freshness.name,
                expectedPageCount = upsert.manifest.pages.size.toLong(),
                downloadedPageCount = downloadedPageCount.toLong(),
                createdAt = createdAt,
                updatedAt = now,
                verifiedAt = upsert.verifiedAt,
                orphanedAt = upsert.orphanedAt,
            )
            database.client_chapter_copiesQueries.clearPages(
                serverKey = upsert.serverKey,
                mangaId = upsert.manifest.chapter.mangaId.toLong(),
                chapterId = upsert.manifest.chapter.id.toLong(),
            )
            upsert.pages
                .sortedBy { it.index }
                .forEach { page ->
                    database.client_chapter_copiesQueries.insertPage(
                        serverKey = upsert.serverKey,
                        mangaId = upsert.manifest.chapter.mangaId.toLong(),
                        chapterId = upsert.manifest.chapter.id.toLong(),
                        pageIndex = page.index.toLong(),
                        sourceUrl = page.sourceUrl,
                        localUri = page.localUri,
                        fileName = page.fileName,
                        mimeType = page.mimeType,
                        byteSize = page.byteSize,
                        sha256 = page.sha256,
                        isPresent = if (page.isPresent) 1L else 0L,
                    )
                }
        }

        return requireNotNull(
            getCopy(
                serverKey = upsert.serverKey,
                mangaId = upsert.manifest.chapter.mangaId,
                chapterId = upsert.manifest.chapter.id,
            ),
        )
    }

    override suspend fun getCopy(serverKey: String, mangaId: Int, chapterId: Int): ClientDeviceChapterCopy? {
        val copy = database.client_chapter_copiesQueries
            .getCopy(serverKey, mangaId.toLong(), chapterId.toLong())
            .awaitAsOneOrNull()
            ?: return null
        val pages = getPages(serverKey, mangaId, chapterId)
        return copy.toModel(pages)
    }

    suspend fun getCompleteFreshCopy(serverKey: String, mangaId: Int, chapterId: Int): ClientDeviceChapterCopy? {
        val copy = database.client_chapter_copiesQueries
            .getCompleteFreshCopy(serverKey, mangaId.toLong(), chapterId.toLong())
            .awaitAsOneOrNull()
            ?: return null
        val pages = getPages(serverKey, mangaId, chapterId)
        return copy.toModel(pages).takeIf { it.isComplete }
    }

    suspend fun getCopiesForManga(serverKey: String, mangaId: Int): List<ClientDeviceChapterCopy> {
        return database.client_chapter_copiesQueries
            .getCopiesForManga(serverKey, mangaId.toLong())
            .awaitAsList()
            .map { copy -> copy.toModel(getPages(serverKey, copy.manga_id.toInt(), copy.chapter_id.toInt())) }
    }

    suspend fun getCopiesForServer(serverKey: String): List<ClientDeviceChapterCopy> {
        return database.client_chapter_copiesQueries
            .getCopiesForServer(serverKey)
            .awaitAsList()
            .map { copy -> copy.toModel(getPages(serverKey, copy.manga_id.toInt(), copy.chapter_id.toInt())) }
    }

    suspend fun getCopiesByFreshness(
        serverKey: String,
        freshness: ClientChapterCopyFreshness,
    ): List<ClientDeviceChapterCopy> {
        return database.client_chapter_copiesQueries
            .getCopiesByFreshness(serverKey, freshness.name)
            .awaitAsList()
            .map { copy -> copy.toModel(getPages(serverKey, copy.manga_id.toInt(), copy.chapter_id.toInt())) }
    }

    suspend fun getOrphanedCopies(serverKey: String): List<ClientDeviceChapterCopy> {
        return getCopiesByFreshness(serverKey, ClientChapterCopyFreshness.ORPHANED)
    }

    suspend fun updateState(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        status: ClientChapterCopyStatus,
        freshness: ClientChapterCopyFreshness,
        downloadedPageCount: Int,
        verifiedAt: Long? = null,
        orphanedAt: Long? = null,
    ) {
        database.client_chapter_copiesQueries.updateCopyState(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
            status = status.name,
            freshness = freshness.name,
            downloadedPageCount = downloadedPageCount.toLong(),
            updatedAt = now(),
            verifiedAt = verifiedAt,
            orphanedAt = orphanedAt,
        )
    }

    override suspend fun deleteCopy(serverKey: String, mangaId: Int, chapterId: Int) {
        database.client_chapter_copiesQueries.deleteCopy(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
        )
    }

    override suspend fun updateDownloadProgress(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        downloadedPageCount: Int,
    ) {
        database.client_chapter_copiesQueries.updateCopyDownloadProgress(
            serverKey = serverKey,
            mangaId = mangaId.toLong(),
            chapterId = chapterId.toLong(),
            status = ClientChapterCopyStatus.DOWNLOADING.name,
            freshness = ClientChapterCopyFreshness.INCOMPLETE.name,
            downloadedPageCount = downloadedPageCount.toLong(),
            updatedAt = now(),
        )
    }

    suspend fun markOrphaned(copy: ClientDeviceChapterCopy, orphanedAt: Long = now()) {
        if (copy.freshness == ClientChapterCopyFreshness.ORPHANED) return
        updateState(
            serverKey = copy.serverKey,
            mangaId = copy.mangaId,
            chapterId = copy.chapterId,
            status = copy.status,
            freshness = ClientChapterCopyFreshness.ORPHANED,
            downloadedPageCount = copy.downloadedPageCount,
            verifiedAt = copy.verifiedAt,
            orphanedAt = orphanedAt,
        )
    }

    private suspend fun getPages(serverKey: String, mangaId: Int, chapterId: Int): List<ClientDeviceChapterCopyPage> {
        return database.client_chapter_copiesQueries
            .getPages(serverKey, mangaId.toLong(), chapterId.toLong())
            .awaitAsList()
            .map { it.toModel() }
    }

    private fun now(): Long = System.currentTimeMillis()
}

fun buildClientDeviceCopyPages(manifest: SuwayomiChapterPageManifest): List<ClientDeviceChapterCopyPage> {
    return manifest.pages.mapIndexed { index, pageUrl ->
        ClientDeviceChapterCopyPage(index = index, sourceUrl = pageUrl)
    }
}

fun buildManifestHash(manifest: SuwayomiChapterPageManifest): String {
    val chapter = manifest.chapter
    return sha256(
        buildString {
            append("chapterId=").append(chapter.id).append('\n')
            append("mangaId=").append(chapter.mangaId).append('\n')
            append("url=").append(chapter.url).append('\n')
            append("realUrl=").append(chapter.realUrl.orEmpty()).append('\n')
            append("sourceOrder=").append(chapter.sourceOrder).append('\n')
            append("chapterNumber=").append(chapter.chapterNumber).append('\n')
            append("name=").append(chapter.name).append('\n')
            append("scanlator=").append(chapter.scanlator.orEmpty()).append('\n')
            append("uploadDate=").append(chapter.uploadDate).append('\n')
            append("fetchedAt=").append(chapter.fetchedAt).append('\n')
            append("pageCount=").append(chapter.pageCount).append('\n')
            manifest.pages.forEachIndexed { index, page ->
                append("page[").append(index).append("]=").append(page).append('\n')
            }
        },
    )
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun Client_chapter_copies.toModel(
    pages: List<ClientDeviceChapterCopyPage>,
): ClientDeviceChapterCopy {
    return ClientDeviceChapterCopy(
        serverKey = server_key,
        mangaId = manga_id.toInt(),
        chapterId = chapter_id.toInt(),
        mangaTitle = manga_title,
        chapterTitle = chapter_title,
        chapterUrl = chapter_url,
        chapterRealUrl = chapter_real_url,
        sourceOrder = source_order.toInt(),
        chapterNumber = chapter_number.toFloat(),
        uploadDate = upload_date,
        fetchedAt = fetched_at,
        scanlator = scanlator,
        storagePath = storage_path,
        manifestHash = manifest_hash,
        status = enumValueOf(status),
        freshness = enumValueOf(freshness),
        expectedPageCount = expected_page_count.toInt(),
        downloadedPageCount = downloaded_page_count.toInt(),
        createdAt = created_at,
        updatedAt = updated_at,
        verifiedAt = verified_at,
        orphanedAt = orphaned_at,
        pages = pages,
    )
}

private fun Client_chapter_copy_pages.toModel(): ClientDeviceChapterCopyPage {
    return ClientDeviceChapterCopyPage(
        index = page_index.toInt(),
        sourceUrl = source_url,
        localUri = local_uri,
        fileName = file_name,
        mimeType = mime_type,
        byteSize = byte_size,
        sha256 = sha256,
        isPresent = is_present != 0L,
    )
}
