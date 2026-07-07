package eu.kanade.tachiyomi.ui.manga.track

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.DisposableEffect
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
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.TrackerSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackRecordDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackSearchDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackerDto
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track as DomainTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
        val screenModel = rememberScreenModel { Model(mangaId) }
        val state by screenModel.state.collectAsState()
        val dateFormat = remember { UiPreferences.dateFormat(Injekt.get<UiPreferences>().dateFormat.get()) }

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
                                tracker = it.tracker as ServerTracker,
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
                                tracker = it.tracker as ServerTracker,
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
                                tracker = it.tracker as ServerTracker,
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
                                tracker = it.tracker as ServerTracker,
                            ),
                        )
                    },
                    onCopyLink = { context.copyTrackerLink(it) },
                    onTogglePrivate = { screenModel.togglePrivate(it.track!!) },
                )
            }
        }
    }

    private fun Context.copyTrackerLink(trackItem: TrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    private class Model(
        private val mangaId: Int,
    ) : StateScreenModel<Model.State>(State()) {
        private val client = suwayomiClient()

        init {
            refresh(showLoading = true)
            ServerStateSync.refreshes
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
                            TrackItem(
                                track = records.find { it.trackerId == tracker.id }?.toDomainTrack(),
                                tracker = ServerTracker(tracker, displayScores),
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

        fun togglePrivate(track: DomainTrack) {
            screenModelScope.launchNonCancellable {
                runCatching {
                    client.updateTrack(track.id.toInt(), private = !track.private)
                    ServerStateSync.requestRefresh()
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
            val trackItems: List<TrackItem> = emptyList(),
        )
    }
}

private data class ServerTrackerSearchScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val tracker: ServerTracker,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model(mangaId, initialQuery, currentUrl, tracker.dto) }
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
        private val tracker: SuwayomiTrackerDto,
    ) : StateScreenModel<Model.State>(State()) {
        private val client = suwayomiClient()

        init {
            search(initialQuery)
        }

        fun search(query: String) {
            screenModelScope.launch {
                mutableState.update { it.copy(queryResult = null, selected = null) }
                val result = withIOContext {
                    runCatching {
                        client.searchTracker(tracker.id, query)
                            .map { it.toTrackSearch() }
                    }
                }
                mutableState.update {
                    it.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { search -> search.tracking_url == currentUrl },
                    )
                }
            }
        }

        fun bind(
            item: TrackSearch,
            private: Boolean,
            onComplete: () -> Unit,
        ) {
            screenModelScope.launchNonCancellable {
                runCatching {
                    client.bindTrack(mangaId, tracker.id, item.remote_id, private)
                }.onSuccess {
                    ServerStateSync.requestRefresh()
                    withUIContext { onComplete() }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to bind Suwayomi track" }
                    withUIContext { Injekt.get<Application>().toast(e.message ?: "Tracking failed") }
                }
            }
        }

        fun select(selected: TrackSearch) {
            mutableState.update { it.copy(selected = selected) }
        }

        @Immutable
        data class State(
            val queryResult: Result<List<TrackSearch>>? = null,
            val selected: TrackSearch? = null,
        )
    }
}

private data class ServerTrackStatusSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: DomainTrack,
    private val tracker: ServerTracker,
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
                selections = remember { tracker.dto.statuses.associate { it.value.toLong() to it.name.toStatusStringResource() } },
                onConfirm = { pending = true },
                onDismissRequest = navigator::pop,
            )
        }
    }
}

private data class ServerTrackChapterSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: DomainTrack,
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
    private val track: DomainTrack,
    private val tracker: ServerTracker,
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
                selections = remember { tracker.getScoreList() },
                onConfirm = { pending = true },
                onDismissRequest = navigator::pop,
            )
        }
    }
}

