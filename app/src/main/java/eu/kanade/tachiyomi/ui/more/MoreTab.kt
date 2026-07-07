package eu.kanade.tachiyomi.ui.more

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource as composeStringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object MoreTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                index = 4u,
                title = composeStringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsState()
        val serverSyncState by screenModel.serverSyncState.collectAsState()
        val syncYomiState by screenModel.syncYomiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        MoreScreen(
            downloadQueueStateProvider = { downloadQueueState },
            serverSyncStateProvider = { serverSyncState },
            syncYomiStateProvider = { syncYomiState },
            snackbarHostState = snackbarHostState,
            downloadedOnly = screenModel.downloadedOnly,
            onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
            incognitoMode = screenModel.incognitoMode,
            onIncognitoModeChange = { screenModel.incognitoMode = it },
            onClickSyncServerState = screenModel::syncServerState,
            onClickStopLibraryUpdate = screenModel::stopLibraryUpdate,
            onClickStartSyncYomi = screenModel::startSyncYomi,
            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onClickCategories = { navigator.push(CategoryScreen()) },
            onClickStats = { navigator.push(StatsScreen()) },
            onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
            onClickSettings = { navigator.push(SettingsScreen()) },
            onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                val message = when (event) {
                    MoreScreenModel.Event.ServerSyncSuccess -> MR.strings.sync_server_state_success
                    MoreScreenModel.Event.ServerSyncPartial -> MR.strings.sync_server_state_partial
                    MoreScreenModel.Event.ServerSyncFailed -> MR.strings.sync_server_state_failed
                    MoreScreenModel.Event.SyncYomiStarted -> MR.strings.syncyomi_started
                    MoreScreenModel.Event.SyncYomiFailed -> MR.strings.syncyomi_failed
                }
                snackbarHostState.showSnackbar(context.stringResource(message))
            }
        }
    }
}

