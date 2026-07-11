package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLibraryUpdateStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationDownloadAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationLibraryUpdateAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverNotificationSyncAffectedEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

internal class ServerNotificationReconciler(
    private val renderer: ServerNotificationRenderer,
    private val checkpoints: ServerNotificationCheckpointStore,
) {
    suspend fun reconcileLibraryUpdate(
        client: SuwayomiGraphQlClient,
        serverIdentity: String,
        status: SuwayomiLibraryUpdateStatusDto,
    ): LibraryReconciliationResult {
        val jobs = status.jobsInfo
        val transition = checkpoints.recordLibraryUpdate(serverIdentity, jobs)

        if (!jobs.isRunning) {
            renderer.cancelLibraryProgress()
            return if (transition.completed) {
                ServerStateSync.requestRefresh(*serverNotificationLibraryUpdateAffectedEntities().toTypedArray())
                showLibraryUpdateCompleteNotification(client, transition.startedAt)
            } else {
                LibraryReconciliationResult(
                    started = false,
                    completed = false,
                    recentChaptersChecked = false,
                    unnotifiedChapterCount = 0,
                )
            }
        }

        renderer.showLibraryProgress(jobs)
        return LibraryReconciliationResult(
            started = !transition.wasRunning,
            completed = false,
            recentChaptersChecked = false,
            unnotifiedChapterCount = 0,
        )
    }

    fun reconcileDownloadStatus(
        serverIdentity: String,
        status: SuwayomiDownloadStatusDto,
    ) {
        val activeQueue = status.queue.filterNot { it.state.isDoneState() }
        val hasVisibleQueue = status.queue.isNotEmpty() && !(activeQueue.isEmpty() && status.state.isStoppedState())
        val transition = checkpoints.recordDownloadQueue(
            serverIdentity = serverIdentity,
            visible = hasVisibleQueue,
            state = status.state,
        )
        if (!hasVisibleQueue) {
            renderer.cancelDownloadNotifications()
            if (transition.becameHidden) {
                ServerStateSync.requestRefresh(*serverNotificationDownloadAffectedEntities().toTypedArray())
            }
            return
        }
        val visibleQueue = activeQueue.ifEmpty { status.queue }

        val errorDownloads = visibleQueue.filter { it.state.isErrorState() }
        if (errorDownloads.isEmpty()) {
            renderer.cancelDownloadError()
        }
        val unnotifiedErrors = checkpoints.filterUnnotifiedDownloadErrors(errorDownloads)
        renderer.showDownloadNotifications(status, visibleQueue, unnotifiedErrors)
        checkpoints.markDownloadErrorsNotified(unnotifiedErrors)
    }

    fun reconcileSyncYomiStatus(
        serverIdentity: String,
        status: SuwayomiSyncStatusDto?,
    ) {
        val transition = checkpoints.recordSyncYomiStatus(serverIdentity, status)
        if (status == null) {
            renderer.cancelSyncYomiProgress()
            return
        }

        if (transition.isRunning) {
            renderer.showSyncYomiProgress(status)
            return
        }

        renderer.cancelSyncYomiProgress()
        if (transition.notifyTerminal) {
            renderer.showSyncYomiTerminal(status)
            ServerStateSync.requestRefresh(*serverNotificationSyncAffectedEntities().toTypedArray())
        }
    }

    private suspend fun showLibraryUpdateCompleteNotification(
        client: SuwayomiGraphQlClient,
        startedAt: Long,
    ): LibraryReconciliationResult {
        val reconciliation = withContext(Dispatchers.IO) {
            runCatching {
                val chapters = client.getRecentChapters()
                val storedCheckpoint = client.getGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY)
                    ?.value
                    ?.decodeNewChapterCheckpointOrNull()
                val highWater = chapters.highWaterCheckpoint()

                if (storedCheckpoint?.seeded != true) {
                    highWater?.let { client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, it.encode()) }
                    NewChapterReconciliation(chaptersToNotify = emptyList(), checkpointToAdvance = null)
                } else {
                    val chaptersToNotify = chapters
                        .asSequence()
                        .filter { !it.isRead }
                        .filter { it.isAfter(storedCheckpoint) }
                        .filter { it.wasFetchedAfterLibraryAdd() }
                        .take(MAX_NEW_CHAPTER_NOTIFICATION_BATCH)
                        .toList()
                    NewChapterReconciliation(
                        chaptersToNotify = chaptersToNotify,
                        checkpointToAdvance = highWater?.takeIf { it.isAfter(storedCheckpoint) },
                    )
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to resolve Suwayomi new chapter notification details" }
                NewChapterReconciliation(chaptersToNotify = emptyList(), checkpointToAdvance = null)
            }
        }

        renderer.showNewChapters(reconciliation.chaptersToNotify)
        reconciliation.checkpointToAdvance?.let { checkpoint ->
            withContext(Dispatchers.IO) {
                client.setGlobalMeta(NEW_CHAPTER_CHECKPOINT_KEY, checkpoint.encode())
            }
        }
        return LibraryReconciliationResult(
            started = false,
            completed = true,
            recentChaptersChecked = true,
            unnotifiedChapterCount = reconciliation.chaptersToNotify.size,
        )
    }

    private fun String.isDoneState(): Boolean {
        return equals("DONE", ignoreCase = true) ||
            equals("DOWNLOADED", ignoreCase = true) ||
            equals("FINISHED", ignoreCase = true)
    }

    private fun String.isStoppedState(): Boolean {
        return equals("STOPPED", ignoreCase = true)
    }

    private fun String.isErrorState(): Boolean {
        return equals("ERROR", ignoreCase = true) || equals("FAILED", ignoreCase = true)
    }
}