private data class ServerTrackDateSelectorScreen(
    private val mangaId: Int,
    private val mangaTitle: String,
    private val track: DomainTrack,
    private val start: Boolean,
) : Screen() {
    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val targetDate = Instant.ofEpochMilli(utcTimeMillis).toLocalDate(ZoneOffset.UTC)
            if (targetDate > LocalDate.now(ZoneOffset.UTC)) return false
            return when {
                start && track.finishDate > 0 -> targetDate <= Instant.ofEpochMilli(track.finishDate).toLocalDate(ZoneOffset.UTC)
                !start && track.startDate > 0 -> Instant.ofEpochMilli(track.startDate).toLocalDate(ZoneOffset.UTC) <= targetDate
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
                    pendingAction = DateAction.Set(utcMillis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault()))
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
    private val track: DomainTrack,
    private val tracker: ServerTracker,
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
                        if (tracker.dto.supportsTrackDeletion) {
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
            suwayomiClient().action()
        }.onSuccess {
            ServerStateSync.requestRefresh()
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

private data class ServerTracker(
    val dto: SuwayomiTrackerDto,
    private val displayScores: Map<Long, String> = emptyMap(),
) : Tracker, DeletableTracker {
    override val id: Long = dto.id.toLong()
    override val name: String = dto.name
    override val client: OkHttpClient = Injekt.get<NetworkHelper>().client
    override val supportsReadingDates: Boolean = dto.supportsReadingDates
    override val supportsPrivateTracking: Boolean = dto.supportsPrivateTracking
    override val isLoggedIn: Boolean = dto.isLoggedIn
    override val isLoggedInFlow: Flow<Boolean> = MutableStateFlow(dto.isLoggedIn)

    override fun getLogo(): Int = when (dto.name.lowercase()) {
        "myanimelist" -> R.drawable.brand_myanimelist
        "anilist" -> R.drawable.brand_anilist
        "kitsu" -> R.drawable.brand_kitsu
        "shikimori" -> R.drawable.brand_shikimori
        "mangaupdates" -> R.drawable.brand_mangaupdates
        "bangumi" -> R.drawable.brand_bangumi
        else -> R.drawable.brand_suwayomi
    }

    override fun getStatusList(): List<Long> = dto.statuses.map { it.value.toLong() }
    override fun getStatus(status: Long): StringResource? = dto.statuses.firstOrNull { it.value.toLong() == status }?.name.toStatusStringResource()
    override fun getReadingStatus(): Long = dto.statuses.firstOrNull()?.value?.toLong() ?: 0
    override fun getRereadingStatus(): Long = dto.statuses.firstOrNull { it.name.equals("Rereading", ignoreCase = true) }?.value?.toLong() ?: getReadingStatus()
    override fun getCompletionStatus(): Long = dto.statuses.firstOrNull { it.name.equals("Completed", ignoreCase = true) }?.value?.toLong() ?: getReadingStatus()
    override fun getScoreList(): List<String> = dto.scores
    override fun get10PointScore(track: DomainTrack): Double = track.score
    override fun indexToScore(index: Int): Double = dto.scores.getOrNull(index)?.toDoubleOrNull() ?: 0.0
    override fun displayScore(track: DomainTrack): String = displayScores[track.id]?.takeIf { it.isNotBlank() } ?: track.score.toString()

    override suspend fun update(track: Track, didReadChapter: Boolean): Track = unsupported()
    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = unsupported()
    override suspend fun search(query: String): List<TrackSearch> = unsupported()
    override suspend fun refresh(track: Track): Track = unsupported()
    override suspend fun login(username: String, password: String) = unsupported<Unit>()
    override fun logout() = Unit
    override fun getUsername(): String = ""
    override fun getPassword(): String = ""
    override fun saveCredentials(username: String, password: String) = Unit
    override suspend fun register(item: Track, mangaId: Long) = unsupported<Unit>()
    override suspend fun setRemoteStatus(track: Track, status: Long) = unsupported<Unit>()
    override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int) = unsupported<Unit>()
    override suspend fun setRemoteScore(track: Track, scoreString: String) = unsupported<Unit>()
    override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) = unsupported<Unit>()
    override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) = unsupported<Unit>()
    override suspend fun setRemotePrivate(track: Track, private: Boolean) = unsupported<Unit>()
    override suspend fun delete(track: DomainTrack) = unsupported<Unit>()
}

private fun SuwayomiTrackRecordDto.toDomainTrack(): DomainTrack {
    return DomainTrack(
        id = id.toLong(),
        mangaId = mangaId.toLong(),
        trackerId = trackerId.toLong(),
        remoteId = remoteId.toLongOrNull() ?: 0L,
        libraryId = libraryId?.toLongOrNull(),
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

private fun SuwayomiTrackSearchDto.toTrackSearch(): TrackSearch {
    return TrackSearch.create(trackerId.toLong()).also {
        it.id = id.toLong()
        it.remote_id = remoteId.toLongOrNull() ?: 0L
        it.library_id = libraryId?.toLongOrNull()
        it.title = title
        it.last_chapter_read = lastChapterRead
        it.total_chapters = totalChapters.toLong()
        it.tracking_url = trackingUrl
        it.cover_url = coverUrl.orEmpty()
        it.summary = summary.orEmpty()
        it.publishing_status = publishingStatus.orEmpty()
        it.publishing_type = publishingType.orEmpty()
        it.start_date = startDate.orEmpty()
        it.status = status.toLong()
        it.score = score
        it.started_reading_date = startedReadingDate.toLongOrNull() ?: 0L
        it.finished_reading_date = finishedReadingDate.toLongOrNull() ?: 0L
        it.private = private
    }
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

private fun suwayomiClient(): SuwayomiGraphQlClient {
    return SuwayomiClientProvider().graphQlClient
}

private fun <T> unsupported(): T = throw UnsupportedOperationException("Use Suwayomi GraphQL operations for server-backed tracking")
