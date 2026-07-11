package eu.kanade.tachiyomi.ui.manga.track

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.ServerTrackItem
import eu.kanade.presentation.track.ServerTrackRecord
import eu.kanade.presentation.track.ServerTrackSearchResult
import eu.kanade.presentation.track.ServerTrackerPresentation
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.TrackerSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateInvalidation
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackRecordDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackSearchDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackerDto
import eu.kanade.tachiyomi.data.suwayomi.serverTrackAffectedEntities
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.ServerForegroundRefreshEffect
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ServerTrackInfoDialogScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val dependencies = context.appDependencies
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                client = dependencies.suwayomiClientProvider.graphQlClient,
            )
        }
        val state by screenModel.state.collectAsState()
        val dateFormat = remember { UiPreferences.dateFormat(dependencies.uiPreferences.dateFormat.get()) }

        DisposableEffect(lifecycleOwner, screenModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    screenModel.refresh(showLoading = false)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        ServerForegroundRefreshEffect {
            screenModel.refresh(showLoading = false)
        }

        when {
            state.loading -> LoadingScreen()
            state.error != null -> {
                val errorMessage = state.error ?: stringResource(MR.strings.unknown_error)
                EmptyScreen(message = errorMessage)
            }
            else -> {
                TrackInfoDialogHome(
                    trackItems = state.trackItems,
                    dateFormat = dateFormat,
                    onStatusClick = {
                        navigator.push(
                            ServerTrackStatusSelectorScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                                tracker = it.tracker,
                            ),
                        )
                    },
                    onChapterClick = {
                        navigator.push(
                            ServerTrackChapterSelectorScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                            ),
                        )
                    },
                    onScoreClick = {
                        navigator.push(
                            ServerTrackScoreSelectorScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                                tracker = it.tracker,
                            ),
                        )
                    },
                    onStartDateEdit = {
                        navigator.push(
                            ServerTrackDateSelectorScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                                start = true,
                            ),
                        )
                    },
                    onEndDateEdit = {
                        navigator.push(
                            ServerTrackDateSelectorScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                                start = false,
                            ),
                        )
                    },
                    onNewSearch = {
                        val track = it.track
                        val initialQuery = track?.title
                            ?: state.trackItems.firstNotNullOfOrNull { item -> item.track?.title }
                            ?: mangaTitle
                        navigator.push(
                            ServerTrackerSearchScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                initialQuery = initialQuery,
                                currentUrl = track?.remoteUrl,
                                tracker = it.tracker,
                            ),
                        )
                    },
                    onOpenInBrowser = { it.track?.remoteUrl?.takeIf(String::isNotBlank)?.let(context::openInBrowser) },
                    onRemoved = {
                        navigator.push(
                            ServerTrackerRemoveScreen(
                                mangaId = mangaId,
                                mangaTitle = mangaTitle,
                                track = it.track!!,
                                tracker = it.tracker,
                            ),
                        )
                    },
                    onCopyLink = { context.copyTrackerLink(it) },
                    onTogglePrivate = { screenModel.togglePrivate(it.track!!) },
                )
            }
        }
    }

    private fun Context.copyTrackerLink(trackItem: ServerTrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    private class Model(
        private val mangaId: Int,
        private val client: SuwayomiGraphQlClient,
    ) : StateScreenModel<Model.State>(State()) {
        init {
            refresh(showLoading = true)
            ServerStateSync.invalidations
                .filterTrackInvalidations(mangaId)
                .onEach { refresh(showLoading = false) }
                .launchIn(screenModelScope)
        }

        fun refresh(showLoading: Boolean) {
            screenModelScope.launch {
                if (showLoading) {
                    mutableState.update { it.copy(loading = true, error = null) }
                } else {
                    mutableState.update { it.copy(error = null) }
                }
                runCatching {
                    withIOContext {
                        val trackers = client.trackerList().filter { it.isLoggedIn }
                        val records = client.getTrackRecords(mangaId)
                        val displayScores = records.associate { it.id.toLong() to (it.displayScore.orEmpty()) }
                        trackers.map { tracker ->
                            ServerTrackItem(
                                track = records.find { it.trackerId == tracker.id }?.toServerTrackRecord(),
                                tracker = tracker.toPresentation(displayScores),
                            )
                        }
                    }
                }.onSuccess { trackItems ->
                    mutableState.update { it.copy(loading = false, trackItems = trackItems) }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to load Suwayomi tracking home" }
                    mutableState.update {
                        if (showLoading || it.trackItems.isEmpty()) {
                            it.copy(loading = false, error = e.message ?: "Failed to load tracking")
                        } else {
                            it
                        }
                    }
                }
            }
        }

        fun togglePrivate(track: ServerTrackRecord) {
            screenModelScope.launchNonCancellable {
                runCatching {
                    client.updateTrack(track.id.toInt(), private = !track.private)
                    ServerStateSync.requestRefresh(*serverTrackAffectedEntities(mangaId).toTypedArray())
                    refresh(showLoading = false)
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to update Suwayomi track privacy" }
                }
            }
        }

        @Immutable
        data class State(
            val loading: Boolean = true,
            val error: String? = null,
            val trackItems: List<ServerTrackItem> = emptyList(),
        )
    }
}

