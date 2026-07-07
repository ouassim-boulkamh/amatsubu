package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterDownloadAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
    SAVE_DEVICE,
    REMOVE_DEVICE,
    REFRESH_DEVICE,
}

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    deviceCopyStateProvider: () -> eu.kanade.tachiyomi.ui.manga.DeviceCopyState = {
        eu.kanade.tachiyomi.ui.manga.DeviceCopyState.NONE
    },
    serverActionsEnabled: Boolean = true,
    deviceSaveEnabled: Boolean = false,
    showDeviceCopyActions: Boolean = false,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val downloadState = downloadStateProvider()) {
        Download.State.NOT_DOWNLOADED -> NotDownloadedIndicator(
            enabled = enabled,
            modifier = modifier,
            deviceCopyStateProvider = deviceCopyStateProvider,
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
        Download.State.QUEUE, Download.State.DOWNLOADING -> DownloadingIndicator(
            enabled = enabled,
            modifier = modifier,
            downloadState = downloadState,
            downloadProgressProvider = downloadProgressProvider,
            deviceCopyStateProvider = deviceCopyStateProvider,
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
        Download.State.DOWNLOADED -> DownloadedIndicator(
            enabled = enabled,
            modifier = modifier,
            deviceCopyStateProvider = deviceCopyStateProvider,
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
        Download.State.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            deviceCopyStateProvider = deviceCopyStateProvider,
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotDownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    deviceCopyStateProvider: () -> eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    serverActionsEnabled: Boolean,
    deviceSaveEnabled: Boolean,
    showDeviceCopyActions: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START_NOW) },
                onClick = { isMenuExpanded = true },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download_chapter_24dp),
            contentDescription = stringResource(MR.strings.action_download_to_server),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChapterDownloadDropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false },
            downloadState = Download.State.NOT_DOWNLOADED,
            deviceCopyState = deviceCopyStateProvider(),
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
    }
}

@Composable
private fun DownloadingIndicator(
    enabled: Boolean,
    downloadState: Download.State,
    downloadProgressProvider: () -> Int,
    deviceCopyStateProvider: () -> eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    serverActionsEnabled: Boolean,
    deviceSaveEnabled: Boolean,
    showDeviceCopyActions: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val arrowColor: Color
        val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
        val downloadProgress = downloadProgressProvider()
        val indeterminate = downloadState == Download.State.QUEUE ||
            (downloadState == Download.State.DOWNLOADING && downloadProgress == 0)
        if (indeterminate) {
            arrowColor = strokeColor
            CircularProgressIndicator(
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            val animatedProgress by animateFloatAsState(
                targetValue = downloadProgress / 100f,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "progress",
            )
            arrowColor = if (animatedProgress < 0.5f) {
                strokeColor
            } else {
                MaterialTheme.colorScheme.background
            }
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorSize / 2,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_download_to_server_now)) },
                enabled = serverActionsEnabled,
                onClick = {
                    onClick(ChapterDownloadAction.START_NOW)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(ChapterDownloadAction.CANCEL)
                    isMenuExpanded = false
                },
            )
            if (showDeviceCopyActions) {
                DeviceCopyDropdownItems(
                    deviceCopyState = deviceCopyStateProvider(),
                    deviceSaveEnabled = deviceSaveEnabled,
                    onClick = onClick,
                    onDismissRequest = { isMenuExpanded = false },
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ArrowDownward,
            contentDescription = null,
            modifier = ArrowModifier,
            tint = arrowColor,
        )
    }
}

@Composable
private fun DownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    deviceCopyStateProvider: () -> eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    serverActionsEnabled: Boolean,
    deviceSaveEnabled: Boolean,
    showDeviceCopyActions: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            ServerDownloadedDropdownItems(
                serverActionsEnabled = serverActionsEnabled,
                onClick = onClick,
                onDismissRequest = { isMenuExpanded = false },
            )
            if (showDeviceCopyActions) {
                DeviceCopyDropdownItems(
                    deviceCopyState = deviceCopyStateProvider(),
                    deviceSaveEnabled = deviceSaveEnabled,
                    onClick = onClick,
                    onDismissRequest = { isMenuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    deviceCopyStateProvider: () -> eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    serverActionsEnabled: Boolean,
    deviceSaveEnabled: Boolean,
    showDeviceCopyActions: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(MR.strings.chapter_error),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
        ChapterDownloadDropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false },
            downloadState = Download.State.ERROR,
            deviceCopyState = deviceCopyStateProvider(),
            serverActionsEnabled = serverActionsEnabled,
            deviceSaveEnabled = deviceSaveEnabled,
            showDeviceCopyActions = showDeviceCopyActions,
            onClick = onClick,
        )
    }
}

@Composable
private fun ChapterDownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    downloadState: Download.State,
    deviceCopyState: eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    serverActionsEnabled: Boolean,
    deviceSaveEnabled: Boolean,
    showDeviceCopyActions: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.action_download_to_server)) },
            enabled = serverActionsEnabled,
            onClick = {
                onClick(ChapterDownloadAction.START)
                onDismissRequest()
            },
        )
        if (!serverActionsEnabled) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.server_offline_actions_disabled)) },
                enabled = false,
                onClick = {},
            )
        }
        if (downloadState == Download.State.QUEUE || downloadState == Download.State.DOWNLOADING) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(ChapterDownloadAction.CANCEL)
                    onDismissRequest()
                },
            )
        }
        if (showDeviceCopyActions) {
            DeviceCopyDropdownItems(
                deviceCopyState = deviceCopyState,
                deviceSaveEnabled = deviceSaveEnabled,
                onClick = onClick,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun ServerDownloadedDropdownItems(
    serverActionsEnabled: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = stringResource(MR.strings.action_remove_server_download)) },
        enabled = serverActionsEnabled,
        onClick = {
            onClick(ChapterDownloadAction.DELETE)
            onDismissRequest()
        },
    )
    if (!serverActionsEnabled) {
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.server_offline_actions_disabled)) },
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
private fun DeviceCopyDropdownItems(
    deviceCopyState: eu.kanade.tachiyomi.ui.manga.DeviceCopyState,
    deviceSaveEnabled: Boolean,
    onClick: (ChapterDownloadAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val hasDeviceCopy = deviceCopyState != eu.kanade.tachiyomi.ui.manga.DeviceCopyState.NONE
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(
                    if (hasDeviceCopy) MR.strings.action_refresh_device_copy else MR.strings.action_save_to_device,
                ),
            )
        },
        enabled = deviceSaveEnabled,
        onClick = {
            onClick(if (hasDeviceCopy) ChapterDownloadAction.REFRESH_DEVICE else ChapterDownloadAction.SAVE_DEVICE)
            onDismissRequest()
        },
    )
    if (!deviceSaveEnabled) {
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.save_device_copy_offline_disabled)) },
            enabled = false,
            onClick = {},
        )
    }
    if (hasDeviceCopy) {
        DropdownMenuItem(
            text = { Text(text = stringResource(MR.strings.action_remove_device_copy)) },
            onClick = {
                onClick(ChapterDownloadAction.REMOVE_DEVICE)
                onDismissRequest()
            },
        )
    }
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val IndicatorSize = 26.dp
private val IndicatorPadding = 2.dp

// To match composable parameter name when used later
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)
