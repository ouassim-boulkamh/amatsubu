package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.notification.ServerNotificationCheckpointStore
import eu.kanade.tachiyomi.data.notification.ServerNotificationReconciler
import eu.kanade.tachiyomi.data.notification.ServerNotificationRenderer
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

internal class ServerLibraryUpdateNotifier(
    private val context: Context,
    private val clientProvider: SuwayomiClientProvider = SuwayomiClientProvider(),
    private val renderer: ServerNotificationRenderer = ServerNotificationRenderer(context),
    private val checkpoints: ServerNotificationCheckpointStore = ServerNotificationCheckpointStore(),
    private val reconciler: ServerNotificationReconciler = ServerNotificationReconciler(renderer, checkpoints),
) {
    fun init(scope: CoroutineScope) {
        clientProvider.liveStatusClient.libraryUpdateStatusFlow()
            .onEach { status ->
                val result = reconciler.reconcileLibraryUpdate(
                    client = clientProvider.graphQlClient,
                    serverIdentity = clientProvider.baseUrl(),
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

        clientProvider.liveStatusClient.downloadStatusFlow()
            .onEach { status ->
                reconciler.reconcileDownloadStatus(
                    serverIdentity = clientProvider.baseUrl(),
                    status = status,
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to observe Suwayomi download notifications" }
                renderer.cancelDownloadNotifications()
            }
            .launchIn(scope)

        clientProvider.liveStatusClient.syncStatusFlow()
            .onEach { status ->
                reconciler.reconcileSyncYomiStatus(
                    serverIdentity = clientProvider.baseUrl(),
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