internal data class LibraryReconciliationResult(
    val started: Boolean,
    val completed: Boolean,
    val recentChaptersChecked: Boolean,
    val unnotifiedChapterCount: Int,
)

internal fun String.toSuwayomiEpochMillis(): Long {
    val value = toLongOrNull() ?: return 0L
    return if (value in 1 until EPOCH_MILLIS_THRESHOLD) value * 1_000L else value
}

internal fun String.wasFetchedDuringObservedLibraryUpdate(startedAtMillis: Long): Boolean {
    return toSuwayomiEpochMillis() >= startedAtMillis - OBSERVED_LIBRARY_UPDATE_START_GRACE_MS
}

@Serializable
internal data class NewChapterCheckpoint(
    val version: Int = NEW_CHAPTER_CHECKPOINT_VERSION,
    val lastFetchedAt: Long = 0L,
    val lastChapterId: Int = 0,
    val seeded: Boolean = true,
) {
    fun encode(): String = newChapterCheckpointJson.encodeToString(serializer(), this)

    fun isAfter(other: NewChapterCheckpoint): Boolean {
        return lastFetchedAt > other.lastFetchedAt ||
            (lastFetchedAt == other.lastFetchedAt && lastChapterId > other.lastChapterId)
    }
}

private data class NewChapterReconciliation(
    val chaptersToNotify: List<SuwayomiChapterWithMangaDto>,
    val checkpointToAdvance: NewChapterCheckpoint?,
)

private fun String.decodeNewChapterCheckpointOrNull(): NewChapterCheckpoint? {
    return try {
        newChapterCheckpointJson.decodeFromString(NewChapterCheckpoint.serializer(), this)
            .takeIf { it.version == NEW_CHAPTER_CHECKPOINT_VERSION }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun List<SuwayomiChapterWithMangaDto>.highWaterCheckpoint(): NewChapterCheckpoint? {
    return map { it.toNewChapterCheckpoint() }
        .maxWithOrNull(compareBy<NewChapterCheckpoint> { it.lastFetchedAt }.thenBy { it.lastChapterId })
}

private fun SuwayomiChapterWithMangaDto.toNewChapterCheckpoint(): NewChapterCheckpoint {
    return NewChapterCheckpoint(
        lastFetchedAt = fetchedAt.toSuwayomiEpochMillis(),
        lastChapterId = id,
        seeded = true,
    )
}

private fun SuwayomiChapterWithMangaDto.isAfter(checkpoint: NewChapterCheckpoint): Boolean {
    return toNewChapterCheckpoint().isAfter(checkpoint)
}

private fun SuwayomiChapterWithMangaDto.wasFetchedAfterLibraryAdd(): Boolean {
    val inLibraryAt = manga.inLibraryAt ?: return true
    return fetchedAt.toSuwayomiEpochMillis() >= inLibraryAt
}

internal const val NEW_CHAPTER_CHECKPOINT_KEY = "amatsubu.newChapterCheckpoint"
internal const val MAX_NEW_CHAPTER_NOTIFICATION_BATCH = 100
private const val NEW_CHAPTER_CHECKPOINT_VERSION = 1
private const val EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L
private const val OBSERVED_LIBRARY_UPDATE_START_GRACE_MS = 60_000L
private val newChapterCheckpointJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
