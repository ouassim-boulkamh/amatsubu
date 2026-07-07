package eu.kanade.tachiyomi.ui.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyOrphanManager
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyStore
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.DateFormat
import java.util.Date

object ClientDeviceCopiesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ClientDeviceCopiesScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingRemoval by remember { mutableStateOf<ClientDeviceChapterCopy?>(null) }

        pendingRemoval?.let { copy ->
            AlertDialog(
                onDismissRequest = { pendingRemoval = null },
                title = { Text(text = "Remove device copy?") },
                text = {
                    Text(
                        text = "This deletes the local files for \"${copy.chapterTitle}\". " +
                            "Suwayomi server downloads are not changed.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRemoval = null
                            screenModel.remove(copy)
                        },
                    ) {
                        Text(text = "Remove device copy")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemoval = null }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = "Device copies",
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_retry),
                                    icon = Icons.Outlined.Refresh,
                                    onClick = screenModel::refresh,
                                ),
                            ),
                        )
                    },
                    scrollBehavior = it,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when (val value = state) {
                ClientDeviceCopiesScreenModel.State.Loading -> {
                    EmptyScreen(
                        message = stringResource(MR.strings.loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                is ClientDeviceCopiesScreenModel.State.Error -> {
                    EmptyScreen(
                        message = value.message,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                is ClientDeviceCopiesScreenModel.State.Ready -> {
                    if (value.orphanedCopies.isEmpty()) {
                        EmptyScreen(
                            message = if (value.reconciliationSkipped) {
                                "No orphaned device copies found. Orphan detection needs a cached or reachable server library."
                            } else {
                                "No orphaned device copies found."
                            },
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.padding(contentPadding)) {
                            items(
                                items = value.orphanedCopies,
                                key = { copy -> "${copy.serverKey}-${copy.mangaId}-${copy.chapterId}" },
                            ) { copy ->
                                ClientDeviceCopyItem(
                                    copy = copy,
                                    onRemove = { pendingRemoval = copy },
                                )
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            screenModel.events.receiveAsFlow().collect { event ->
                val message = when (event) {
                    ClientDeviceCopiesScreenModel.Event.Removed -> "Device copy removed"
                    ClientDeviceCopiesScreenModel.Event.RemoveFailed -> "Failed to remove device copy"
                    ClientDeviceCopiesScreenModel.Event.RefreshFailed -> "Failed to refresh device copies"
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}

@Composable
private fun ClientDeviceCopyItem(
    copy: ClientDeviceChapterCopy,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = copy.chapterTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = copy.mangaTitle ?: "Manga ${copy.mangaId}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    Text(
                        text = "Orphaned" + copy.orphanedAt?.let { " ${formatDate(it)}" }.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = "${copy.downloadedPageCount}/${copy.expectedPageCount} pages on device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove device copy",
                )
            }
        },
    )
}

private class ClientDeviceCopiesScreenModel(
    private val store: ClientDeviceChapterCopyStore = Injekt.get(),
) : StateScreenModel<ClientDeviceCopiesScreenModel.State>(State.Loading) {

    private val provider = SuwayomiClientProvider()
    private val orphanManager = ClientDeviceChapterCopyOrphanManager(
        store = store,
        client = provider.graphQlClient,
    )
    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Channel<Event> = _events

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launchIO {
            try {
                val result = orphanManager.reconcile(provider.serverKey())
                mutableState.value = State.Ready(
                    orphanedCopies = store.getOrphanedCopies(provider.serverKey()),
                    reconciliationSkipped = result.skipped,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to refresh client device copies" }
                mutableState.value = State.Error(error.message ?: "Failed to refresh device copies")
                _events.send(Event.RefreshFailed)
            }
        }
    }

    fun remove(copy: ClientDeviceChapterCopy) {
        screenModelScope.launchIO {
            try {
                withIOContext {
                    copy.storagePath
                        ?.let(::File)
                        ?.takeIf(File::exists)
                        ?.deleteRecursively()
                    store.deleteCopy(copy.serverKey, copy.mangaId, copy.chapterId)
                }
                mutableState.value = State.Ready(
                    orphanedCopies = store.getOrphanedCopies(provider.serverKey()),
                    reconciliationSkipped = (mutableState.value as? State.Ready)?.reconciliationSkipped ?: false,
                )
                _events.send(Event.Removed)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to remove client device copy" }
                _events.send(Event.RemoveFailed)
            }
        }
    }

    sealed interface State {
        data object Loading : State
        data class Ready(
            val orphanedCopies: List<ClientDeviceChapterCopy>,
            val reconciliationSkipped: Boolean,
        ) : State
        data class Error(val message: String) : State
    }

    sealed interface Event {
        data object Removed : Event
        data object RemoveFailed : Event
        data object RefreshFailed : Event
    }
}

private fun formatDate(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
