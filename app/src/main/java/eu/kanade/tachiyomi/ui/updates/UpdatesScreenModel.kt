package eu.kanade.tachiyomi.ui.updates

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.domain.manga.model.applyFilter
import eu.kanade.domain.updates.model.UpdatesWithRelations
import eu.kanade.domain.updates.service.UpdatesPreferences
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLibraryUpdateStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverLibraryUpdateAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverUpdatesBookmarkAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverUpdatesDownloadAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverUpdatesReadAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.syncTrackerProgressAfterReadStateChange
import eu.kanade.tachiyomi.di.AppDependencies
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class UpdatesScreenModel private constructor(
    private val application: Application,
    private val libraryPreferences: LibraryPreferences,
    private val updatesPreferences: UpdatesPreferences,
    private val suwayomiProvider: SuwayomiClientProvider,
    private val json: Json,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<UpdatesScreenModel.State>(State()) {

    internal constructor(dependencies: AppDependencies) : this(
        application = dependencies.application,
        libraryPreferences = dependencies.libraryPreferences,
        updatesPreferences = dependencies.updatesPreferences,
        suwayomiProvider = dependencies.suwayomiClientProvider,
        json = dependencies.json,
    )

    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val serverUpdatesRefreshes = MutableStateFlow(ServerRefreshRequest())
    private val serverDownloadRefreshes = MutableStateFlow(0)
    private var recentChapterIds: Set<Long> = emptySet()
    private var activeDownloadChapterIds: Set<Long> = emptySet()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp.asState(screenModelScope)

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedChapterIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            combine(
                serverUpdatesRefreshes.flatMapLatest { request ->
                    getServerUpdatesFlow(emitErrors = request.emitErrors)
                },
                serverDownloadRefreshes.flatMapLatest { getServerDownloadStatusFlow() },
                getUpdatesItemPreferenceFlow().distinctUntilChanged(),
            ) { updates, downloadStatus, itemPreferences ->
                updates
                    .toUpdateItems(downloadStatus)
                    .applyFilters(itemPreferences)
            }
                .collectLatest { updateItems ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = updateItems,
                        )
                    }
                }
        }

        getUpdatesItemPreferenceFlow()
            .map { prefs ->
                listOf(
                    prefs.filterUnread,
                    prefs.filterDownloaded,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                )
                    .any { it != TriState.DISABLED }
            }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        ServerStateSync.invalidations
            .onEach { invalidation ->
                if (invalidation.affectsAny(ServerStateEntity.Updates)) {
                    refreshServerUpdates()
                }
                if (invalidation.affectsAny(ServerStateEntity.Downloads)) {
                    serverDownloadRefreshes.update { it + 1 }
                }
            }
            .launchIn(screenModelScope)

        suwayomiProvider.liveStatusClient.libraryUpdateStatusFlow()
            .onEach { status ->
                mutableState.update { state ->
                    state.copy(libraryUpdateStatus = status.toLibraryUpdateState())
                }
            }
            .launchIn(screenModelScope)
    }

    private fun List<UpdatesItem>.applyFilters(
        preferences: ItemPreferences,
    ): List<UpdatesItem> {
        val filterDownloaded = preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterExcludedScanlators = preferences.filterExcludedScanlators

        val filterFnDownloaded: (UpdatesItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.downloadStateProvider() == Download.State.DOWNLOADED
            }
        }
        val filterFnUnread: (UpdatesItem) -> Boolean = {
            applyFilter(filterUnread) {
                !it.update.read
            }
        }
        val filterFnStarted: (UpdatesItem) -> Boolean = {
            applyFilter(filterStarted) {
                it.update.lastPageRead > 0
            }
        }
        val filterFnBookmarked: (UpdatesItem) -> Boolean = {
            applyFilter(filterBookmarked) {
                it.update.bookmark
            }
        }
        val filterFnExcludedScanlators: (UpdatesItem) -> Boolean = {
            !filterExcludedScanlators || it.update.scanlator !in it.excludedScanlators
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnExcludedScanlators(it)
        }
    }

    private fun getServerUpdatesFlow(emitErrors: Boolean): Flow<List<SuwayomiChapterWithMangaDto>> = flow {
        val result = runCatching { suwayomiClient.getRecentChapters() }
            .onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load server updates" }
                mutableState.update { it.copy(serverUnavailable = error.isSuwayomiServerUnavailable()) }
                if (emitErrors) {
                    _events.send(
                        if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError,
                    )
                }
            }
            .onSuccess {
                mutableState.update { it.copy(serverUnavailable = false) }
            }
        val updates = result.getOrDefault(emptyList())
        recentChapterIds = updates.mapTo(mutableSetOf()) { it.id.toLong() }
        emit(updates)
    }

    private fun getServerDownloadStatusFlow(): Flow<SuwayomiDownloadStatusDto?> = flow {
        emit(null)
        emitAll(
            suwayomiProvider.liveStatusClient.downloadStatusFlow()
                .onEach(::refreshUpdatesWhenVisibleDownloadLeavesQueue),
        )
    }

    private fun refreshUpdatesWhenVisibleDownloadLeavesQueue(status: SuwayomiDownloadStatusDto) {
        val nextActiveIds = status.queue.mapTo(mutableSetOf()) { it.chapter.id.toLong() }
        val removedVisibleIds = activeDownloadChapterIds
            .intersect(recentChapterIds)
            .subtract(nextActiveIds)
        activeDownloadChapterIds = nextActiveIds
        if (removedVisibleIds.isNotEmpty()) {
            refreshServerUpdates()
        }
    }

    fun refreshServerState(emitErrors: Boolean = true) {
        refreshServerUpdates(emitErrors)
        serverDownloadRefreshes.update { it + 1 }
    }

    private fun refreshServerUpdates(emitErrors: Boolean = true) {
        serverUpdatesRefreshes.update {
            ServerRefreshRequest(sequence = it.sequence + 1, emitErrors = emitErrors)
        }
    }

    private fun List<SuwayomiChapterWithMangaDto>.toUpdateItems(
        downloadStatus: SuwayomiDownloadStatusDto?,
    ): List<UpdatesItem> {
        val queuedDownloads = downloadStatus
            ?.queue
            ?.associateBy { it.chapter.id.toLong() }
            .orEmpty()
        return this
            .map { chapter ->
                val manga = chapter.manga
                val mangaId = manga.id.toLong()
                val chapterId = chapter.id.toLong()
                val sourceId = manga.sourceId.toLongOrNull() ?: 0L
                val activeDownload = queuedDownloads[chapterId]
                val downloadState = when {
                    activeDownload != null -> activeDownload.state.toDownloadState()
                    chapter.isDownloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                UpdatesItem(
                    update = UpdatesWithRelations(
                        mangaId = mangaId,
                        mangaTitle = manga.title,
                        chapterId = chapterId,
                        chapterName = chapter.name,
                        scanlator = chapter.scanlator,
                        chapterUrl = chapter.url,
                        read = chapter.isRead,
                        bookmark = chapter.isBookmarked,
                        lastPageRead = chapter.lastPageRead.toLong(),
                        sourceId = sourceId,
                        dateFetch = chapter.fetchedAt.toLongOrNull() ?: chapter.uploadDate,
                        coverData = MangaCover(
                            mangaId = mangaId,
                            sourceId = sourceId,
                            isMangaFavorite = manga.inLibrary,
                            url = manga.thumbnailUrl?.let { resolveServerUrl(suwayomiProvider.baseUrl(), it) },
                            lastModified = 0L,
                        ),
                    ),
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress.toDownloadProgress() },
                    excludedScanlators = manga.excludedScanlators(),
                    selected = chapterId in selectedChapterIds,
                )
            }
    }

    private fun SuwayomiMangaDto.excludedScanlators(): Set<String> {
        val value = meta.firstOrNull { it.key == SERVER_EXCLUDED_SCANLATORS_META_KEY }?.value ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), value).toSet()
        }.getOrDefault(emptySet())
    }

    fun updateLibrary(): Boolean {
        if (state.value.libraryUpdateStatus.isRunning) {
            stopLibraryUpdate()
            return false
        }
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.updateLibraryMangas()
            }.onSuccess { started ->
                libraryPreferences.lastUpdatedTimestamp.set(System.currentTimeMillis())
                refreshServerUpdates()
                ServerStateSync.requestRefresh(*serverLibraryUpdateAffectedEntities().toTypedArray())
                if (started) {
                    ServerNotificationSyncJob.schedulePromptReconciliation(application)
                }
                _events.send(Event.LibraryUpdateTriggered(started))
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to trigger server library update" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
        return true
    }

    private fun stopLibraryUpdate() {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.stopLibraryUpdate()
            }.onSuccess {
                refreshServerUpdates()
                ServerStateSync.requestRefresh(*serverLibraryUpdateAffectedEntities().toTypedArray())
                _events.send(Event.LibraryUpdateStopped)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to stop server library update from Updates" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
    }

    fun downloadChapters(items: List<UpdatesItem>, action: ChapterDownloadAction) {
        if (items.isEmpty()) return
        screenModelScope.launchNonCancellable {
            when (action) {
                ChapterDownloadAction.START -> {
                    enqueueDownloads(items)
                }
                ChapterDownloadAction.START_NOW -> {
                    enqueueDownloads(listOfNotNull(items.singleOrNull()), startDownloader = true)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterIds = items.map { it.update.chapterId.toInt() }
                    runServerDownloadAction(items.serverUpdateDownloadAffectedEntities()) {
                        suwayomiClient.dequeueChapterDownloads(chapterIds)
                    }
                }
                ChapterDownloadAction.DELETE -> {
                    deleteChapters(items)
                }
                ChapterDownloadAction.SAVE_DEVICE,
                ChapterDownloadAction.REMOVE_DEVICE,
                ChapterDownloadAction.REFRESH_DEVICE,
                -> Unit
            }
            toggleAllSelection(false)
        }
    }

    private suspend fun enqueueDownloads(items: List<UpdatesItem>, startDownloader: Boolean = false) {
        val chapterIds = items.map { it.update.chapterId.toInt() }
        if (chapterIds.isEmpty()) return
        runServerDownloadAction(items.serverUpdateDownloadAffectedEntities()) {
            suwayomiClient.enqueueChapterDownloads(chapterIds)
            if (startDownloader) {
                suwayomiClient.startDownloader()
            }
        }
    }

    /**
     * Mark the selected updates list as read/unread.
     * @param updates the list of selected updates.
     * @param read whether to mark chapters as read or unread.
     */
    fun markUpdatesRead(updates: List<UpdatesItem>, read: Boolean) {
        screenModelScope.launchIO {
            val changedMangaIds = updates
                .filterNot { it.update.read == read }
                .map { it.update.mangaId.toInt() }
                .distinct()
            runCatching {
                val chapterIds = updates
                    .filterNot { it.update.read == read }
                    .map { it.update.chapterId.toInt() }
                suwayomiClient.updateChaptersRead(chapterIds, read)
                syncTrackerProgressAfterReadStateChange(
                    read = read,
                    changedMangaIds = changedMangaIds,
                    trackProgress = suwayomiClient::trackProgress,
                    onFailure = { error ->
                        logcat(LogPriority.ERROR, error) { "Failed to update server tracker progress from Updates" }
                    },
                )
            }.onSuccess {
                refreshServerUpdates()
                ServerStateSync.requestRefresh(
                    *serverUpdatesReadAffectedEntities(changedMangaIds).toTypedArray(),
                )
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to update server chapter read state from Updates" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param updates the list of chapters to bookmark.
     */
    fun bookmarkUpdates(updates: List<UpdatesItem>, bookmark: Boolean) {
        screenModelScope.launchIO {
            runCatching {
                val chapterIds = updates
                    .filterNot { it.update.bookmark == bookmark }
                    .map { it.update.chapterId.toInt() }
                suwayomiClient.updateChaptersBookmark(chapterIds, bookmark)
            }.onSuccess {
                refreshServerUpdates()
                ServerStateSync.requestRefresh(
                    *serverUpdatesBookmarkAffectedEntities(updates.map { it.update.mangaId.toInt() }.distinct())
                        .toTypedArray(),
                )
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to update server chapter bookmark state from Updates" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
        toggleAllSelection(false)
    }

    /**
     * Delete selected chapters
     *
     * @param updatesItem list of chapters
     */
    fun deleteChapters(updatesItem: List<UpdatesItem>) {
        screenModelScope.launchNonCancellable {
            runServerDownloadAction(updatesItem.serverUpdateDownloadAffectedEntities()) {
                suwayomiClient.deleteDownloadedChapters(updatesItem.map { it.update.chapterId.toInt() })
            }
        }
        toggleAllSelection(false)
    }

    private suspend fun runServerDownloadAction(
        affected: Set<ServerStateEntity>,
        action: suspend () -> Unit,
    ) {
        runCatching {
            action()
        }.onSuccess {
            refreshServerUpdates()
            serverDownloadRefreshes.update { it + 1 }
            ServerStateSync.requestRefresh(*affected.toTypedArray())
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to update server download state from Updates" }
            _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
        }
    }

    private fun List<UpdatesItem>.serverUpdateDownloadAffectedEntities(): Set<ServerStateEntity> {
        return serverUpdatesDownloadAffectedEntities(map { it.update.mangaId.toInt() }.distinct())
    }

    private fun String.toDownloadState(): Download.State {
        return when {
            equals("DOWNLOADED", ignoreCase = true) ||
                equals("FINISHED", ignoreCase = true) ||
                equals("DONE", ignoreCase = true) -> Download.State.DOWNLOADED
            equals("ERROR", ignoreCase = true) ||
                equals("FAILED", ignoreCase = true) -> Download.State.ERROR
            contains("DOWNLOAD", ignoreCase = true) ||
                equals("STARTED", ignoreCase = true) -> Download.State.DOWNLOADING
            else -> Download.State.QUEUE
        }
    }

    private fun Double?.toDownloadProgress(): Int {
        val value = this ?: return 0
        return when {
            value <= 1.0 -> (value * 100).toInt()
            else -> value.toInt()
        }.coerceIn(0, 100)
    }

    fun showConfirmDeleteChapters(updatesItem: List<UpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    fun toggleSelection(
        item: UpdatesItem,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.update.chapterId == item.update.chapterId }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.update.chapterId, selected)

                if (selected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.update.chapterId)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (!fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            state.copy(items = newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems)
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedChapterIds.addOrRemove(it.update.chapterId, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems)
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newUpdatesCount.set(0)
    }

    private fun getUpdatesItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            updatesPreferences.filterDownloaded.changes(),
            updatesPreferences.filterUnread.changes(),
            updatesPreferences.filterStarted.changes(),
            updatesPreferences.filterBookmarked.changes(),
            updatesPreferences.filterExcludedScanlators.changes(),
        ) { downloaded, unread, started, bookmarked, excludedScanlators ->
            ItemPreferences(
                filterDownloaded = downloaded,
                filterUnread = unread,
                filterStarted = started,
                filterBookmarked = bookmarked,
                filterExcludedScanlators = excludedScanlators,
            )
        }
    }

    fun showFilterDialog() {
        mutableState.update { it.copy(dialog = Dialog.FilterSheet) }
    }

    @Immutable
    private data class ItemPreferences(
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterExcludedScanlators: Boolean,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val serverUnavailable: Boolean = false,
        val hasActiveFilters: Boolean = false,
        val libraryUpdateStatus: LibraryUpdateState = LibraryUpdateState(),
        val items: List<UpdatesItem> = listOf(),
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<UpdatesUiModel> {
            return items
                .map { UpdatesUiModel.Item(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> UpdatesUiModel.Header(afterDate)
                        // Return null to avoid adding a separator between two items.
                        else -> null
                    }
                }
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<UpdatesItem>) : Dialog
        data object FilterSheet : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data object ServerUnavailable : Event
        data object LibraryUpdateStopped : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

@Immutable
data class LibraryUpdateState(
    val isRunning: Boolean = false,
    val totalJobs: Int = 0,
    val finishedJobs: Int = 0,
)

private fun SuwayomiLibraryUpdateStatusDto.toLibraryUpdateState(): LibraryUpdateState {
    return LibraryUpdateState(
        isRunning = jobsInfo.isRunning,
        totalJobs = jobsInfo.totalJobs,
        finishedJobs = jobsInfo.finishedJobs,
    )
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
    val excludedScanlators: Set<String> = emptySet(),
    val selected: Boolean = false,
)

private data class ServerRefreshRequest(
    val sequence: Int = 0,
    val emitErrors: Boolean = true,
)

private const val SERVER_EXCLUDED_SCANLATORS_META_KEY = "amatsubu.excludedScanlators"
