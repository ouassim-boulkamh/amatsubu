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
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.ServerLiveNotificationManager
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
            ACTION_CANCEL_NOTIFICATION_RECONCILIATION -> {
                ServerNotificationSyncJob.cancel(context)
                Log.i(TAG, "Cancelled prompt server notification reconciliation")
            }
            ACTION_TOKEN_AUTH_LOGIN_AND_TEST -> loginAndTestTokenAuth(context, intent)
            ACTION_TOKEN_AUTH_TEST_CONNECTION -> testTokenAuthenticatedConnection(context)
            ACTION_TOKEN_AUTH_LOGOUT -> logoutTokenAuth(context)
            ACTION_ENQUEUE_DELAYED_COPY_SAVE -> enqueueDelayedCopySave(context, intent)
            ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE -> enqueueDelayedServerBackupCreate(context, intent)
            ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE -> enqueueDelayedServerBackupRestore(context, intent)
            ACTION_MUTATION_REFETCH_FAILURE -> probeMutationRefetchFailure(context, intent)
            ACTION_START_LIVE_SERVER_NOTIFICATIONS -> {
                check(ServerLiveNotificationManager.start(context))
                Log.i(TAG, "Started live server notification monitoring")
            }
            ACTION_STOP_LIVE_SERVER_NOTIFICATIONS -> {
                ServerLiveNotificationManager.stop(context)
                Log.i(TAG, "Stopped live server notification monitoring")
            }
            ACTION_TRIGGER_LIVE_SERVER_NOTIFICATION_STOP -> {
                context.sendBroadcast(
                    Intent(context, NotificationReceiver::class.java)
                        .setAction(NotificationReceiver.ACTION_STOP_LIVE_SERVER_NOTIFICATIONS),
                )
                Log.i(TAG, "Triggered live server notification Stop action")
            }
            else -> Log.w(TAG, "Unknown ADR probe action=${intent.action}")
        }
    }

    private fun setServerUrl(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_SERVER_URL)?.trim().orEmpty()
        setServerUrl(context, url)
    }

    private fun setServerUrl(context: Context, url: String) {
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

    private fun probeMutationRefetchFailure(context: Context, intent: Intent) {
        val mangaId = intent.getIntExtra(EXTRA_MANGA_ID, -1)
        val originalInLibrary = intent.getBooleanExtra(EXTRA_ORIGINAL_IN_LIBRARY, false)
        val primaryServerUrl = intent.getStringExtra(EXTRA_PRIMARY_SERVER_URL)?.trim().orEmpty()
        val unavailableServerUrl = intent.getStringExtra(EXTRA_UNAVAILABLE_SERVER_URL)?.trim().orEmpty()
        if (mangaId < 0 || primaryServerUrl.isBlank() || unavailableServerUrl.isBlank()) {
            Log.e(TAG, "Missing mutation/refetch probe inputs")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val provider = context.appDependencies.suwayomiClientProvider
            val changedInLibrary = !originalInLibrary
            try {
                provider.graphQlClient.updateMangaLibrary(mangaId, changedInLibrary)
                Log.i(TAG, "OC07 mutation accepted mangaId=$mangaId inLibrary=$changedInLibrary")

                setServerUrl(context, unavailableServerUrl)
                val refetchFailure = runCatching { provider.graphQlClient.getManga(mangaId) }.exceptionOrNull()
                check(refetchFailure != null) { "OC07 expected refetch failure was not observed" }
                Log.i(TAG, "OC07 refetch failed after accepted mutation mangaId=$mangaId")

                setServerUrl(context, primaryServerUrl)
                check(provider.graphQlClient.getManga(mangaId).inLibrary == changedInLibrary) {
                    "OC07 recovery refetch did not retain accepted mutation"
                }
                Log.i(TAG, "OC07 recovery refetch converged mangaId=$mangaId inLibrary=$changedInLibrary")

                provider.graphQlClient.updateMangaLibrary(mangaId, originalInLibrary)
                check(provider.graphQlClient.getManga(mangaId).inLibrary == originalInLibrary) {
                    "OC07 fixture restoration did not converge"
                }
                Log.i(TAG, "OC07 fixture restored mangaId=$mangaId inLibrary=$originalInLibrary")
            } catch (error: Throwable) {
                Log.e(TAG, "OC07 mutation/refetch failure probe failed", error)
            } finally {
                setServerUrl(context, primaryServerUrl)
                pendingResult.finish()
            }
        }
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
        const val ACTION_CANCEL_NOTIFICATION_RECONCILIATION =
            "app.amatsubu.debug.ADR_PROBE_CANCEL_NOTIFICATION_RECONCILIATION"
        const val ACTION_TOKEN_AUTH_LOGIN_AND_TEST = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_LOGIN_AND_TEST"
        const val ACTION_TOKEN_AUTH_TEST_CONNECTION = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_TEST_CONNECTION"
        const val ACTION_TOKEN_AUTH_LOGOUT = "app.amatsubu.debug.ADR_PROBE_TOKEN_AUTH_LOGOUT"
        const val ACTION_ENQUEUE_DELAYED_COPY_SAVE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_COPY_SAVE"
        const val ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_SERVER_BACKUP_CREATE"
        const val ACTION_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE =
            "app.amatsubu.debug.ADR_PROBE_ENQUEUE_DELAYED_SERVER_BACKUP_RESTORE"
        const val ACTION_MUTATION_REFETCH_FAILURE =
            "app.amatsubu.debug.ADR_PROBE_MUTATION_REFETCH_FAILURE"
        const val ACTION_START_LIVE_SERVER_NOTIFICATIONS =
            "app.amatsubu.debug.ADR_PROBE_START_LIVE_SERVER_NOTIFICATIONS"
        const val ACTION_STOP_LIVE_SERVER_NOTIFICATIONS =
            "app.amatsubu.debug.ADR_PROBE_STOP_LIVE_SERVER_NOTIFICATIONS"
        const val ACTION_TRIGGER_LIVE_SERVER_NOTIFICATION_STOP =
            "app.amatsubu.debug.ADR_PROBE_TRIGGER_LIVE_SERVER_NOTIFICATION_STOP"

        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_PRIMARY_SERVER_URL = "primary_server_url"
        const val EXTRA_UNAVAILABLE_SERVER_URL = "unavailable_server_url"
        const val EXTRA_MANGA_ID = "manga_id"
        const val EXTRA_ORIGINAL_IN_LIBRARY = "original_in_library"
        const val EXTRA_CHAPTER_ID = "chapter_id"
        const val EXTRA_MANGA_TITLE = "manga_title"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LOCATION_URI = "location_uri"
    }
}
