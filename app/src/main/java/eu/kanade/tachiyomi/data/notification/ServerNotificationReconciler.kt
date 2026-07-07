package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLibraryUpdateStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

internal class ServerNotificationReconciler(
    private val renderer: ServerNotificationRenderer,
    private val checkpoints: ServerNotificationCheckpointStore = ServerNotificationCheckpointStore(),
) {
    suspend fun reconcileLibraryUpdate(
        client: SuwayomiGraphQlClient,
        serverIdentity: String,
        status: SuwayomiLibraryUpdateStatusDto,
    ) {
        val jobs = status.jobsInfo
        val transition = checkpoints.recordLibraryUpdate(serverIdentity, jobs)

        if (!jobs.isRunning) {
            renderer.cancelLibraryProgress()
            if (transition.completed) {
                ServerStateSync.requestRefresh()
                showLibraryUpdateCompleteNotification(client, transition.startedAt)
            }
            return
        }

        renderer.showLibraryProgress(jobs)
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
                ServerStateSync.requestRefresh()
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
            ServerStateSync.requestRefresh()
        }
    }

    private suspend fun showLibraryUpdateCompleteNotification(
        client: SuwayomiGraphQlClient,
        startedAt: Long,
    ) {
        val recentUnread = withContext(Dispatchers.IO) {
            runCatching {
                val chapters = client.getRecentChapters()
                val unnotifiedChapterIds = checkpoints.filterUnnotifiedChapterIds(chapters.map { it.id })
                chapters
                    .asSequence()
                    .filter { !it.isRead }
                    .filter { (it.fetchedAt.toLongOrNull() ?: 0L) >= startedAt }
                    .filter { it.id in unnotifiedChapterIds }
                    .toList()
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to resolve Suwayomi new chapter notification details" }
                emptyList()
            }
        }

        renderer.showNewChapters(recentUnread)
        checkpoints.markChaptersNotified(recentUnread.map { it.id })
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
