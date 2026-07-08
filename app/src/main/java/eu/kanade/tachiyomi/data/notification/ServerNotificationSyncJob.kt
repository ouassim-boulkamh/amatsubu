package eu.kanade.tachiyomi.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
            logcat(LogPriority.DEBUG) { "Server notification sync worker started" }
            val serverIdentity = clientProvider.baseUrl()
            val client = clientProvider.graphQlClient
            val libraryUpdateStatus = client.getLibraryUpdateStatus()
            val jobs = libraryUpdateStatus.jobsInfo

            logcat(LogPriority.DEBUG) {
                "Server notification sync library status: " +
                    "running=${jobs.isRunning}, finished=${jobs.finishedJobs}, total=${jobs.totalJobs}, " +
                    "skippedCategories=${jobs.skippedCategoriesCount}, skippedMangas=${jobs.skippedMangasCount}"
            }

            val libraryResult = reconciler.reconcileLibraryUpdate(
                client = client,
                serverIdentity = serverIdentity,
                status = libraryUpdateStatus,
            )
            val recentChaptersChecked = libraryResult.recentChaptersChecked
            logcat(LogPriority.DEBUG) {
                "Server notification sync library reconciliation: " +
                    "completed=${libraryResult.completed}, recentChaptersChecked=$recentChaptersChecked, " +
                    "unnotifiedChapters=${libraryResult.unnotifiedChapterCount}"
            }
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

            logcat(LogPriority.DEBUG) { "Server notification sync completed: success" }
            Result.success()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error.isSuwayomiServerUnavailable()) {
                logcat(LogPriority.DEBUG, error) { "Server notification sync skipped; Suwayomi server unavailable" }
                logcat(LogPriority.DEBUG) { "Server notification sync completed: server_unavailable_retry" }
            } else {
                logcat(LogPriority.ERROR, error) { "Server notification sync failed" }
                logcat(LogPriority.DEBUG) { "Server notification sync completed: retry" }
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
        private const val UNIQUE_IMMEDIATE_WORK_NAME = "ServerNotificationSyncImmediate"
        private const val UNIQUE_DELAYED_30_SECONDS_WORK_NAME = "ServerNotificationSyncDelayed30s"
        private const val UNIQUE_DELAYED_2_MINUTES_WORK_NAME = "ServerNotificationSyncDelayed2m"
        private const val TAG = "ServerNotificationSync"
        private const val REPEAT_INTERVAL_MINUTES = 15L
        private const val FIRST_DELAY_SECONDS = 30L
        private const val SECOND_DELAY_MINUTES = 2L

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

        fun schedulePromptReconciliation(context: Context) {
            schedule(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediateRequest = OneTimeWorkRequestBuilder<ServerNotificationSyncJob>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            val delayed30SecondsRequest = OneTimeWorkRequestBuilder<ServerNotificationSyncJob>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setInitialDelay(FIRST_DELAY_SECONDS, TimeUnit.SECONDS)
                .build()
            val delayed2MinutesRequest = OneTimeWorkRequestBuilder<ServerNotificationSyncJob>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setInitialDelay(SECOND_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            val workManager = context.workManager
            workManager.enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest,
            )
            workManager.enqueueUniqueWork(
                UNIQUE_DELAYED_30_SECONDS_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                delayed30SecondsRequest,
            )
            workManager.enqueueUniqueWork(
                UNIQUE_DELAYED_2_MINUTES_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                delayed2MinutesRequest,
            )
            logcat(LogPriority.DEBUG) { "Scheduled prompt server notification reconciliation work" }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DELAYED_30_SECONDS_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DELAYED_2_MINUTES_WORK_NAME)
        }
    }
}
