package eu.kanade.tachiyomi.data.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.data.suwayomi.isValidSuwayomiConnectionSettings
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

internal object ServerLiveNotificationManager {

    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        appContext.appDependencies.suwayomiClientProvider.preferences.liveServerNotifications.set(true)
        return runCatching {
            ContextCompat.startForegroundService(appContext, intent(appContext, ACTION_START))
        }.onFailure { error ->
            appContext.appDependencies.suwayomiClientProvider.preferences.liveServerNotifications.set(false)
            logcat(LogPriority.ERROR, error) { "Failed to start live Suwayomi notification monitoring" }
        }.isSuccess
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.appDependencies.suwayomiClientProvider.preferences.liveServerNotifications.set(false)
        appContext.stopService(intent(appContext, ACTION_STOP))
        NotificationManagerCompat.from(appContext).cancel(Notifications.ID_LIVE_SERVER_NOTIFICATIONS)
        logcat { "Stopped live Suwayomi notification monitoring" }
    }

    /** Starts monitoring when the user opens the app, but only if they left the feature enabled. */
    fun startIfEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val preferences = appContext.appDependencies.suwayomiClientProvider.preferences
        if (!preferences.liveServerNotifications.get()) return false
        if (!isValidSuwayomiConnectionSettings(
                serverUrl = preferences.serverUrl.get(),
                useServerPort = preferences.useServerPort.get(),
                serverPort = preferences.serverPort.get(),
                authType = preferences.authType.get(),
                timeoutSeconds = preferences.timeoutSeconds.get(),
            )
        ) {
            logcat(LogPriority.WARN) { "Did not auto-start live Suwayomi monitoring; server settings are invalid" }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            logcat(LogPriority.WARN) { "Did not auto-start live Suwayomi monitoring; notification permission is missing" }
            return false
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            logcat(LogPriority.WARN) { "Did not auto-start live Suwayomi monitoring; notifications are disabled" }
            return false
        }

        return runCatching {
            ContextCompat.startForegroundService(appContext, intent(appContext, ACTION_START))
        }.onFailure { error ->
            logcat(LogPriority.ERROR, error) { "Failed to auto-start live Suwayomi notification monitoring" }
        }.isSuccess
    }

    fun stopIntent(context: Context): Intent = intent(context, ACTION_STOP)

    private fun intent(context: Context, action: String) = Intent(context, ServerLiveNotificationService::class.java)
        .setAction(action)

    const val ACTION_START = "eu.kanade.tachiyomi.action.START_LIVE_SERVER_NOTIFICATIONS"
    const val ACTION_STOP = "eu.kanade.tachiyomi.action.STOP_LIVE_SERVER_NOTIFICATIONS"
}
