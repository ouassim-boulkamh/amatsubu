package eu.kanade.tachiyomi.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NestedMenuItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.di.appDependencies
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

object DownloadQueueScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val dependencies = LocalContext.current.appDependencies
        val screenModel = rememberScreenModel { DownloadQueueScreenModel(dependencies) }
        val state by screenModel.state.collectAsState()
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            topBar = {
                AppBar(
                    titleContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(MR.strings.label_download_queue),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.downloads.isNotEmpty()) {
                                Pill(
                                    text = "${state.downloads.size}",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    },
                    navigateUp = navigator::pop,
                    actions = {
                        if (state.downloads.isNotEmpty()) {
                            var sortExpanded by remember { mutableStateOf(false) }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                            ) {
                                NestedMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_order_by_upload_date)) },
                                    children = { closeMenu ->
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_newest)) },
                                            onClick = {
                                                screenModel.sortByUploadDate(descending = true)
                                                closeMenu()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_oldest)) },
                                            onClick = {
                                                screenModel.sortByUploadDate(descending = false)
                                                closeMenu()
                                            },
                                        )
                                    },
                                )
                                NestedMenuItem(
                                    text = { Text(text = stringResource(MR.strings.action_order_by_chapter_number)) },
                                    children = { closeMenu ->
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_asc)) },
                                            onClick = {
                                                screenModel.sortByChapterNumber(descending = false)
                                                closeMenu()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = stringResource(MR.strings.action_desc)) },
                                            onClick = {
                                                screenModel.sortByChapterNumber(descending = true)
                                                closeMenu()
                                            },
                                        )
                                    },
                                )
                            }

                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_sort),
                                        icon = Icons.AutoMirrored.Outlined.Sort,
                                        onClick = { sortExpanded = true },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_retry),
                                        onClick = { screenModel.refresh() },
                                    ),
                                    AppBar.OverflowAction(
                                        title = stringResource(MR.strings.action_cancel_all),
                                        onClick = { screenModel.clearQueue() },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                SmallExtendedFloatingActionButton(
                    text = {
                        val id = if (state.isDownloaderRunning) {
                            MR.strings.action_pause
                        } else {
                            MR.strings.action_resume
                        }
                        Text(text = stringResource(id))
                    },
                    icon = {
                        val icon = if (state.isDownloaderRunning) {
                            Icons.Outlined.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        }
                        Icon(imageVector = icon, contentDescription = null)
                    },
                    onClick = {
                        if (state.isDownloaderRunning) {
                            screenModel.pauseDownloads()
                        } else {
                            screenModel.startDownloads()
                        }
                    },
                    modifier = Modifier.animateFloatingActionButton(
                        visible = state.downloads.isNotEmpty(),
                        alignment = Alignment.BottomEnd,
                    ),
                )
            },
        ) { contentPadding ->
            when {
                state.error != null -> {
                    EmptyScreen(
                        message = state.error.orEmpty(),
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                state.downloads.isEmpty() -> {
                    EmptyScreen(
                        stringRes = MR.strings.information_no_downloads,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.padding(contentPadding),
                    ) {
                        itemsIndexed(
                            items = state.downloads,
                            key = { _, item -> item.chapter.id },
                        ) { index, download ->
                            ServerDownloadItem(
                                download = download,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.downloads.lastIndex,
                                onMoveUp = { screenModel.move(download, 0) },
                                onMoveDown = { screenModel.move(download, state.downloads.lastIndex) },
                                onRetry = { screenModel.retry(download) },
                                onCancel = { screenModel.cancel(download) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDownloadItem(
    download: SuwayomiDownloadDto,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = download.chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = download.manga.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { download.progress.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = download.state,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (download.tries > 0) {
                        Text(
                            text = "Tries ${download.tries}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    enabled = canMoveUp,
                    onClick = onMoveUp,
                ) {
                    Text(text = stringResource(MR.strings.action_move_to_top))
                }
                TextButton(
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                ) {
                    Text(text = stringResource(MR.strings.action_move_to_bottom))
                }
                Row {
                    if (download.state.equals("ERROR", ignoreCase = true)) {
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(MR.strings.action_retry))
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                }
            }
        },
    )
}
