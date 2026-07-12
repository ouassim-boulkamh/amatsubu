package eu.kanade.tachiyomi.ui.download

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyWorker
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.di.AppDependencies
import eu.kanade.tachiyomi.di.appDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ClientDeviceCopiesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val dependencies = context.appDependencies
        val screenModel = rememberScreenModel { ClientDeviceCopiesScreenModel(dependencies) }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingRemoval by remember { mutableStateOf<DeviceCopyMangaSummary?>(null) }

        pendingRemoval?.let { summary ->
            AlertDialog(
                onDismissRequest = { pendingRemoval = null },
                title = { Text(text = "Remove device copies?") },
                text = {
                    Text(
                        text = "This deletes ${summary.totalChapterCopyCount} local chapter " +
                            "copies for \"${summary.mangaTitle}\". Suwayomi server downloads are not changed.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRemoval = null
                            screenModel.applyBulkAction(
                                context = context,
                                summary = summary,
                                target = DeviceCopyBulkActionTarget.REMOVE,
                            )
                        },
                    ) {
                        Text(text = "Remove")
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
                    ClientDeviceCopiesContent(
                        state = value,
                        onFilterChanged = screenModel::setFilter,
                        onRefresh = { summary ->
                            screenModel.applyBulkAction(context, summary, DeviceCopyBulkActionTarget.REFRESH)
                        },
                        onRetry = { summary ->
                            screenModel.applyBulkAction(context, summary, DeviceCopyBulkActionTarget.RETRY)
                        },
                        onRemoveOrphaned = { summary ->
                            screenModel.applyBulkAction(context, summary, DeviceCopyBulkActionTarget.REMOVE_ORPHANED)
                        },
                        onRemove = { summary -> pendingRemoval = summary },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            screenModel.events.receiveAsFlow().collect { event ->
                val message = when (event) {
                    ClientDeviceCopiesScreenModel.Event.ActionQueued -> "Device copy action queued"
                    ClientDeviceCopiesScreenModel.Event.ActionHadNoTargets -> "No matching device copies"
                    ClientDeviceCopiesScreenModel.Event.ActionFailed -> "Failed to queue device copy action"
                    ClientDeviceCopiesScreenModel.Event.RefreshFailed -> "Failed to refresh device copies"
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}

@Composable
private fun ClientDeviceCopiesContent(
    state: ClientDeviceCopiesScreenModel.State.Ready,
    onFilterChanged: (DeviceCopyFilter) -> Unit,
    onRefresh: (DeviceCopyMangaSummary) -> Unit,
    onRetry: (DeviceCopyMangaSummary) -> Unit,
    onRemoveOrphaned: (DeviceCopyMangaSummary) -> Unit,
    onRemove: (DeviceCopyMangaSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredSummaries = state.summaries.filter { it.matches(state.filter) }
    Column(modifier = modifier) {
        DeviceCopySummaryStrip(state)
        DeviceCopyFilterRow(
            selected = state.filter,
            onFilterChanged = onFilterChanged,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        if (filteredSummaries.isEmpty()) {
            EmptyScreen(
                message = if (state.summaries.isEmpty()) {
                    if (state.reconciliationSkipped) {
                        "No device copies found. Orphan detection needs a cached or reachable server library."
                    } else {
                        "No device copies found."
                    }
                } else {
                    "No device copies match this filter."
                },
            )
        } else {
            LazyColumn {
                items(
                    items = filteredSummaries,
                    key = { summary -> "${summary.serverKey}-${summary.mangaId}" },
                ) { summary ->
                    ClientDeviceCopySummaryItem(
                        summary = summary,
                        onRefresh = { onRefresh(summary) },
                        onRetry = { onRetry(summary) },
                        onRemoveOrphaned = { onRemoveOrphaned(summary) },
                        onRemove = { onRemove(summary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCopySummaryStrip(
    state: ClientDeviceCopiesScreenModel.State.Ready,
) {
    val mangaCount = state.summaries.size
    val chapterCount = state.summaries.sumOf { it.totalChapterCopyCount }
    val totalBytes = state.summaries.sumOf { it.totalBytes }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$mangaCount manga, $chapterCount chapter copies",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Using ${formatBytes(totalBytes)} on this device",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeviceCopyFilterRow(
    selected: DeviceCopyFilter,
    onFilterChanged: (DeviceCopyFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceCopyFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilterChanged(filter) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

@Composable
private fun ClientDeviceCopySummaryItem(
    summary: DeviceCopyMangaSummary,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRemoveOrphaned: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var expanded by remember(summary.serverKey, summary.mangaId) { mutableStateOf(false) }
    Column {
        ListItem(
            supportingContent = {
                Column {
                    Text(
                        text = summary.readinessLine(),
                        color = readinessColor(summary.readiness),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${summary.totalChapterCopyCount} chapters, ${formatBytes(summary.totalBytes)}" +
                            ", updated ${formatDate(summary.latestUpdatedAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val attention = summary.attentionLine()
                    if (attention.isNotBlank()) {
                        Text(
                            text = attention,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(text = if (expanded) "Hide chapters" else "Show chapters")
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Device copy actions",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Retry incomplete") },
                        onClick = {
                            menuExpanded = false
                            onRetry()
                        },
                    )
                    if (summary.orphanedCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Remove orphaned") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRemoveOrphaned()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove all") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            },
        ) {
            Text(
                text = summary.mangaTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            summary.copies.forEach { copy ->
                ClientDeviceCopyChapterItem(copy)
            }
        }
    }
}

@Composable
private fun ClientDeviceCopyChapterItem(copy: ClientDeviceChapterCopy) {
    ListItem(
        supportingContent = {
            Text(
                text = "${copy.deviceCopyChapterStatusLabel()} · ${copy.deviceCopyChapterProgressLabel()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = Modifier.padding(start = 16.dp),
    ) {
        Text(
            text = copy.chapterTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private class ClientDeviceCopiesScreenModel(
    private val store: ClientDeviceChapterCopyStore,
    private val provider: SuwayomiClientProvider,
) : StateScreenModel<ClientDeviceCopiesScreenModel.State>(State.Loading) {

    constructor(dependencies: AppDependencies) : this(
        store = dependencies.clientDeviceChapterCopyStore,
        provider = dependencies.suwayomiClientProvider,
    )

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
                mutableState.value = buildReadyState(
                    filter = (mutableState.value as? State.Ready)?.filter ?: DeviceCopyFilter.ALL,
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

    fun setFilter(filter: DeviceCopyFilter) {
        val ready = mutableState.value as? State.Ready ?: return
        mutableState.value = ready.copy(filter = filter)
    }

    fun applyBulkAction(
        context: Context,
        summary: DeviceCopyMangaSummary,
        target: DeviceCopyBulkActionTarget,
    ) {
        screenModelScope.launchIO {
            try {
                val targets = summary.bulkActionCopies(target)
                if (targets.isEmpty()) {
                    _events.send(Event.ActionHadNoTargets)
                    return@launchIO
                }
                targets.forEach { copy ->
                    when (target) {
                        DeviceCopyBulkActionTarget.REFRESH,
                        DeviceCopyBulkActionTarget.RETRY,
                        -> ClientDeviceChapterCopyWorker.enqueueSave(context, copy.mangaTitle, copy.chapterId)
                        DeviceCopyBulkActionTarget.REMOVE,
                        DeviceCopyBulkActionTarget.REMOVE_ORPHANED,
                        -> ClientDeviceChapterCopyWorker.enqueueRemove(context, copy.mangaId, copy.chapterId)
                    }
                }
                _events.send(Event.ActionQueued)
                refresh()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to queue client device copy action" }
                _events.send(Event.ActionFailed)
            }
        }
    }

    private suspend fun buildReadyState(
        filter: DeviceCopyFilter,
        reconciliationSkipped: Boolean,
    ): State.Ready {
        return State.Ready(
            summaries = store.getCopiesForServer(provider.serverKey()).toDeviceCopyMangaSummaries(),
            filter = filter,
            reconciliationSkipped = reconciliationSkipped,
        )
    }

    sealed interface State {
        data object Loading : State
        data class Ready(
            val summaries: List<DeviceCopyMangaSummary>,
            val filter: DeviceCopyFilter,
            val reconciliationSkipped: Boolean,
        ) : State
        data class Error(val message: String) : State
    }

    sealed interface Event {
        data object ActionQueued : Event
        data object ActionHadNoTargets : Event
        data object ActionFailed : Event
        data object RefreshFailed : Event
    }
}

private val DeviceCopyFilter.label: String
    get() = when (this) {
        DeviceCopyFilter.ALL -> "All"
        DeviceCopyFilter.READY -> "Ready"
        DeviceCopyFilter.PARTIAL -> "Partial"
        DeviceCopyFilter.NEEDS_ATTENTION -> "Not ready"
        DeviceCopyFilter.ORPHANED -> "Orphaned"
    }

private fun DeviceCopyMangaSummary.readinessLine(): String {
    return when (readiness) {
        DeviceCopyReadiness.READY -> "$completeFreshCount ready"
        DeviceCopyReadiness.PARTIAL -> "$completeFreshCount ready, ${totalChapterCopyCount - completeFreshCount} not ready"
        DeviceCopyReadiness.NEEDS_ATTENTION -> "Not ready"
        DeviceCopyReadiness.ORPHANED -> "Orphaned"
    }
}

private fun DeviceCopyMangaSummary.attentionLine(): String {
    return listOfNotNull(
        staleCount.takeIf { it > 0 }?.let { "$it stale" },
        unverifiedCount.takeIf { it > 0 }?.let { "$it unverified" },
        incompleteCount.takeIf { it > 0 }?.let { "$it incomplete" },
        failedCount.takeIf { it > 0 }?.let { "$it failed" },
        orphanedCount.takeIf { it > 0 }?.let { "$it orphaned" },
    ).joinToString(separator = ", ")
}

@Composable
private fun readinessColor(readiness: DeviceCopyReadiness) = when (readiness) {
    DeviceCopyReadiness.READY -> MaterialTheme.colorScheme.primary
    DeviceCopyReadiness.PARTIAL -> MaterialTheme.colorScheme.tertiary
    DeviceCopyReadiness.NEEDS_ATTENTION -> MaterialTheme.colorScheme.error
    DeviceCopyReadiness.ORPHANED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatDate(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
