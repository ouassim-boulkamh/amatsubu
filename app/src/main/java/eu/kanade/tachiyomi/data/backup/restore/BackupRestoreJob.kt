package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.suwayomi.EnqueueBoundServerIdentity
import eu.kanade.tachiyomi.data.suwayomi.EnqueueBoundServerIdentityCheck
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class BackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        setForegroundSafely()

        return try {
            val dependencies = context.appDependencies
            BackupRestorer(
                context = context,
                notifier = notifier,
                isSync = isSync,
                decoder = BackupDecoder(context, dependencies.protoBuf),
                preferenceStore = dependencies.preferenceStore,
                preferenceRestorer = PreferenceRestorer(context, dependencies.preferenceStore),
                mangaMetadataStore = dependencies.clientMangaMetadataStore,
                currentServerKey = dependencies.suwayomiClientProvider::serverKey,
            ).restore(uri, options)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_CLIENT)
        }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                SYNC_KEY to sync,
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupRestoreJob>()
                .addTag(TAG_CLIENT)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_CLIENT, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG_CLIENT)
        }
    }
}

class ServerBackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { RestoreOptions.fromBooleanArray(it) }

        if (uri == null || options == null) {
            return Result.failure()
        }

        val isSync = inputData.getBoolean(SYNC_KEY, false)

        setForegroundSafely()

        val provider = context.appDependencies.suwayomiClientProvider
        when (val identityCheck = EnqueueBoundServerIdentity.check(inputData, provider.serverKey())) {
            is EnqueueBoundServerIdentityCheck.Matched -> Unit
            EnqueueBoundServerIdentityCheck.Missing -> {
                val message = "Server backup restore job missing enqueue-bound server identity"
                logcat(LogPriority.ERROR) { message }
                notifier.showRestoreError(message)
                context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
                return Result.failure()
            }
            is EnqueueBoundServerIdentityCheck.Mismatched -> {
                val message = "Server changed since restore was queued. Start the restore again."
                logcat(LogPriority.ERROR) {
                    "Server backup restore job server identity mismatch " +
                        "enqueued=${identityCheck.enqueuedServerKey} current=${identityCheck.currentServerKey}"
                }
                notifier.showRestoreError(message)
                context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
                return Result.failure()
            }
        }

        return try {
            ServerBackupRestorer(context, notifier, isSync, provider).restore(uri, options)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_SERVER)
        }

        fun start(
            context: Context,
            uri: Uri,
            options: RestoreOptions,
            sync: Boolean = false,
        ) {
            val inputData = buildServerBackupRestoreInputData(
                locationUri = uri.toString(),
                options = options,
                sync = sync,
                serverKey = context.appDependencies.suwayomiClientProvider.serverKey(),
            )
            val request = OneTimeWorkRequestBuilder<ServerBackupRestoreJob>()
                .addTag(TAG_SERVER)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_SERVER, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG_SERVER)
        }
    }
}

internal fun buildServerBackupRestoreInputData(
    locationUri: String,
    options: RestoreOptions,
    sync: Boolean,
    serverKey: String,
): Data {
    return EnqueueBoundServerIdentity.put(
        Data.Builder(),
        serverKey,
    )
        .putString(LOCATION_URI_KEY, locationUri)
        .putBoolean(SYNC_KEY, sync)
        .putBooleanArray(OPTIONS_KEY, options.asBooleanArray())
        .build()
}

private const val TAG_CLIENT = "BackupRestore"
private const val TAG_SERVER = "ServerBackupRestore"

private const val LOCATION_URI_KEY = "location_uri" // String
private const val SYNC_KEY = "sync" // Boolean
private const val OPTIONS_KEY = "options" // BooleanArray
