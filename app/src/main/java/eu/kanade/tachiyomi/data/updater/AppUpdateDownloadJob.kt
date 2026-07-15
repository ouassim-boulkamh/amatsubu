package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class AppUpdateDownloadJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val notifier = AppUpdateNotifier(context)

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL) ?: return Result.failure()
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: applicationContext.stringResource(MR.strings.app_name)
        setForegroundSafely()
        return try {
            withIOContext { downloadApk(title, url) }
            Result.success()
        } catch (error: CancellationException) {
            notifier.cancel()
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo() = ForegroundInfo(
        Notifications.ID_APP_UPDATER,
        notifier.onDownloadStarted().build(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )

    private suspend fun downloadApk(title: String, url: String) {
        notifier.onDownloadStarted(title)
        val progressListener = object : ProgressListener {
            var savedProgress = 0
            var lastTick = 0L
            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                if (contentLength <= 0L) return
                val progress = (100 * bytesRead.toFloat() / contentLength).toInt()
                val now = System.currentTimeMillis()
                if (progress > savedProgress && now - lastTick >= 200) {
                    savedProgress = progress
                    lastTick = now
                    notifier.onProgressChange(progress)
                }
            }
        }
        try {
            val response = applicationContext.appDependencies.networkHelper.client
                .newCachelessCallWithProgress(GET(url), progressListener).await()
            val apkFile = File(applicationContext.externalCacheDir, "update.apk")
            if (!response.isSuccessful) {
                response.close()
                error("Unsuccessful update download response")
            }
            response.body.source().saveTo(apkFile)
            notifier.cancel()
            notifier.promptInstall(apkFile.getUriCompat(applicationContext))
        } catch (error: CancellationException) {
            notifier.cancel()
            throw error
        } catch (error: Exception) {
            if (error is StreamResetException &&
                error.errorCode == ErrorCode.CANCEL
            ) {
                notifier.cancel()
            } else {
                notifier.onDownloadError(url)
            }
            throw error
        }
    }

    companion object {
        private const val TAG = "AppUpdateDownload"
        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
        const val EXTRA_DOWNLOAD_TITLE = "DOWNLOAD_TITLE"
        fun start(context: Context, url: String, title: String? = null) {
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .addTag(TAG)
                .setInputData(workDataOf(EXTRA_DOWNLOAD_URL to url, EXTRA_DOWNLOAD_TITLE to title))
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
        fun stop(context: Context) = context.workManager.cancelUniqueWork(TAG)
    }
}
