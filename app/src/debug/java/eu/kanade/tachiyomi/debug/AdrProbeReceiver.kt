package eu.kanade.tachiyomi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.ServerBackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.buildServerBackupCreateInputData
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.ServerBackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.buildServerBackupRestoreInputData
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyWorker
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_TOKEN
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AdrProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET_SERVER_URL -> setServerUrl(context, intent)
            ACTION_SCHEDULE_NOTIFICATION_RECONCILIATION -> {
                ServerNotificationSyncJob.schedulePromptReconciliation(context)
                Log.i(TAG, "Scheduled prompt server notification reconciliation")
            }
            ACTION_TOKEN_AUTH_LOGIN_AND_TEST -> loginAndTestTokenAuth(context, intent)
            ACTION_TOKEN_AUTH_TEST_CONNECTION -> testTokenAuthenticatedConnection(context)
            ACTION_TOKEN_AUTH_LOGOUT -> logoutTokenAuth(context)
            ACTION_ENQUEUE_DELAYED_COPY_SAVE -> enqueueDelayedCopySave(context, intent)
            ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE -> enqueueDelayedServerBackupCreate(context, intent)
            ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE -> enqueueDelayedServerBackupRestore(context, intent)
            else -> Log.w(TAG, "Unknown ADR probe action=${intent.action}")
        }
    }

    private fun setServerUrl(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_SERVER_URL)?.trim().orEmpty()
        if (url.isBlank()) {
            Log.e(TAG, "Missing $EXTRA_SERVER_URL")
            return
        }

        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SERVER_URL, url.trimEnd('/'))
            }
        Log.i(TAG, "Set debug server URL to $url")
    }

    private fun loginAndTestTokenAuth(context: Context, intent: Intent) {
        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        if (username.isBlank() || password.isBlank()) {
            Log.e(TAG, "Missing token-auth credentials")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val provider = context.appDependencies.suwayomiClientProvider
                provider.preferences.authType.set(AUTH_TOKEN)
                provider.preferences.username.set(username)
                provider.preferences.password.set(password)
                provider.tokenAuth.login(username, password)
                provider.preferences.clearTokenLoginPassword()
                val authorizationHeaderPresent = provider.httpClient.newCall(
                    Request.Builder().url(provider.preferences.graphQlEndpoint()).build(),
                ).execute().use { response ->
                    response.request.header("Authorization")?.startsWith("Bearer ") == true
                }
                check(authorizationHeaderPresent) { "Token-auth HTTP client did not attach a Bearer header" }
                val connection = provider.graphQlClient.testConnection()
                Log.i(
                    TAG,
                    "Token auth login and authenticated connection passed endpoint=${connection.endpoint} " +
                        "sourceCount=${connection.sourceCount}",
                )
            } catch (error: Throwable) {
                Log.e(TAG, "Token auth login or authenticated connection failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun logoutTokenAuth(context: Context) {
        val provider = context.appDependencies.suwayomiClientProvider
        provider.tokenAuth.logout()
        provider.preferences.clearTokenLoginPassword()
        Log.i(TAG, "Token auth logout cleared the current server token")
    }

    private fun testTokenAuthenticatedConnection(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = context.appDependencies.suwayomiClientProvider.graphQlClient.testConnection()
                Log.i(
                    TAG,
                    "Token auth authenticated connection passed endpoint=${connection.endpoint} " +
                        "sourceCount=${connection.sourceCount}",
                )
            } catch (error: Throwable) {
                Log.e(TAG, "Token auth authenticated connection failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun enqueueDelayedCopySave(context: Context, intent: Intent) {
        val chapterId = intent.getIntExtra(EXTRA_CHAPTER_ID, -1)
        if (chapterId < 0) {
            Log.e(TAG, "Missing $EXTRA_CHAPTER_ID")
            return
        }

        val delaySeconds = intent.getLongExtra(EXTRA_DELAY_SECONDS, 10L).coerceAtLeast(0L)
        val serverKey = context.appDependencies.suwayomiClientProvider.serverKey()
        val request = OneTimeWorkRequestBuilder<ClientDeviceChapterCopyWorker>()
            .addTag(TAG_CLIENT_DEVICE_COPY)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(
                ClientDeviceChapterCopyWorker.buildSaveInputData(
                    serverKey = serverKey,
                    mangaTitle = intent.getStringExtra(EXTRA_MANGA_TITLE),
                    chapterId = chapterId,
                ),
            )
            .build()

        context.workManager.enqueueUniqueWork(
            "AdrProbeClientDeviceCopySave-$chapterId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(
            TAG,
            "Enqueued delayed client-device copy save chapterId=$chapterId " +
                "delaySeconds=$delaySeconds serverKey=$serverKey",
        )
    }

    private fun enqueueDelayedServerBackupCreate(context: Context, intent: Intent) {
        val delaySeconds = intent.getLongExtra(EXTRA_DELAY_SECONDS, 10L).coerceAtLeast(0L)
        val serverKey = context.appDependencies.suwayomiClientProvider.serverKey()
        val locationUri = intent.getStringExtra(EXTRA_LOCATION_URI)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKUP_CREATE_URI
        val request = OneTimeWorkRequestBuilder<ServerBackupCreateJob>()
            .addTag(TAG_SERVER_BACKUP_CREATE)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(
                buildServerBackupCreateInputData(
                    locationUri = locationUri.toUri().toString(),
                    options = BackupOptions(),
                    serverKey = serverKey,
                ),
            )
            .build()

        context.workManager.enqueueUniqueWork(
            UNIQUE_SERVER_BACKUP_CREATE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(
            TAG,
            "Enqueued delayed server backup create delaySeconds=$delaySeconds " +
                "serverKey=$serverKey locationUri=$locationUri",
        )
    }

    private fun enqueueDelayedServerBackupRestore(context: Context, intent: Intent) {
        val delaySeconds = intent.getLongExtra(EXTRA_DELAY_SECONDS, 10L).coerceAtLeast(0L)
        val serverKey = context.appDependencies.suwayomiClientProvider.serverKey()
        val locationUri = intent.getStringExtra(EXTRA_LOCATION_URI)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKUP_RESTORE_URI
        val request = OneTimeWorkRequestBuilder<ServerBackupRestoreJob>()
            .addTag(TAG_SERVER_BACKUP_RESTORE)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(
                buildServerBackupRestoreInputData(
                    locationUri = locationUri.toUri().toString(),
                    options = RestoreOptions(),
                    sync = false,
                    serverKey = serverKey,
                ),
            )
            .build()

        context.workManager.enqueueUniqueWork(
            UNIQUE_SERVER_BACKUP_RESTORE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(
            TAG,
            "Enqueued delayed server backup restore delaySeconds=$delaySeconds " +
                "serverKey=$serverKey locationUri=$locationUri",
        )
    }

    companion object {
        private const val TAG = "AdrProbeReceiver"
        private const val TAG_CLIENT_DEVICE_COPY = "ClientDeviceChapterCopy"
        private const val TAG_SERVER_BACKUP_CREATE = "AdrProbeServerBackupCreate"
        private const val TAG_SERVER_BACKUP_RESTORE = "AdrProbeServerBackupRestore"
        private const val UNIQUE_SERVER_BACKUP_CREATE = "AdrProbeServerBackupCreate"
        private const val UNIQUE_SERVER_BACKUP_RESTORE = "AdrProbeServerBackupRestore"
        private const val KEY_SERVER_URL = "amatsubu_server_url"
        private const val DEFAULT_BACKUP_CREATE_URI = "file:///sdcard/Download/adr-probe-server-backup-create.tachibk"
        private const val DEFAULT_BACKUP_RESTORE_URI = "file:///sdcard/Download/adr-probe-server-backup-restore.tachibk"

        const val ACTION_SET_SERVER_URL = "app.amatsubu.debug.ADR_PROBE_SET_SERVER_URL"
        const val ACTION_SCHEDULE_NOTIFICATION_RECONCILIATION =
            "app.amatsubu.debug.ADR_PROBE_SCHEDULE_NOTIFICATION_RECONCILIATION"
        const val ACTION_TOKEN_AUTH_LOGIN_AND_TEST = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_LOGIN_AND_TEST"
        const val ACTION_TOKEN_AUTH_TEST_CONNECTION = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_TEST_CONNECTION"
        const val ACTION_TOKEN_AUTH_LOGOUT = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_LOGOUT"
        const val ACTION_ENQUEUE_DELAYED_COPY_SAVE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_COPY_SAVE"
        const val ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE"
        const val ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE"

        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_MANGA_TITLE = "manga_title"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LOCATION_URI = "location_uri"
    }
}
