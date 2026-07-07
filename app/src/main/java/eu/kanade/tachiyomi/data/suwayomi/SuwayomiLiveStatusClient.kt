package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SuwayomiLiveStatusClient(
    private val graphQlClient: SuwayomiGraphQlClient,
    private val subscriptionClient: SuwayomiGraphQlSubscriptionClient,
) {
    fun downloadStatusFlow(
        pollInterval: Duration = 2.seconds,
        maxUpdates: Int = 150,
    ): Flow<SuwayomiDownloadStatusDto> {
        return flow {
            val initialStatus = graphQlClient.getDownloadStatus()
            emit(initialStatus)
            val reconciler = SuwayomiDownloadStatusReconciler(initialStatus)

            subscriptionClient.downloadStatusChanged(maxUpdates)
                .collect { updates ->
                    val status = if (updates.omittedUpdates) {
                        reconciler.replaceWith(graphQlClient.getDownloadStatus())
                    } else {
                        reconciler.reconcile(updates)
                    }
                    emit(status)
                }
        }.catch {
            emitAll(pollDownloadStatus(pollInterval))
        }
    }

    fun libraryUpdateStatusFlow(
        pollInterval: Duration = 2.seconds,
        maxUpdates: Int = 150,
    ): Flow<SuwayomiLibraryUpdateStatusDto> {
        return channelFlow {
            var latestStatus: SuwayomiLibraryUpdateStatusDto? = null

            suspend fun sendStatus(status: SuwayomiLibraryUpdateStatusDto) {
                latestStatus = status
                send(status)
            }

            sendStatus(graphQlClient.getLibraryUpdateStatus())

            val statusReconciler = launch {
                while (isActive) {
                    delay(pollInterval)
                    runCatching { graphQlClient.getLibraryUpdateStatus() }
                        .onSuccess { status ->
                            if (status != latestStatus) {
                                sendStatus(status)
                            }
                        }
                }
            }

            subscriptionClient.libraryUpdateStatusChanged(maxUpdates)
                .collect { updates ->
                    val status = when {
                        updates.omittedUpdates -> graphQlClient.getLibraryUpdateStatus()
                        updates.initial != null -> updates.initial
                        else -> SuwayomiLibraryUpdateStatusDto(
                            categoryUpdates = updates.categoryUpdates,
                            mangaUpdates = updates.mangaUpdates,
                            jobsInfo = updates.jobsInfo,
                        )
                    }
                    sendStatus(status)
                }
            statusReconciler.cancel()
        }.catch {
            emitAll(pollLibraryUpdateStatus(pollInterval))
        }
    }

    fun syncStatusFlow(
        pollInterval: Duration = 5.seconds,
    ): Flow<SuwayomiSyncStatusDto?> {
        return flow {
            emit(graphQlClient.lastSyncStatus())
            subscriptionClient.syncStatusChanged()
                .collect { emit(it) }
        }.catch {
            emitAll(pollSyncStatus(pollInterval))
        }
    }

    private fun pollDownloadStatus(interval: Duration): Flow<SuwayomiDownloadStatusDto> = flow {
        while (true) {
            runCatching { graphQlClient.getDownloadStatus() }
                .onSuccess { emit(it) }
            delay(interval)
        }
    }

    private fun pollLibraryUpdateStatus(interval: Duration): Flow<SuwayomiLibraryUpdateStatusDto> = flow {
        while (true) {
            runCatching { graphQlClient.getLibraryUpdateStatus() }
                .onSuccess { emit(it) }
            delay(interval)
        }
    }

    private fun pollSyncStatus(interval: Duration): Flow<SuwayomiSyncStatusDto?> = flow {
        var latestStatus: SuwayomiSyncStatusDto? = null
        var hasEmitted = false
        while (true) {
            runCatching { graphQlClient.lastSyncStatus() }
                .onSuccess { status ->
                    if (!hasEmitted || status != latestStatus) {
                        latestStatus = status
                        hasEmitted = true
                        emit(status)
                    }
                }.onFailure {
                    if (!hasEmitted) {
                        hasEmitted = true
                        emit(null)
                    }
                }
            delay(interval)
        }
    }

}

internal class SuwayomiDownloadStatusReconciler(
    initialStatus: SuwayomiDownloadStatusDto,
) {
    private val queueByChapterId = initialStatus.queue.associateBy { it.chapter.id }.toMutableMap()

    fun reconcile(
        updates: SuwayomiDownloadUpdatesDto,
    ): SuwayomiDownloadStatusDto {
        return when {
            updates.omittedUpdates -> error("Omitted download updates require a full status refetch")
            updates.initial != null -> {
                queueByChapterId.clear()
                updates.initial.associateByTo(queueByChapterId) { it.chapter.id }
                updates.toDownloadStatus()
            }
            else -> {
                updates.updates.forEach { update ->
                    when (update.type) {
                        SuwayomiDownloadUpdateType.DEQUEUED,
                        SuwayomiDownloadUpdateType.FINISHED,
                        -> queueByChapterId.remove(update.download.chapter.id)
                        else -> queueByChapterId[update.download.chapter.id] = update.download
                    }
                }
                updates.toDownloadStatus()
            }
        }
    }

    fun replaceWith(status: SuwayomiDownloadStatusDto): SuwayomiDownloadStatusDto {
        queueByChapterId.clear()
        status.queue.associateByTo(queueByChapterId) { it.chapter.id }
        return status
    }

    private fun SuwayomiDownloadUpdatesDto.toDownloadStatus(): SuwayomiDownloadStatusDto {
        return SuwayomiDownloadStatusDto(
            state = state,
            queue = queueByChapterId.values.sortedBy(SuwayomiDownloadDto::position),
        )
    }
}
