package eu.kanade.tachiyomi.ui.download

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

class DownloadQueueScreenModel : ScreenModel {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val suwayomiClient = suwayomiProvider.graphQlClient

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        refresh()
        screenModelScope.launchIO {
            suwayomiProvider.liveStatusClient.downloadStatusFlow()
                .collect { status ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            downloaderState = status.state,
                            downloads = status.queue.sortedBy(SuwayomiDownloadDto::position),
                        )
                    }
                }
        }
        ServerStateSync.refreshes
            .onEach { refresh(showLoading = false) }
            .launchIn(screenModelScope)
    }

    fun refresh(showLoading: Boolean = true) {
        screenModelScope.launchIO {
            if (showLoading) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            runCatching {
                suwayomiClient.getDownloadStatus()
            }.onSuccess { status ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        downloaderState = status.state,
                        downloads = status.queue.sortedBy(SuwayomiDownloadDto::position),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi download queue" }
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: error::class.simpleName.orEmpty(),
                    )
                }
            }
        }
    }

    fun startDownloads() {
        runMutation { suwayomiClient.startDownloader() }
    }

    fun pauseDownloads() {
        runMutation { suwayomiClient.stopDownloader() }
    }

    fun clearQueue() {
        runMutation { suwayomiClient.clearDownloader() }
    }

    fun cancel(download: SuwayomiDownloadDto) {
        runMutation { suwayomiClient.dequeueChapterDownloads(listOf(download.chapter.id)) }
    }

    fun retry(download: SuwayomiDownloadDto) {
        runMutation {
            suwayomiClient.dequeueChapterDownloads(listOf(download.chapter.id))
            suwayomiClient.enqueueChapterDownloads(listOf(download.chapter.id))
            suwayomiClient.startDownloader()
        }
    }

    fun move(download: SuwayomiDownloadDto, to: Int) {
        runMutation {
            val status = suwayomiClient.reorderChapterDownload(download.chapter.id, to)
            _state.update {
                it.copy(
                    downloaderState = status.state,
                    downloads = status.queue.sortedBy(SuwayomiDownloadDto::position),
                )
            }
        }
    }

    fun sortByUploadDate(descending: Boolean) {
        sortQueue(
            sortedDownloads = if (descending) {
                state.value.downloads.sortedWith(
                    compareByDescending<SuwayomiDownloadDto> { it.chapter.uploadDate }
                        .thenBy { it.position },
                )
            } else {
                state.value.downloads.sortedWith(
                    compareBy<SuwayomiDownloadDto> { it.chapter.uploadDate }
                        .thenBy { it.position },
                )
            },
        )
    }

    fun sortByChapterNumber(descending: Boolean) {
        sortQueue(
            sortedDownloads = if (descending) {
                state.value.downloads.sortedWith(
                    compareByDescending<SuwayomiDownloadDto> { it.chapter.chapterNumber }
                        .thenBy { it.position },
                )
            } else {
                state.value.downloads.sortedWith(
                    compareBy<SuwayomiDownloadDto> { it.chapter.chapterNumber }
                        .thenBy { it.position },
                )
            },
        )
    }

    private fun sortQueue(sortedDownloads: List<SuwayomiDownloadDto>) {
        if (sortedDownloads.map { it.chapter.id } == state.value.downloads.map { it.chapter.id }) return

        runMutation {
            var status = suwayomiClient.getDownloadStatus()
            sortedDownloads.forEachIndexed { index, download ->
                status = suwayomiClient.reorderChapterDownload(download.chapter.id, index)
            }
            _state.update {
                it.copy(
                    error = null,
                    downloaderState = status.state,
                    downloads = status.queue.sortedBy(SuwayomiDownloadDto::position),
                )
            }
        }
    }

    private fun runMutation(action: suspend () -> Unit) {
        screenModelScope.launchIO {
            runCatching {
                action()
                suwayomiClient.getDownloadStatus()
            }.onSuccess { status ->
                _state.update {
                    it.copy(
                        error = null,
                        downloaderState = status.state,
                        downloads = status.queue.sortedBy(SuwayomiDownloadDto::position),
                    )
                }
                ServerStateSync.requestRefresh()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi download queue" }
                _state.update { it.copy(error = error.message ?: error::class.simpleName.orEmpty()) }
            }
        }
    }

    data class State(
        val isLoading: Boolean = false,
        val error: String? = null,
        val downloaderState: String = "STOPPED",
        val downloads: List<SuwayomiDownloadDto> = emptyList(),
    ) {
        val isDownloaderRunning: Boolean
            get() = downloaderState.equals("STARTED", ignoreCase = true)
    }
}
