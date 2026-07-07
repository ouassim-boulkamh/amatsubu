package eu.kanade.tachiyomi.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

internal class ServerNotificationSyncJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val clientProvider = SuwayomiClientProvider()
    private val renderer = ServerNotificationRenderer(context)
    private val checkpoints = ServerNotificationCheckpointStore()
    private val reconciler = ServerNotificationReconciler(renderer, checkpoints)

    override suspend fun doWork(): Result {
        return try {
            val serverIdentity = clientProvider.baseUrl()
            val client = clientProvider.graphQlClient

            reconciler.reconcileLibraryUpdate(
                client = client,
                serverIdentity = serverIdentity,
                status = client.getLibraryUpdateStatus(),
            )
            reconciler.reconcileDownloadStatus(
                serverIdentity = serverIdentity,
                status = client.getDownloadStatus(),
            )
            reconciler.reconcileSyncYomiStatus(
                serverIdentity = serverIdentity,
                status = client.lastSyncStatus(),
            )
            reconcileExtensionUpdates(
                serverIdentity = serverIdentity,
                extensions = client.extensionList(),
            )

            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error.isSuwayomiServerUnavailable()) {
                logcat(LogPriority.DEBUG, error) { "Server notification sync skipped; Suwayomi server unavailable" }
            } else {
                logcat(LogPriority.ERROR, error) { "Server notification sync failed" }
            }
            Result.retry()
        }
    }

    private fun reconcileExtensionUpdates(
        serverIdentity: String,
        extensions: List<SuwayomiExtensionDto>,
    ) {
        val updateExtensions = extensions.filter { it.isInstalled && it.hasUpdate }
        if (updateExtensions.isEmpty()) {
            checkpoints.filterUnnotifiedExtensionUpdates(serverIdentity, extensions)
            renderer.cancelExtensionUpdates()
            return
        }

        val unnotifiedUpdates = checkpoints.filterUnnotifiedExtensionUpdates(serverIdentity, updateExtensions)
        if (unnotifiedUpdates.isEmpty()) return

        renderer.showExtensionUpdates(updateExtensions)
        checkpoints.markExtensionUpdatesNotified(updateExtensions)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ServerNotificationSync"
        private const val TAG = "ServerNotificationSync"
        private const val REPEAT_INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val hasActiveServerJob = runCatching {
                ServerNotificationCheckpointStore().hasActiveServerJob(SuwayomiClientProvider().baseUrl())
            }.getOrDefault(false)

            if (!notificationsEnabled && !hasActiveServerJob) {
                cancel(context)
                return
            }

            val request = PeriodicWorkRequestBuilder<ServerNotificationSyncJob>(
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
