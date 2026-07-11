package eu.kanade.tachiyomi.data.suwayomi

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.delay
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

internal class ClientDeviceChapterCopyDownloader(
    private val store: ClientDeviceChapterCopyRepository,
    private val pageFetcher: ClientDeviceChapterPageFetcher,
    private val rootDirectory: () -> File,
    private val now: () -> Long = System::currentTimeMillis,
    private val pageRetryDelaysMillis: List<Long> = listOf(500L, 1_500L),
    private val promoteTempDirectory: (tempDir: File, finalDir: File) -> Boolean = { tempDir, finalDir ->
        tempDir.renameTo(finalDir)
    },
    private val deleteCopyDirectory: (directory: File) -> Boolean = { directory ->
        directory.deleteRecursively()
    },
) {

    suspend fun saveToDevice(
        serverKey: String,
        mangaTitle: String?,
        chapterId: Int,
    ): ClientDeviceChapterCopy = withIOContext {
        val manifest = pageFetcher.fetchManifest(chapterId)
        val expectedPages = buildClientDeviceCopyPages(manifest)
        val tempDir = chapterTempDirectory(serverKey, manifest)
        val finalDir = chapterDirectory(serverKey, manifest)
        val existingCompleteCopy = store.getCopy(
            serverKey = serverKey,
            mangaId = manifest.chapter.mangaId,
            chapterId = manifest.chapter.id,
        )?.takeIf { it.isComplete && File(it.storagePath.orEmpty()).isDirectory }
        val downloadedPages = mutableListOf<ClientDeviceChapterCopyPage>()

        tempDir.deleteRecursively()
        tempDir.mkdirs()
        store.upsert(
            ClientDeviceChapterCopyUpsert(
                serverKey = serverKey,
                mangaTitle = mangaTitle,
                manifest = manifest,
                storagePath = finalDir.absolutePath,
                pages = expectedPages,
                status = ClientChapterCopyStatus.DOWNLOADING,
                freshness = ClientChapterCopyFreshness.INCOMPLETE,
            ),
        )

        try {
            expectedPages.forEach { page ->
                val pageFile = File(tempDir, pageFileName(page.index))
                val downloaded = downloadPageWithRetry(page.sourceUrl, pageFile)
                downloadedPages += page.copy(
                    localUri = pageFile.toURI().toString(),
                    fileName = pageFile.name,
                    mimeType = downloaded.mimeType,
                    byteSize = downloaded.byteSize,
                    sha256 = downloaded.sha256,
                    isPresent = true,
                )
                store.updateDownloadProgress(
                    serverKey = serverKey,
                    mangaId = manifest.chapter.mangaId,
                    chapterId = manifest.chapter.id,
                    downloadedPageCount = downloadedPages.size,
                )
            }

            finalDir.deleteRecursively()
            check(promoteTempDirectory(tempDir, finalDir)) {
                "Could not promote downloaded chapter copy to ${finalDir.absolutePath}"
            }

            val finalPages = downloadedPages.map { page ->
                page.copy(localUri = File(finalDir, page.fileName.orEmpty()).toURI().toString())
            }
            store.upsert(
                ClientDeviceChapterCopyUpsert(
                    serverKey = serverKey,
                    mangaTitle = mangaTitle,
                    manifest = manifest,
                    storagePath = finalDir.absolutePath,
                    pages = finalPages,
                    status = ClientChapterCopyStatus.COMPLETE,
                    freshness = ClientChapterCopyFreshness.FRESH,
                    verifiedAt = now(),
                ),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (existingCompleteCopy != null) {
                store.upsert(existingCompleteCopy.toUpsert())
            } else {
                finalDir.deleteRecursively()
                if (downloadedPages.isNotEmpty()) {
                    tempDir.renameTo(finalDir)
                } else {
                    tempDir.deleteRecursively()
                }
                val incompletePages = expectedPages.map { expected ->
                    downloadedPages.firstOrNull { it.index == expected.index }
                        ?.copy(
                            localUri = File(
                                finalDir,
                                expected.fileName ?: pageFileName(expected.index),
                            ).toURI().toString(),
                        )
                        ?: expected
                }
                store.upsert(
                    ClientDeviceChapterCopyUpsert(
                        serverKey = serverKey,
                        mangaTitle = mangaTitle,
                        manifest = manifest,
                        storagePath = finalDir.absolutePath,
                        pages = incompletePages,
                        status = ClientChapterCopyStatus.INCOMPLETE,
                        freshness = ClientChapterCopyFreshness.INCOMPLETE,
                    ),
                )
            }
            throw error
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun downloadPageWithRetry(
        sourceUrl: String,
        destination: File,
    ): DownloadedClientDevicePage {
        pageRetryDelaysMillis.forEach { delayMillis ->
            try {
                return pageFetcher.downloadPage(sourceUrl, destination)
            } catch (error: Throwable) {
                if (error is CancellationException || !error.isTransientPageDownloadFailure()) {
                    throw error
                }
                destination.delete()
                delay(delayMillis)
            }
        }
        return pageFetcher.downloadPage(sourceUrl, destination)
    }

    suspend fun removeDeviceCopy(serverKey: String, mangaId: Int, chapterId: Int) = withIOContext {
        val existing = store.getCopy(serverKey, mangaId, chapterId)
        existing?.storagePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.let { directory ->
                check(deleteCopyDirectory(directory)) {
                    "Could not remove downloaded chapter copy at ${directory.absolutePath}"
                }
            }
        store.deleteCopy(serverKey, mangaId, chapterId)
    }

    private fun chapterDirectory(serverKey: String, manifest: SuwayomiChapterPageManifest): File {
        return File(
            rootDirectory(),
            ClientDeviceChapterCopyPathPolicy.relativeChapterPath(serverKey, manifest),
        )
    }

    private fun chapterTempDirectory(serverKey: String, manifest: SuwayomiChapterPageManifest): File {
        return File(chapterDirectory(serverKey, manifest).parentFile, "${manifest.chapter.id}.tmp")
    }

    private fun pageFileName(index: Int): String = ClientDeviceChapterCopyPathPolicy.pageFileName(index)
}

private fun ClientDeviceChapterCopy.toUpsert(): ClientDeviceChapterCopyUpsert {
    return ClientDeviceChapterCopyUpsert(
        serverKey = serverKey,
        mangaTitle = mangaTitle,
        manifest = SuwayomiChapterPageManifest(
            pages = pages.map { it.sourceUrl },
            chapter = SuwayomiChapterDto(
                id = chapterId,
                mangaId = mangaId,
                name = chapterTitle,
                url = chapterUrl,
                realUrl = chapterRealUrl,
                sourceOrder = sourceOrder,
                chapterNumber = chapterNumber,
                uploadDate = uploadDate,
                fetchedAt = fetchedAt,
                scanlator = scanlator,
                pageCount = expectedPageCount,
            ),
        ),
        storagePath = storagePath,
        pages = pages,
        status = status,
        freshness = freshness,
        verifiedAt = verifiedAt,
        orphanedAt = orphanedAt,
    )
}

internal object ClientDeviceChapterCopyPathPolicy {
    fun relativeChapterPath(serverKey: String, manifest: SuwayomiChapterPageManifest): String {
        return listOf(
            DiskUtil.hashKeyForDisk(serverKey),
            manifest.chapter.mangaId.toString(),
            manifest.chapter.id.toString(),
        ).joinToString(File.separator)
    }

    fun pageFileName(index: Int): String = "page-${index.toString().padStart(4, '0')}.img"
}

private fun Throwable.isTransientPageDownloadFailure(): Boolean {
    return when (this) {
        is IOException -> true
        is HttpException -> code == 408 || code == 429 || code in 500..599
        else -> false
    }
}

internal data class DownloadedClientDevicePage(
    val mimeType: String?,
    val byteSize: Long,
    val sha256: String,
)

internal interface ClientDeviceChapterPageFetcher {
    suspend fun fetchManifest(chapterId: Int): SuwayomiChapterPageManifest
    suspend fun downloadPage(sourceUrl: String, destination: File): DownloadedClientDevicePage
}

internal class SuwayomiClientDeviceChapterPageFetcher(
    private val graphQlClient: SuwayomiGraphQlClient,
    private val baseUrl: () -> String,
    private val httpClient: OkHttpClient,
) : ClientDeviceChapterPageFetcher {

    override suspend fun fetchManifest(chapterId: Int): SuwayomiChapterPageManifest {
        return graphQlClient.getChapterPageManifest(chapterId)
    }

    override suspend fun downloadPage(sourceUrl: String, destination: File): DownloadedClientDevicePage {
        val response = httpClient.newCall(GET(resolveServerUrl(baseUrl(), sourceUrl))).awaitSuccess()
        response.use {
            val body = it.body
            destination.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            destination.outputStream().buffered().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        totalBytes += read
                    }
                }
            }
            return DownloadedClientDevicePage(
                mimeType = body.contentType()?.toString(),
                byteSize = totalBytes,
                sha256 = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) },
            )
        }
    }
}

internal class ClientDeviceChapterCopyWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
        if (action == null) {
            logcat(LogPriority.ERROR) { "Client device chapter copy worker missing action" }
            return Result.failure()
        }
        return try {
            val provider = context.appDependencies.suwayomiClientProvider
            when (val identityCheck = EnqueueBoundServerIdentity.check(inputData, provider.serverKey())) {
                is EnqueueBoundServerIdentityCheck.Matched -> Unit
                EnqueueBoundServerIdentityCheck.Missing -> {
                    logcat(LogPriority.ERROR) {
                        "Client device chapter copy worker missing enqueue-bound server identity"
                    }
                    return Result.failure()
                }
                is EnqueueBoundServerIdentityCheck.Mismatched -> {
                    logcat(LogPriority.ERROR) {
                        "Client device chapter copy worker server identity mismatch " +
                            "enqueued=${identityCheck.enqueuedServerKey} current=${identityCheck.currentServerKey}"
                    }
                    return Result.failure()
                }
            }
            val downloader = ClientDeviceChapterCopyDownloader(
                store = context.appDependencies.clientDeviceChapterCopyStore,
                pageFetcher = SuwayomiClientDeviceChapterPageFetcher(
                    graphQlClient = provider.graphQlClient,
                    baseUrl = provider::baseUrl,
                    httpClient = provider.httpClient,
                ),
                rootDirectory = { defaultRootDirectory(context) },
            )

            when (action) {
                ACTION_SAVE -> {
                    val chapterId = inputData.getInt(KEY_CHAPTER_ID, -1).takeIf { it >= 0 }
                    if (chapterId == null) {
                        logcat(LogPriority.ERROR) { "Client device chapter copy save missing chapter id" }
                        return Result.failure()
                    }
                    downloader.saveToDevice(
                        serverKey = provider.serverKey(),
                        mangaTitle = inputData.getString(KEY_MANGA_TITLE),
                        chapterId = chapterId,
                    )
                }
                ACTION_REMOVE -> {
                    val mangaId = inputData.getInt(KEY_MANGA_ID, -1).takeIf { it >= 0 }
                    val chapterId = inputData.getInt(KEY_CHAPTER_ID, -1).takeIf { it >= 0 }
                    if (mangaId == null || chapterId == null) {
                        logcat(LogPriority.ERROR) {
                            "Client device chapter copy remove missing ids mangaId=$mangaId chapterId=$chapterId"
                        }
                        return Result.failure()
                    }
                    downloader.removeDeviceCopy(
                        serverKey = provider.serverKey(),
                        mangaId = mangaId,
                        chapterId = chapterId,
                    )
                }
                else -> {
                    logcat(LogPriority.ERROR) { "Client device chapter copy worker unknown action=$action" }
                    return Result.failure()
                }
            }
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Client device chapter copy worker failed" }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ClientDeviceChapterCopy"
        private const val ACTION_SAVE = "save"
        private const val ACTION_REMOVE = "remove"
        private const val KEY_ACTION = "action"
        private const val KEY_MANGA_ID = "manga_id"
        private const val KEY_MANGA_TITLE = "manga_title"
        private const val KEY_CHAPTER_ID = "chapter_id"

        fun enqueueSave(context: Context, mangaTitle: String?, chapterId: Int) {
            val request = OneTimeWorkRequestBuilder<ClientDeviceChapterCopyWorker>()
                .addTag(TAG)
                .setInputData(
                    buildSaveInputData(
                        serverKey = context.appDependencies.suwayomiClientProvider.serverKey(),
                        mangaTitle = mangaTitle,
                        chapterId = chapterId,
                    ),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            context.workManager.enqueueUniqueWork(
                "ClientDeviceChapterCopySave-$chapterId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueRemove(context: Context, mangaId: Int, chapterId: Int) {
            val request = OneTimeWorkRequestBuilder<ClientDeviceChapterCopyWorker>()
                .addTag(TAG)
                .setInputData(
                    buildRemoveInputData(
                        serverKey = context.appDependencies.suwayomiClientProvider.serverKey(),
                        mangaId = mangaId,
                        chapterId = chapterId,
                    ),
                )
                .build()

            context.workManager.enqueueUniqueWork(
                "ClientDeviceChapterCopyRemove-$mangaId-$chapterId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        fun defaultRootDirectory(context: Context): File {
            return File(context.filesDir, "client-chapter-copies")
        }

        internal fun buildSaveInputData(
            serverKey: String,
            mangaTitle: String?,
            chapterId: Int,
        ): Data {
            return EnqueueBoundServerIdentity.put(
                Data.Builder(),
                serverKey,
            )
                .putString(KEY_ACTION, ACTION_SAVE)
                .putString(KEY_MANGA_TITLE, mangaTitle)
                .putInt(KEY_CHAPTER_ID, chapterId)
                .build()
        }

        internal fun buildRemoveInputData(
            serverKey: String,
            mangaId: Int,
            chapterId: Int,
        ): Data {
            return EnqueueBoundServerIdentity.put(
                Data.Builder(),
                serverKey,
            )
                .putString(KEY_ACTION, ACTION_REMOVE)
                .putInt(KEY_MANGA_ID, mangaId)
                .putInt(KEY_CHAPTER_ID, chapterId)
                .build()
        }
    }
}