private fun Flow<ServerStateInvalidation>.filterTrackInvalidations(mangaId: Int): Flow<ServerStateInvalidation> {
    return filter {
        it.affectsAny(
            ServerStateEntity.Manga(mangaId),
            ServerStateEntity.Trackers(mangaId),
        )
    }
}

private data class ServerTrackerSearchScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val tracker: ServerTrackerPresentation,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                initialQuery = initialQuery,
                currentUrl = currentUrl,
                tracker = tracker,
                client = context.appDependencies.suwayomiClientProvider.graphQlClient,
                context = context.applicationContext,
            )
        }
        val state by screenModel.state.collectAsState()
        val textFieldState = rememberTextFieldState(initialQuery)

        TrackerSearch(
            state = textFieldState,
            onDispatchQuery = { screenModel.search(textFieldState.text.toString()) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = screenModel::select,
            onConfirmSelection = f@{ private ->
                val selected = state.selected ?: return@f
                screenModel.bind(selected, private) {
                    navigator.replaceAll(ServerTrackInfoDialogScreen(mangaId, mangaTitle))
                }
            },
            onDismissRequest = navigator::pop,
            supportsPrivateTracking = tracker.supportsPrivateTracking,
        )
    }

    private class Model(
        private val mangaId: Int,
        initialQuery: String,
        private val currentUrl: String?,
        private val tracker: ServerTrackerPresentation,
        private val client: SuwayomiGraphQlClient,
        private val context: Context,
    ) : StateScreenModel<Model.State>(State()) {
        init {
            search(initialQuery)
        }

        fun search(query: String) {
            screenModelScope.launch {
                mutableState.update { it.copy(queryResult = null, selected = null) }
                val result = withIOContext {
                    runCatching {
                        client.searchTracker(tracker.id.toInt(), query)
                            .map { it.toServerTrackSearchResult() }
                    }
                }
                mutableState.update {
                    it.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { search -> search.trackingUrl == currentUrl },
                    )
                }
            }
        }

        fun bind(
            item: ServerTrackSearchResult,
            private: Boolean,
            onComplete: () -> Unit,
        ) {
            screenModelScope.launchNonCancellable {
                runCatching {
                    client.bindTrack(mangaId, tracker.id.toInt(), item.remoteId, private)
                }.onSuccess {
                    ServerStateSync.requestRefresh(*serverTrackAffectedEntities(mangaId).toTypedArray())
                    withUIContext { onComplete() }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to bind Suwayomi track" }
                    withUIContext { context.toast(e.message ?: "Tracking failed") }
                }
            }
        }

        fun select(selected: ServerTrackSearchResult) {
            mutableState.update { it.copy(selected = selected) }
        }

        @Immutable
        data class State(
            val queryResult: Result<List<ServerTrackSearchResult>>? = null,
            val selected: ServerTrackSearchResult? = null,
        )
    }
}

