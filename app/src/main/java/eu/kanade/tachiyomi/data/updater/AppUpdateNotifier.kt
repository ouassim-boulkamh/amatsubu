package eu.kanade.tachiyomi.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import eu.kanade.domain.release.model.Release
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

internal class AppUpdateNotifier(private val context: Context) {
    private val builder = context.notificationBuilder(Notifications.CHANNEL_APP_UPDATE)

    private fun NotificationCompat.Builder.show(id: Int = Notifications.ID_APP_UPDATER) = context.notify(id, build())

    fun cancel() = NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATER)

    fun promptUpdate(release: Release) {
        val downloadIntent = NotificationReceiver.downloadAppUpdatePendingBroadcast(
            context,
            release.downloadLink,
            release.version,
        )
        val releaseIntent = PendingIntent.getActivity(
            context,
            release.hashCode(),
            Intent(Intent.ACTION_VIEW, release.releaseLink.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.apply {
            setContentTitle(context.stringResource(MR.strings.update_check_notification_update_available))
            setContentText(release.version)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentIntent(downloadIntent)
            clearActions()
            addAction(
                android.R.drawable.stat_sys_download_done,
                context.stringResource(MR.strings.action_download),
                downloadIntent,
            )
            addAction(R.drawable.ic_info_24dp, context.stringResource(MR.strings.whats_new), releaseIntent)
        }.show()
    }

    fun onDownloadStarted(title: String? = null): NotificationCompat.Builder = builder.apply {
        title?.let(::setContentTitle)
        setContentText(context.stringResource(MR.strings.update_check_notification_download_in_progress))
        setSmallIcon(android.R.drawable.stat_sys_download)
        setOngoing(true)
        setProgress(0, 0, true)
        clearActions()
        addAction(
            R.drawable.ic_close_24dp,
            context.stringResource(MR.strings.action_cancel),
            NotificationReceiver.cancelDownloadAppUpdatePendingBroadcast(context),
        )
    }.also { it.show() }

    fun onProgressChange(progress: Int) = builder.apply {
        setProgress(100, progress, false)
        setOnlyAlertOnce(true)
    }.show()

    fun promptInstall(uri: Uri) = builder.apply {
        val installIntent = NotificationHandler.installApkPendingActivity(context, uri)
        setContentText(context.stringResource(MR.strings.update_check_notification_download_complete))
        setSmallIcon(android.R.drawable.stat_sys_download_done)
        setOnlyAlertOnce(false)
        setProgress(0, 0, false)
        setContentIntent(installIntent)
        setOngoing(true)
        clearActions()
        addAction(
            R.drawable.ic_system_update_alt_white_24dp,
            context.stringResource(MR.strings.action_install),
            installIntent,
        )
        addAction(
            R.drawable.ic_close_24dp,
            context.stringResource(MR.strings.action_cancel),
            NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_PROMPT),
        )
    }.show(Notifications.ID_APP_UPDATE_PROMPT)

    fun onDownloadError(url: String) = builder.apply {
        setContentText(context.stringResource(MR.strings.update_check_notification_download_error))
        setSmallIcon(R.drawable.ic_warning_white_24dp)
        setOnlyAlertOnce(false)
        setProgress(0, 0, false)
        setOngoing(false)
        clearActions()
        addAction(
            R.drawable.ic_refresh_24dp,
            context.stringResource(MR.strings.action_retry),
            NotificationReceiver.downloadAppUpdatePendingBroadcast(context, url),
        )
        addAction(
            R.drawable.ic_close_24dp,
            context.stringResource(MR.strings.action_cancel),
            NotificationReceiver.dismissNotificationPendingBroadcast(context, Notifications.ID_APP_UPDATE_ERROR),
        )
    }.show(Notifications.ID_APP_UPDATE_ERROR)
}
