package eu.kanade.tachiyomi.data.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.toTimestampString
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.util.Date

/** Keeps the process alive; App owns the single live-status observer pipeline. */
internal class ServerLiveNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionCheck: Job? = null
    private var lastSuccessfulCheckAt: Long? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ServerLiveNotificationManager.ACTION_STOP || !isEnabled()) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        return runCatching {
            startForeground(Notifications.ID_LIVE_SERVER_NOTIFICATIONS, monitorNotification(MonitorState.RECONNECTING))
            startConnectionChecks()
            logcat { "Started live Suwayomi notification monitoring" }
            START_NOT_STICKY
        }.getOrElse { error ->
            logcat(LogPriority.ERROR, error) { "Failed to enter live Suwayomi notification foreground mode" }
            ServerLiveNotificationManager.stop(this)
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectionCheck?.cancel()
        cancelNotification(Notifications.ID_LIVE_SERVER_NOTIFICATIONS)
        super.onDestroy()
    }

    private fun isEnabled() = appDependencies.suwayomiClientProvider.preferences.liveServerNotifications.get()

    private fun stopMonitoring() {
        connectionCheck?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelNotification(Notifications.ID_LIVE_SERVER_NOTIFICATIONS)
        stopSelf()
    }

    private fun startConnectionChecks() {
        if (connectionCheck?.isActive == true) return
        connectionCheck = serviceScope.launch {
            while (isActive && isEnabled()) {
                updateNotification(MonitorState.RECONNECTING)
                val state = runCatching {
                    appDependencies.suwayomiClientProvider.graphQlClient.getLibraryUpdateStatus()
                }.fold(
                    onSuccess = {
                        lastSuccessfulCheckAt = System.currentTimeMillis()
                        ServerNotificationSyncJob.scheduleHealthReconciliation(this@ServerLiveNotificationService)
                        MonitorState.CONNECTED
                    },
                    onFailure = { error ->
                        logcat(LogPriority.WARN, error) { "Live Suwayomi monitor connection check failed" }
                        MonitorState.UNAVAILABLE
                    },
                )
                updateNotification(state)
                delay(CONNECTION_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(state: MonitorState) {
        notify(
            Notifications.ID_LIVE_SERVER_NOTIFICATIONS,
            monitorNotification(state),
        )
    }

    private fun monitorNotification(state: MonitorState): Notification {
        val stopIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_STOP,
            Intent(this, NotificationReceiver::class.java).setAction(
                NotificationReceiver.ACTION_STOP_LIVE_SERVER_NOTIFICATIONS,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showServerAddress =
            appDependencies.suwayomiClientProvider.preferences.showServerAddressInLiveNotification.get()
        val titleResource = when (state) {
            MonitorState.CONNECTED -> if (showServerAddress) {
                MR.strings.live_server_notifications_connected_to_server
            } else {
                MR.strings.live_server_notifications_connected
            }
            MonitorState.RECONNECTING -> if (showServerAddress) {
                MR.strings.live_server_notifications_reconnecting_to_server
            } else {
                MR.strings.live_server_notifications_reconnecting
            }
            MonitorState.UNAVAILABLE -> if (showServerAddress) {
                MR.strings.live_server_notifications_server_unavailable_at
            } else {
                MR.strings.live_server_notifications_server_unavailable
            }
        }
        val titleArguments = if (showServerAddress) {
            arrayOf(
                runCatching {
                    appDependencies.suwayomiClientProvider.preferences.notificationServerAddress()
                }.getOrDefault("127.0.0.1:4567"),
            )
        } else {
            emptyArray()
        }
        return notificationBuilder(Notifications.CHANNEL_LIVE_SERVER_NOTIFICATIONS) {
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setContentTitle(stringResource(titleResource, *titleArguments))
            setContentIntent(openAppIntent())
            lastSuccessfulCheckAt?.let {
                setContentText(
                    stringResource(MR.strings.live_server_notifications_last_checked, Date(it).toTimestampString()),
                )
            }
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(R.drawable.ic_close_24dp, stringResource(MR.strings.live_server_notifications_stop), stopIntent)
        }.build()
    }

    private fun openAppIntent(): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_STOP = 8_010
        const val REQUEST_OPEN_APP = 8_011
        const val CONNECTION_CHECK_INTERVAL_MS = 60_000L
    }

    private enum class MonitorState {
        CONNECTED,
        RECONNECTING,
        UNAVAILABLE,
    }
}
