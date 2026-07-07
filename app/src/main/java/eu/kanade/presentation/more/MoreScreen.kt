package eu.kanade.presentation.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.ServerSyncState
import eu.kanade.tachiyomi.ui.more.SyncYomiState
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    serverSyncStateProvider: () -> ServerSyncState,
    syncYomiStateProvider: () -> SyncYomiState,
    snackbarHostState: SnackbarHostState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickSyncServerState: () -> Unit,
    onClickStopLibraryUpdate: () -> Unit,
    onClickStartSyncYomi: () -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            item {
                LogoHeader(
                    iconPadding = PaddingValues(vertical = 32.dp),
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.label_downloaded_only),
                    subtitle = stringResource(MR.strings.downloaded_only_summary),
                    icon = Icons.Outlined.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                )
            }

            item { HorizontalDivider() }

            item {
                val serverSyncState = serverSyncStateProvider()
                TextPreferenceWidget(
                    title = stringResource(
                        if (serverSyncState is ServerSyncState.LibraryUpdating) {
                            MR.strings.action_stop_library_update
                        } else {
                            MR.strings.action_sync_server_state
                        },
                    ),
                    subtitle = when (serverSyncState) {
                        ServerSyncState.Idle -> stringResource(MR.strings.sync_server_state_summary)
                        ServerSyncState.Syncing -> stringResource(MR.strings.sync_server_state_syncing)
                        is ServerSyncState.LibraryUpdating -> {
                            if (serverSyncState.totalJobs > 0) {
                                stringResource(
                                    MR.strings.library_update_progress,
                                    serverSyncState.finishedJobs,
                                    serverSyncState.totalJobs,
                                )
                            } else {
                                stringResource(MR.strings.updating_library)
                            }
                        }
                        is ServerSyncState.Success -> stringResource(MR.strings.sync_server_state_synced_just_now)
                        is ServerSyncState.Error ->
                            serverSyncState.message
                                ?: stringResource(MR.strings.sync_server_state_failed)
                    },
                    icon = if (serverSyncState is ServerSyncState.LibraryUpdating) {
                        Icons.Outlined.Stop
                    } else {
                        Icons.Outlined.Sync
                    },
                    onPreferenceClick = if (serverSyncState is ServerSyncState.Syncing) {
                        null
                    } else if (serverSyncState is ServerSyncState.LibraryUpdating) {
                        onClickStopLibraryUpdate
                    } else {
                        onClickSyncServerState
                    },
                )
            }
            item {
                val syncYomiState = syncYomiStateProvider()
                TextPreferenceWidget(
                    title = stringResource(MR.strings.action_syncyomi),
                    subtitle = syncYomiSubtitle(syncYomiState),
                    icon = Icons.Outlined.Sync,
                    onPreferenceClick = if (syncYomiState.isRunning) {
                        null
                    } else {
                        onClickStartSyncYomi
                    },
                )
            }
            item {
                val downloadQueueState = downloadQueueStateProvider()
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_download_queue),
                    subtitle = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(MR.strings.paused)
                            } else {
                                "${stringResource(MR.strings.paused)} • ${
                                    pluralStringResource(
                                        MR.plurals.download_queue_summary,
                                        count = pending,
                                        pending,
                                    )
                                }"
                            }
                        }
                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
                        }
                    },
                    icon = Icons.Outlined.GetApp,
                    onPreferenceClick = onClickDownloadQueue,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.categories),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onPreferenceClick = onClickCategories,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_stats),
                    icon = Icons.Outlined.QueryStats,
                    onPreferenceClick = onClickStats,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_data_storage),
                    icon = Icons.Outlined.Storage,
                    onPreferenceClick = onClickDataAndStorage,
                )
            }

            item { HorizontalDivider() }

            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_settings),
                    icon = Icons.Outlined.Settings,
                    onPreferenceClick = onClickSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.pref_category_about),
                    icon = Icons.Outlined.Info,
                    onPreferenceClick = onClickAbout,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_help),
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    onPreferenceClick = { uriHandler.openUri(Constants.URL_HELP) },
                )
            }
        }
    }
}

@Composable
private fun syncYomiSubtitle(state: SyncYomiState): String {
    return when (state) {
        SyncYomiState.Loading -> stringResource(MR.strings.loading)
        SyncYomiState.Unavailable -> stringResource(MR.strings.syncyomi_no_status)
        SyncYomiState.Starting -> stringResource(MR.strings.syncyomi_starting)
        is SyncYomiState.Error -> state.message ?: stringResource(MR.strings.syncyomi_failed)
        is SyncYomiState.Status -> {
            val label = when (state.status.state.uppercase()) {
                "STARTED" -> stringResource(MR.strings.syncyomi_state_started)
                "CREATING_BACKUP" -> stringResource(MR.strings.syncyomi_state_creating_backup)
                "DOWNLOADING" -> stringResource(MR.strings.syncyomi_state_downloading)
                "MERGING" -> stringResource(MR.strings.syncyomi_state_merging)
                "UPLOADING" -> stringResource(MR.strings.syncyomi_state_uploading)
                "RESTORING" -> stringResource(MR.strings.syncyomi_state_restoring)
                "SUCCESS" -> stringResource(MR.strings.syncyomi_state_success)
                "ERROR" -> stringResource(MR.strings.syncyomi_state_error)
                else -> state.status.state
            }
            val details = listOfNotNull(
                state.status.backupRestoreId
                    ?.takeUnless(String::isBlank)
                    ?.let { stringResource(MR.strings.syncyomi_backup_restore_id, it) },
                state.status.errorMessage
                    ?.takeUnless(String::isBlank),
            )
            if (details.isEmpty()) {
                label
            } else {
                "$label\n${details.joinToString("\n")}"
            }
        }
    }
}