private data class ServerTrackStatusSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: ServerTrackRecord,
    private val tracker: ServerTrackerPresentation,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var selection by remember(track.id) { mutableStateOf(track.status) }
        var pending by remember { mutableStateOf(false) }
        if (pending) {
            ServerUpdateEffect(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                onFailure = { pending = false },
            ) {
                updateTrack(track.id.toInt(), status = selection.toInt())
            }
        } else {
            TrackStatusSelector(
                selection = selection,
                onSelectionChange = { selection = it },
                selections = remember {
                    tracker.statuses.associate { it.value to it.label }
                },
                onConfirm = { pending = true },
                onDismissRequest = navigator::pop,
            )
        }
    }
}

private data class ServerTrackChapterSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: ServerTrackRecord,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var selection by remember(track.id) { mutableStateOf(track.lastChapterRead.toInt()) }
        var pending by remember { mutableStateOf(false) }
        if (pending) {
            ServerUpdateEffect(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                onFailure = { pending = false },
            ) {
                updateTrack(track.id.toInt(), lastChapterRead = selection.toDouble())
            }
        } else {
            TrackChapterSelector(
                selection = selection,
                onSelectionChange = { selection = it },
                range = remember(track.id) { 0..(track.totalChapters.takeIf { it > 0 }?.toInt() ?: 10000) },
                onConfirm = { pending = true },
                onDismissRequest = navigator::pop,
            )
        }
    }
}

private data class ServerTrackScoreSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: ServerTrackRecord,
    private val tracker: ServerTrackerPresentation,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var selection by remember(track.id) { mutableStateOf(tracker.displayScore(track)) }
        var pending by remember { mutableStateOf(false) }
        if (pending) {
            ServerUpdateEffect(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                onFailure = { pending = false },
            ) {
                updateTrack(track.id.toInt(), scoreString = selection)
            }
        } else {
            TrackScoreSelector(
                selection = selection,
                onSelectionChange = { selection = it },
                selections = remember { tracker.scores },
                onConfirm = { pending = true },
                onDismissRequest = navigator::pop,
            )
        }
    }
}

private data class ServerTrackDateSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: ServerTrackRecord,
    private val start: Boolean,
) : Screen() {
    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val targetDate = Instant.ofEpochMilli(utcTimeMillis).toLocalDate(ZoneOffset.UTC)
            if (targetDate > LocalDate.now(ZoneOffset.UTC)) return false
            return when {
                start && track.finishDate > 0 ->
                    targetDate <=
                        Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                !start && track.startDate > 0 -> Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC) <=
                    targetDate
                else -> true
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var pendingAction by remember { mutableStateOf<DateAction?>(null) }
        val initialSelection = remember(track.id, start) {
            (if (start) track.startDate else track.finishDate)
                .takeIf { it != 0L }
                ?: Instant.now().toEpochMilli()
        }.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)

        val action = pendingAction
        if (action != null) {
            ServerUpdateEffect(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                onFailure = { pendingAction = null },
            ) {
                when (action) {
                    is DateAction.Set -> {
                        if (start) {
                            updateTrack(track.id.toInt(), startDate = action.localMillis)
                        } else {
                            updateTrack(track.id.toInt(), finishDate = action.localMillis)
                        }
                    }
                    DateAction.Remove -> {
                        if (start) {
                            updateTrack(track.id.toInt(), startDate = 0)
                        } else {
                            updateTrack(track.id.toInt(), finishDate = 0)
                        }
                    }
                }
            }
        } else {
            TrackDateSelector(
                title = if (start) {
                    stringResource(MR.strings.track_started_reading_date)
                } else {
                    stringResource(MR.strings.track_finished_reading_date)
                },
                initialSelectedDateMillis = initialSelection,
                selectableDates = selectableDates,
                onConfirm = { utcMillis ->
                    pendingAction =
                        DateAction.Set(utcMillis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault()))
                },
                onRemove = {
                    pendingAction = DateAction.Remove
                }.takeIf { if (start) track.startDate > 0 else track.finishDate > 0 },
                onDismissRequest = navigator::pop,
            )
        }
    }

    private sealed interface DateAction {
        data class Set(val localMillis: Long) : DateAction
        data object Remove : DateAction
    }
}

