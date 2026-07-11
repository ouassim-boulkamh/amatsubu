package eu.kanade.tachiyomi.data.backup.create

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
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.ServerBackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.suwayomi.EnqueueBoundServerIdentity
import eu.kanade.tachiyomi.data.suwayomi.EnqueueBoundServerIdentityCheck
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class BackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: return Result.failure()

        setForegroundSafely()

        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
            ?: BackupOptions()

        return try {
            val dependencies = context.appDependencies
            val decoder = BackupDecoder(context, dependencies.protoBuf)
            val location = BackupCreator(
                context = context,
                parser = dependencies.protoBuf,
                preferenceBackupCreator = PreferenceBackupCreator(dependencies.preferenceStore),
                mangaMetadataStore = dependencies.clientMangaMetadataStore,
                currentServerKey = dependencies.suwayomiClientProvider::serverKey,
                validator = BackupFileValidator(context, decoder),
            ).backup(uri, options)
            notifier.showBackupComplete(UniFile.fromUri(context, location.toUri())!!)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_CLIENT_MANUAL)
        }

        fun startNow(context: Context, uri: Uri, options: BackupOptions) {
            val inputData = workDataOf(
                LOCATION_URI_KEY to uri.toString(),
                OPTIONS_KEY to options.asBooleanArray(),
            )
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG_CLIENT_MANUAL)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_CLIENT_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

class ServerBackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: return Result.failure()

        setForegroundSafely()

        val dependencies = context.appDependencies
        val provider = dependencies.suwayomiClientProvider
        when (val identityCheck = EnqueueBoundServerIdentity.check(inputData, provider.serverKey())) {
            is EnqueueBoundServerIdentityCheck.Matched -> Unit
            EnqueueBoundServerIdentityCheck.Missing -> {
                val message = "Server backup create job missing enqueue-bound server identity"
                logcat(LogPriority.ERROR) { message }
                notifier.showBackupError(message)
                context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
                return Result.failure()
            }
            is EnqueueBoundServerIdentityCheck.Mismatched -> {
                val message = "Server changed since backup was queued. Start the backup again."
                logcat(LogPriority.ERROR) {
                    "Server backup create job server identity mismatch " +
                        "enqueued=${identityCheck.enqueuedServerKey} current=${identityCheck.currentServerKey}"
                }
                notifier.showBackupError(message)
                context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
                return Result.failure()
            }
        }

        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let { BackupOptions.fromBooleanArray(it) }
            ?: BackupOptions()

        return try {
            val location = ServerBackupCreator(
                context = context,
                suwayomiProvider = provider,
                validator = ServerBackupFileValidator(context, dependencies.json, provider),
            ).backup(uri, options)
            notifier.showBackupComplete(UniFile.fromUri(context, location.toUri())!!)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_MANUAL)
        }

        fun startNow(context: Context, uri: Uri, options: BackupOptions) {
            val inputData = buildServerBackupCreateInputData(
                locationUri = uri.toString(),
                options = options,
                serverKey = context.appDependencies.suwayomiClientProvider.serverKey(),
            )
            val request = OneTimeWorkRequestBuilder<ServerBackupCreateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

internal fun buildServerBackupCreateInputData(
    locationUri: String,
    options: BackupOptions,
    serverKey: String,
): Data {
    return EnqueueBoundServerIdentity.put(
        Data.Builder(),
        serverKey,
    )
        .putString(LOCATION_URI_KEY, locationUri)
        .putBooleanArray(OPTIONS_KEY, options.asBooleanArray())
        .build()
}

private const val TAG_CLIENT_MANUAL = "BackupCreator:manual"
private const val TAG_MANUAL = "ServerBackupCreator:manual"

private const val LOCATION_URI_KEY = "location_uri" // String
private const val OPTIONS_KEY = "options" // BooleanArray