private class MoreScreenModel(
    preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ScreenModel {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val suwayomiClient = suwayomiProvider.graphQlClient

    var downloadedOnly by preferences.downloadedOnly.asState(screenModelScope)
    var incognitoMode by preferences.incognitoMode.asState(screenModelScope)

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    private val _serverSyncState = MutableStateFlow<ServerSyncState>(ServerSyncState.Idle)
    val serverSyncState: StateFlow<ServerSyncState> = _serverSyncState.asStateFlow()

    private val _syncYomiState = MutableStateFlow<SyncYomiState>(SyncYomiState.Loading)
    val syncYomiState: StateFlow<SyncYomiState> = _syncYomiState.asStateFlow()

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            suwayomiProvider.liveStatusClient.downloadStatusFlow()
                .collect { status ->
                    updateDownloadQueueState(
                        queueSize = status.queue.size,
                        downloaderState = status.state,
                    )
                }
        }
        screenModelScope.launchIO {
            suwayomiProvider.liveStatusClient.libraryUpdateStatusFlow()
                .collect { status ->
                    if (status.jobsInfo.isRunning) {
                        _serverSyncState.value = ServerSyncState.LibraryUpdating(
                            finishedJobs = status.jobsInfo.finishedJobs,
                            totalJobs = status.jobsInfo.totalJobs,
                        )
                    } else if (_serverSyncState.value is ServerSyncState.LibraryUpdating) {
                        _serverSyncState.value = ServerSyncState.Success(System.currentTimeMillis())
                    }
                }
        }
        screenModelScope.launchIO {
            suwayomiProvider.liveStatusClient.syncStatusFlow()
                .collect { status ->
                    val current = _syncYomiState.value
                    if (current is SyncYomiState.Starting && status == null) return@collect
                    _syncYomiState.value = if (status == null) {
                        SyncYomiState.Unavailable
                    } else {
                        SyncYomiState.Status(status)
                    }
                }
        }
    }

    fun syncServerState() {
        if (_serverSyncState.value is ServerSyncState.Syncing) return
        if (_serverSyncState.value is ServerSyncState.LibraryUpdating) return

        screenModelScope.launchIO {
            _serverSyncState.value = ServerSyncState.Syncing

            val connectionResult = runCatching {
                suwayomiClient.testConnection()
            }
            if (connectionResult.isFailure) {
                val error = connectionResult.exceptionOrNull()
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to connect to Suwayomi before server state sync" }
                _serverSyncState.value = ServerSyncState.Error(error?.message)
                _events.send(Event.ServerSyncFailed)
                return@launchIO
            }

            refreshServerDownloadQueueState()
            ServerStateSync.requestRefresh()

            val libraryUpdateResult = runCatching {
                suwayomiClient.updateLibraryMangas()
            }
            val updateStarted = libraryUpdateResult.getOrElse { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to trigger Suwayomi library update during server state sync" }
                null
            }

            when (updateStarted) {
                true -> {
                    libraryPreferences.lastUpdatedTimestamp.set(System.currentTimeMillis())
                    _serverSyncState.value = ServerSyncState.LibraryUpdating()
                    _events.send(Event.ServerSyncSuccess)
                }
                false -> {
                    _serverSyncState.value = ServerSyncState.Success(System.currentTimeMillis())
                    _events.send(Event.ServerSyncPartial)
                }
                null -> {
                    _serverSyncState.value = ServerSyncState.Success(System.currentTimeMillis())
                    _events.send(Event.ServerSyncPartial)
                }
            }
        }
    }

    fun stopLibraryUpdate() {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.stopLibraryUpdate()
            }.onSuccess {
                ServerStateSync.requestRefresh()
                _serverSyncState.value = ServerSyncState.Success(System.currentTimeMillis())
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to stop Suwayomi library update" }
                _serverSyncState.value = ServerSyncState.Error(error.message)
                _events.send(Event.ServerSyncFailed)
            }
        }
    }

    fun startSyncYomi() {
        if (_syncYomiState.value.isRunning) return

        screenModelScope.launchIO {
            _syncYomiState.value = SyncYomiState.Starting
            runCatching {
                suwayomiClient.startSync()
            }.onSuccess {
                val status = runCatching { suwayomiClient.lastSyncStatus() }.getOrNull()
                _syncYomiState.value = if (status == null) {
                    SyncYomiState.Starting
                } else {
                    SyncYomiState.Status(status)
                }
                _events.send(Event.SyncYomiStarted)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to start Suwayomi SyncYomi" }
                _syncYomiState.value = SyncYomiState.Error(error.message)
                _events.send(Event.SyncYomiFailed)
            }
        }
    }

    private suspend fun refreshServerDownloadQueueState() {
        runCatching {
            suwayomiClient.getDownloadStatus()
        }.onSuccess { status ->
            updateDownloadQueueState(
                queueSize = status.queue.size,
                downloaderState = status.state,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi download queue status" }
            _downloadQueueState.value = DownloadQueueState.Stopped
        }
    }

    private fun updateDownloadQueueState(queueSize: Int, downloaderState: String) {
        _downloadQueueState.value = when {
            queueSize == 0 -> DownloadQueueState.Stopped
            downloaderState.equals("STARTED", ignoreCase = true) -> DownloadQueueState.Downloading(queueSize)
            else -> DownloadQueueState.Paused(queueSize)
        }
    }

    sealed interface Event {
        data object ServerSyncSuccess : Event
        data object ServerSyncPartial : Event
        data object ServerSyncFailed : Event
        data object SyncYomiStarted : Event
        data object SyncYomiFailed : Event
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}

sealed interface ServerSyncState {
    data object Idle : ServerSyncState
    data object Syncing : ServerSyncState
    data class LibraryUpdating(val finishedJobs: Int = 0, val totalJobs: Int = 0) : ServerSyncState
    data class Success(val syncedAtMillis: Long) : ServerSyncState
    data class Error(val message: String? = null) : ServerSyncState
}

sealed interface SyncYomiState {
    val isRunning: Boolean
        get() = false

    data object Loading : SyncYomiState
    data object Unavailable : SyncYomiState
    data object Starting : SyncYomiState {
        override val isRunning = true
    }
    data class Status(val status: eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto) : SyncYomiState {
        override val isRunning = status.state.isSyncYomiRunningState()
    }
    data class Error(val message: String? = null) : SyncYomiState
}

private fun String.isSyncYomiRunningState(): Boolean {
    return when (uppercase()) {
        "STARTED",
        "CREATING_BACKUP",
        "DOWNLOADING",
        "MERGING",
        "UPLOADING",
        "RESTORING",
        -> true
        else -> false
    }
}