private data class ServerTrackerRemoveScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: ServerTrackRecord,
    private val tracker: ServerTrackerPresentation,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var removeRemoteTrack by remember { mutableStateOf(false) }
        var pending by remember { mutableStateOf(false) }
        if (pending) {
            ServerUpdateEffect(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                onFailure = { pending = false },
            ) {
                unbindTrack(track.id.toInt(), deleteRemoteTrack = removeRemoteTrack)
            }
        } else {
            AlertDialogContent(
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                    )
                },
                title = {
                    Text(
                        text = stringResource(MR.strings.track_delete_title, tracker.name),
                        textAlign = TextAlign.Center,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        Text(text = stringResource(MR.strings.track_delete_text, tracker.name))
                        if (tracker.supportsTrackDeletion) {
                            LabeledCheckbox(
                                label = stringResource(MR.strings.track_delete_remote_text, tracker.name),
                                checked = removeRemoteTrack,
                                onCheckedChange = { removeRemoteTrack = it },
                            )
                        }
                    }
                },
                buttons = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
                    ) {
                        TextButton(onClick = navigator::pop) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                        FilledTonalButton(
                            onClick = { pending = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text(text = stringResource(MR.strings.action_remove))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ServerUpdateEffect(
    mangaId: Int,
    mangaTitle: String,
    onFailure: () -> Unit,
    action: suspend SuwayomiGraphQlClient.() -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching {
            context.appDependencies.suwayomiClientProvider.graphQlClient.action()
        }.onSuccess {
            ServerStateSync.requestRefresh(*serverTrackAffectedEntities(mangaId).toTypedArray())
            navigator.replaceAll(ServerTrackInfoDialogScreen(mangaId, mangaTitle))
        }.onFailure { e ->
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to update Suwayomi track" }
            context.toast(e.message ?: "Tracking update failed")
            onFailure()
        }
    }
    LoadingScreen()
}

private fun SuwayomiTrackerDto.toPresentation(
    displayScores: Map<Long, String> = emptyMap(),
): ServerTrackerPresentation {
    val logoRes = when (name.lowercase()) {
        "myanimelist" -> R.drawable.brand_myanimelist
        "anilist" -> R.drawable.brand_anilist
        "kitsu" -> R.drawable.brand_kitsu
        "shikimori" -> R.drawable.brand_shikimori
        "mangaupdates" -> R.drawable.brand_mangaupdates
        "bangumi" -> R.drawable.brand_bangumi
        else -> R.drawable.brand_suwayomi
    }
    return ServerTrackerPresentation(
        id = id.toLong(),
        name = name,
        logoRes = logoRes,
        supportsReadingDates = supportsReadingDates,
        supportsPrivateTracking = supportsPrivateTracking,
        supportsTrackDeletion = supportsTrackDeletion,
        statuses = statuses.map {
            ServerTrackerPresentation.Status(it.value.toLong(), it.name.toStatusStringResource())
        },
        scores = scores,
        displayScores = displayScores,
    )
}

private fun SuwayomiTrackRecordDto.toServerTrackRecord(): ServerTrackRecord {
    return ServerTrackRecord(
        id = id.toLong(),
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters.toLong(),
        status = status.toLong(),
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate.toLongOrNull() ?: 0L,
        finishDate = finishDate.toLongOrNull() ?: 0L,
        private = private,
    )
}

private fun SuwayomiTrackSearchDto.toServerTrackSearchResult(): ServerTrackSearchResult {
    return ServerTrackSearchResult(
        id = id.toLong(),
        remoteId = remoteId.toLongOrNull() ?: 0L,
        title = title,
        coverUrl = coverUrl.orEmpty(),
        summary = summary.orEmpty(),
        publishingStatus = publishingStatus.orEmpty(),
        publishingType = publishingType.orEmpty(),
        startDate = startDate.orEmpty(),
        score = score,
        trackingUrl = trackingUrl,
    )
}

private fun String?.toStatusStringResource(): StringResource? {
    return when (this?.lowercase()) {
        "reading", "reading list" -> MR.strings.reading
        "completed", "complete list" -> MR.strings.completed
        "on hold", "on hold list" -> MR.strings.on_hold
        "dropped", "unfinished list" -> MR.strings.dropped
        "plan to read", "plan to read list", "wish list" -> MR.strings.plan_to_read
        "rereading" -> MR.strings.repeating
        else -> null
    }
}

private fun <T> unsupported(): T = throw UnsupportedOperationException(
    "Use Suwayomi GraphQL operations for server-backed tracking",
)
