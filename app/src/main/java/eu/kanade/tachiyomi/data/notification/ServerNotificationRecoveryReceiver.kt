package eu.kanade.tachiyomi.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/** Restores durable WorkManager reconciliation only; live monitoring is always user-started. */
internal class ServerNotificationRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                ServerNotificationSyncJob.schedule(context)
                logcat(LogPriority.INFO) {
                    "Scheduled server notification reconciliation after ${intent.action}; " +
                        "live monitoring remains stopped until the user starts it"
                }
            }
        }
    }
}
