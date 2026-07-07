package eu.kanade.tachiyomi.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.backup.restore.ServerBackupRestoreJob
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

/**
 * Global [BroadcastReceiver] that runs on UI thread
 * Pending Broadcasts should be made from here.
 * NOTE: Use local broadcasts if possible.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Dismiss notification
            ACTION_DISMISS_NOTIFICATION -> dismissNotification(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
            // Launch share activity and dismiss notification
            ACTION_SHARE_IMAGE ->
                shareImage(
                    context,
                    intent.getStringExtra(EXTRA_URI)!!.toUri(),
                )
            // Share backup file
            ACTION_SHARE_BACKUP ->
                shareFile(
                    context,
                    intent.getParcelableExtraCompat(EXTRA_URI)!!,
                    "application/x-protobuf+gzip",
                )
            ACTION_CANCEL_RESTORE -> cancelRestore(context)
            ACTION_STOP_LIBRARY_UPDATE -> runServerMutation("stop Suwayomi library update") {
                it.stopLibraryUpdate()
            }
            ACTION_START_DOWNLOADER -> runServerMutation("start Suwayomi downloader") {
                it.startDownloader()
            }
            ACTION_STOP_DOWNLOADER -> runServerMutation("stop Suwayomi downloader") {
                it.stopDownloader()
            }
            ACTION_CLEAR_DOWNLOADER -> runServerMutation("clear Suwayomi downloader") {
                it.clearDownloader()
            }
        }
    }

    /**
     * Dismiss the notification
     *
     * @param notificationId the id of the notification
     */
    private fun dismissNotification(context: Context, notificationId: Int) {
        context.cancelNotification(notificationId)
    }

    /**
     * Called to start share intent to share image
     *
     * @param context context of application
     * @param uri path of file
     */
    private fun shareImage(context: Context, uri: Uri) {
        context.startActivity(uri.toShareIntent(context))
    }

    /**
     * Called to start share intent to share backup file
     *
     * @param context context of application
     * @param path path of file
     */
    private fun shareFile(context: Context, uri: Uri, fileMimeType: String) {
        context.startActivity(uri.toShareIntent(context, fileMimeType))
    }

    /**
     * Method called when user wants to stop a backup restore job.
     *
     * @param context context of application
     */
    private fun cancelRestore(context: Context) {
        ServerBackupRestoreJob.stop(context)
    }

    private fun runServerMutation(
        actionDescription: String,
        mutation: suspend (SuwayomiGraphQlClient) -> Unit,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mutation(SuwayomiClientProvider().graphQlClient)
                ServerStateSync.requestRefresh()
            } catch (error: Exception) {
                logcat(LogPriority.ERROR, error) { "Failed to $actionDescription from notification action" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NAME = "NotificationReceiver"

        private const val ACTION_SHARE_IMAGE = "$ID.$NAME.SHARE_IMAGE"

        private const val ACTION_SHARE_BACKUP = "$ID.$NAME.SEND_BACKUP"

        private const val ACTION_CANCEL_RESTORE = "$ID.$NAME.CANCEL_RESTORE"

        private const val ACTION_DISMISS_NOTIFICATION = "$ID.$NAME.ACTION_DISMISS_NOTIFICATION"

        private const val ACTION_STOP_LIBRARY_UPDATE = "$ID.$NAME.STOP_LIBRARY_UPDATE"
        private const val ACTION_START_DOWNLOADER = "$ID.$NAME.START_DOWNLOADER"
        private const val ACTION_STOP_DOWNLOADER = "$ID.$NAME.STOP_DOWNLOADER"
        private const val ACTION_CLEAR_DOWNLOADER = "$ID.$NAME.CLEAR_DOWNLOADER"

        private const val EXTRA_URI = "$ID.$NAME.URI"
        private const val EXTRA_NOTIFICATION_ID = "$ID.$NAME.NOTIFICATION_ID"

        private const val REQUEST_STOP_LIBRARY_UPDATE = -1010
        private const val REQUEST_START_DOWNLOADER = -2010
        private const val REQUEST_STOP_DOWNLOADER = -2011
        private const val REQUEST_CLEAR_DOWNLOADER = -2012

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotificationPendingBroadcast(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DISMISS_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotification(context: Context, notificationId: Int, groupId: Int? = null) {
            /*
            Group notifications always have at least 2 notifications:
            - Group summary notification
            - Single manga notification

            If the single notification is dismissed by the system, ie by a user swipe or tapping on the notification,
            it will auto dismiss the group notification if there's no other single updates.

            When programmatically dismissing this notification, the group notification is not automatically dismissed.
             */
            val groupKey = context.notificationManager.activeNotifications.find {
                it.id == notificationId
            }?.groupKey

            if (groupId != null && groupId != 0 && !groupKey.isNullOrEmpty()) {
                val notifications = context.notificationManager.activeNotifications.filter {
                    it.groupKey == groupKey
                }

                if (notifications.size == 2) {
                    context.cancelNotification(groupId)
                    return
                }
            }

            context.cancelNotification(notificationId)
        }

        /**
         * Returns [PendingIntent] that starts a share activity
         *
         * @param context context of application
         * @param uri location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareImagePendingBroadcast(context: Context, uri: Uri): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SHARE_IMAGE
                putExtra(EXTRA_URI, uri.toString())
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that directly launches a share activity for a backup file.
         *
         * @param context context of application
         * @param uri uri of backup file
         * @return [PendingIntent]
         */
        internal fun shareBackupPendingActivity(context: Context, uri: Uri): PendingIntent {
            val intent = uri.toShareIntent(context, "application/x-protobuf+gzip").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the error log file in an external viewer
         *
         * @param context context of application
         * @param uri uri of error log file
         * @return [PendingIntent]
         */
        internal fun openErrorLogPendingActivity(context: Context, uri: Uri): PendingIntent {
            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that cancels a backup restore job.
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun cancelRestorePendingBroadcast(context: Context, notificationId: Int): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_RESTORE
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun stopLibraryUpdatePendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_STOP_LIBRARY_UPDATE
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_STOP_LIBRARY_UPDATE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun startDownloaderPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_START_DOWNLOADER
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_START_DOWNLOADER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun stopDownloaderPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_STOP_DOWNLOADER
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_STOP_DOWNLOADER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun clearDownloaderPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CLEAR_DOWNLOADER
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CLEAR_DOWNLOADER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
