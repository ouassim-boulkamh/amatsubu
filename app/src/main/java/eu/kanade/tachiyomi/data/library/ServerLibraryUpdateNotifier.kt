package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.notification.ServerNotificationCheckpointStore
import eu.kanade.tachiyomi.data.notification.ServerNotificationReconciler
import eu.kanade.tachiyomi.data.notification.ServerNotificationRenderer
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

internal class ServerLibraryUpdateNotifier(
    private val context: Context,
    private val clientProvider: SuwayomiClientProvider,
    private val renderer: ServerNotificationRenderer,
    private val checkpoints: ServerNotificationCheckpointStore,
    private val reconciler: ServerNotificationReconciler = ServerNotificationReconciler(renderer, checkpoints),
) {
    private val initialization = OnceOnlyInitialization()

    fun init(scope: CoroutineScope) {
        if (!initialization.tryAcquire()) {
            logcat(LogPriority.WARN) { "Ignored duplicate Suwayomi live-status notifier initialization" }
            return
        }
        val serverIdentityChanges = combine(
            clientProvider.preferences.serverUrl.changes(),
            clientProvider.preferences.useServerPort.changes(),
            clientProvider.preferences.serverPort.changes(),
        ) { _, _, _ -> clientProvider.serverIdentity() }
            .distinctUntilChanged()

        combine(
            clientProvider.preferences.liveServerNotifications.changes(),
            serverIdentityChanges,
        ) { monitorMode, serverIdentity -> monitorMode to serverIdentity }
            .distinctUntilChanged()
            .flatMapLatest { (monitorMode, serverIdentity) ->
                logcat(LogPriority.INFO) {
                    "Starting Suwayomi library status observer (monitorMode=$monitorMode, server=${serverIdentity.baseUrl})"
                }
                clientProvider.liveStatusClient.libraryUpdateStatusFlow(monitorMode = monitorMode)
                    .map { status -> serverIdentity.notificationCheckpointKey to status }
            }
            .onEach { (serverIdentity, status) ->
                logcat(LogPriority.INFO) {
                    "Observed Suwayomi library status (running=${status.jobsInfo.isRunning}, " +
                        "finished=${status.jobsInfo.finishedJobs}, total=${status.jobsInfo.totalJobs})"
                }
                val result = reconciler.reconcileLibraryUpdate(
                    client = clientProvider.graphQlClient,
                    serverIdentity = serverIdentity,
                    status = status,
                )
                if (result.started) {
                    ServerNotificationSyncJob.schedulePromptReconciliation(context)
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to observe Suwayomi library update notifications" }
                renderer.cancelLibraryProgress()
            }
            .launchIn(scope)

        serverIdentityChanges
            .flatMapLatest { serverIdentity ->
                clientProvider.liveStatusClient.downloadStatusFlow()
                    .map { status -> serverIdentity.notificationCheckpointKey to status }
            }
            .onEach { (serverIdentity, status) ->
                reconciler.reconcileDownloadStatus(
                    serverIdentity = serverIdentity,
                    status = status,
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to observe Suwayomi download notifications" }
                renderer.cancelDownloadNotifications()
            }
            .launchIn(scope)

        serverIdentityChanges
            .flatMapLatest { serverIdentity ->
                clientProvider.liveStatusClient.syncStatusFlow()
                    .map { status -> serverIdentity.notificationCheckpointKey to status }
            }
            .onEach { (serverIdentity, status) ->
                reconciler.reconcileSyncYomiStatus(
                    serverIdentity = serverIdentity,
                    status = status,
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to observe Suwayomi SyncYomi notifications" }
                renderer.cancelSyncYomiProgress()
            }
            .launchIn(scope)
    }
}

internal class OnceOnlyInitialization {
    private val initialized = AtomicBoolean(false)

    fun tryAcquire(): Boolean = initialized.compareAndSet(false, true)
}
