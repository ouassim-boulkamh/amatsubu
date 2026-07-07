package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.chaptersFiltered
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.localDownloadedFilter
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyStore
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyWorker
import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_MANGA_REAL_URL_META_KEY
import eu.kanade.tachiyomi.data.suwayomi.ServerReadStatePendingStore
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDisplaySource
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiFetchEstimate
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiStaleSnapshotState
import eu.kanade.tachiyomi.data.suwayomi.estimateFetchInterval
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.data.suwayomi.oldestPositive
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverCoverLastModified
import eu.kanade.tachiyomi.data.suwayomi.syncTrackerProgressAfterReadStateChange
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import mihon.core.common.extensions.EMPTY
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.floor
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy as SuwayomiUpdateStrategy
import eu.kanade.tachiyomi.ui.browse.migration.SERVER_MIGRATION_NOTES_META_KEY as SERVER_MANGA_NOTES_META_KEY

class MangaScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val mangaId: Long,
    private val isFromSource: Boolean,
    private val fetchChaptersOnOpen: Boolean = isFromSource,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    readerPreferences: ReaderPreferences = Injekt.get(),
    private val getMangaAndChapters: GetMangaWithChapters = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getAvailableScanlators: GetAvailableScanlators = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val clientDeviceChapterCopyStore: ClientDeviceChapterCopyStore = Injekt.get()
    private val pendingReadStateStore: ServerReadStatePendingStore = Injekt.get()
    private var serverActiveDownloadsByChapterId: Map<Long, SuwayomiDownloadDto> = emptyMap()
    private var deviceCopyStatesByChapterId: Map<Long, DeviceCopyState> = emptyMap()
    private var deviceCopyProgressByChapterId: Map<Long, Int> = emptyMap()
    private var pendingReadStatesByChapterId: Map<Long, Boolean> = emptyMap()

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeToEndAction.get()
    val chapterSwipeEndAction = libraryPreferences.swipeToStartAction.get()

    private val skipFiltered by readerPreferences.skipFiltered.asState(screenModelScope)

    val isUpdateIntervalEnabled =
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateMangaRestrictions.get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Error -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        ServerStateSync.refreshes
            .onEach {
                if (successState?.isServerBacked == true) {
                    refreshServerManga()
                }
            }
            .launchIn(screenModelScope)

        suwayomiProvider.liveStatusClient.downloadStatusFlow()
            .onEach { downloadStatus ->
                val previousActiveChapterIds = serverActiveDownloadsByChapterId.keys
                applyServerDownloadStatus(downloadStatus)

                val state = successState ?: return@onEach
                if (!state.isServerBacked) return@onEach

                val currentChapterIds = state.chapters.map { it.id }.toSet()
                val removedCurrentDownloads = previousActiveChapterIds
                    .minus(serverActiveDownloadsByChapterId.keys)
                    .intersect(currentChapterIds)
                if (removedCurrentDownloads.isNotEmpty()) {
                    loadServerMangaState(fetchChapters = false)
                }
            }
            .launchIn(screenModelScope)

        screenModelScope.launchIO {
            if (isFromSource) {
                loadInitialServerMangaState(fetchChapters = fetchChaptersOnOpen)
                return@launchIO
            }
            val manga = runCatching { getMangaAndChapters.awaitManga(mangaId) }
                .getOrElse {
                    loadInitialServerMangaState()
                    return@launchIO
                }
            val chapters = getMangaAndChapters.awaitChapters(mangaId, applyScanlatorFilter = true)
                .toChapterListItems(manga)

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                State.Success(
                    manga = manga,
                    source = Injekt.get<SourceManager>().getOrStub(manga.source),
                    isFromSource = isFromSource,
                    isServerBacked = false,
                    chapters = chapters,
                    availableScanlators = getAvailableScanlators.await(mangaId),
                    excludedScanlators = getExcludedScanlators.await(mangaId),
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                    hideMissingChapters = libraryPreferences.hideMissingChapters.get(),
                )
            }

            startLocalObservers()

            // Fetch info-chapters when needed
            if ((needRefreshInfo || needRefreshChapter) && screenModelScope.isActive) {
                fetchAllFromSource(
                    manualFetch = false,
                    fetchDetails = needRefreshInfo,
                    fetchChapters = needRefreshChapter,
                )
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    private fun startLocalObservers() {
        screenModelScope.launchIO {
            getMangaAndChapters.subscribe(mangaId, applyScanlatorFilter = true)
                .distinctUntilChanged()
                .flowWithLifecycle(lifecycle)
                .catch { error -> logcat(LogPriority.ERROR, error) { "Ignoring local manga observer failure" } }
                .collectLatest { (manga, chapters) ->
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapters.toChapterListItems(manga),
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            getExcludedScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .catch { error -> logcat(LogPriority.ERROR, error) { "Ignoring local scanlator exclusion failure" } }
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                }
        }

        screenModelScope.launchIO {
            getAvailableScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .catch { error -> logcat(LogPriority.ERROR, error) { "Ignoring local scanlator availability failure" } }
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                }
        }
    }

    private suspend fun loadServerMangaState(fetchChapters: Boolean = false): List<String> {
        val partialErrors: List<String>
        val serverManga: SuwayomiMangaDto
        val serverChapters: List<SuwayomiChapterDto>
        val staleSnapshot: SuwayomiStaleSnapshotState? = null
        if (fetchChapters) {
            val partialResponse = suwayomiClient.fetchMangaAndChaptersPartial(
                mangaId = mangaId.toInt(),
                fetchManga = true,
                fetchChapters = true,
            )
            val payload = partialResponse.data ?: error("Suwayomi server returned no manga or chapters")
            partialErrors = partialResponse.errorMessages
            serverManga = payload.manga
            serverChapters = payload.chapters
        } else {
            partialErrors = emptyList()
            serverManga = suwayomiClient.getManga(mangaId.toInt())
            serverChapters = suwayomiClient.getCachedChapters(mangaId.toInt())
        }
        val pushedPendingCount = pushPendingReadStates()
        val effectiveServerChapters = if (pushedPendingCount > 0) {
            suwayomiClient.getCachedChapters(mangaId.toInt())
        } else {
            serverChapters
        }
        return applyServerMangaState(
            partialErrors = partialErrors,
            serverManga = serverManga,
            serverChapters = effectiveServerChapters,
            staleSnapshot = staleSnapshot,
        )
    }

    private suspend fun loadServerMangaSnapshotState(error: Throwable): Boolean {
        if (!error.isSuwayomiServerUnavailable()) return false
        val mangaSnapshot = suwayomiClient.getMangaSnapshot(mangaId.toInt()) ?: return false
        val chapterSnapshot = suwayomiClient.getChaptersSnapshot(mangaId.toInt()) ?: return false
        applyServerMangaState(
            partialErrors = emptyList(),
            serverManga = mangaSnapshot.value,
            serverChapters = chapterSnapshot.value,
            staleSnapshot = SuwayomiStaleSnapshotState(
                syncedAt = mangaSnapshot.syncedAt.oldestPositive(chapterSnapshot.syncedAt)!!,
            ),
        )
        return true
    }

    private suspend fun applyServerMangaState(
        partialErrors: List<String>,
        serverManga: SuwayomiMangaDto,
        serverChapters: List<SuwayomiChapterDto>,
        staleSnapshot: SuwayomiStaleSnapshotState?,
    ): List<String> {
        val baseManga = serverManga.toDomainManga()
        val manga = serverManga.toDomainManga(serverChapters.estimateFetchInterval(baseManga))
        val downloadStatus = if (staleSnapshot == null) {
            runCatching { suwayomiClient.getDownloadStatus() }.getOrNull()
        } else {
            suwayomiClient.getDownloadStatusSnapshot()?.value
        }
        downloadStatus?.let(::applyServerDownloadStatus)
        loadDeviceCopyMaps()
        pendingReadStatesByChapterId = pendingReadStateStore
            .getForManga(suwayomiProvider.serverKey(), mangaId.toInt())
            .associate { it.chapterId.toLong() to it.isRead }
        val pendingReadStateCount = pendingReadStateStore.count(suwayomiProvider.serverKey()).toInt()
        val chapters = serverChapters.toServerChapterListItems(manga)
        val source = if (staleSnapshot == null) {
            suwayomiClient.sourceList()
                .firstOrNull { it.id == serverManga.sourceId }
                ?.let { SuwayomiDisplaySource(it, manga.source) }
                ?: Injekt.get<SourceManager>().getOrStub(manga.source)
        } else {
            Injekt.get<SourceManager>().getOrStub(manga.source)
        }
        val trackingCount = if (staleSnapshot == null) {
            runCatching {
                suwayomiClient.getTrackRecords(mangaId.toInt()).size
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to load server manga tracking count" }
            }.getOrDefault(0)
        } else {
            0
        }

        mutableState.update { previous ->
            State.Success(
                manga = manga,
                source = source,
                isFromSource = isFromSource,
                isServerBacked = true,
                chapters = chapters,
                availableScanlators = chapters.mapNotNull { it.chapter.scanlator }.toSet(),
                excludedScanlators = serverManga.excludedScanlators(),
                trackingCount = trackingCount,
                isRefreshingData = false,
                dialog = (previous as? State.Success)?.dialog,
                hideMissingChapters = libraryPreferences.hideMissingChapters.get(),
                staleSnapshot = staleSnapshot,
                pendingReadStateCount = pendingReadStateCount,
            )
        }
        return partialErrors
    }

    suspend fun refreshDeviceCopyStates() {
        val state = successState ?: return
        if (!state.isServerBacked) return

        loadDeviceCopyMaps()
        updateSuccessState { current ->
            current.copy(
                chapters = current.chapters.map { chapterItem ->
                    chapterItem.copy(
                        deviceCopyState = deviceCopyStatesByChapterId[chapterItem.id] ?: DeviceCopyState.NONE,
                        deviceCopyProgress = deviceCopyProgressByChapterId[chapterItem.id] ?: 0,
                    )
                },
            )
        }
    }

    private suspend fun loadDeviceCopyMaps() {
        val deviceCopies = clientDeviceChapterCopyStore
            .getCopiesForManga(suwayomiProvider.serverKey(), mangaId.toInt())
        deviceCopyStatesByChapterId = deviceCopies.associate { it.chapterId.toLong() to it.toDeviceCopyState() }
        deviceCopyProgressByChapterId = deviceCopies.associate { it.chapterId.toLong() to it.toDeviceCopyProgress() }
    }

    private suspend fun pushPendingReadStates(): Int {
        val pending = pendingReadStateStore.getForServer(suwayomiProvider.serverKey())
        if (pending.isEmpty()) return 0

        var pushed = 0
        pending
            .groupBy { it.isRead }
            .forEach { (isRead, states) ->
                val chapterIds = states.map { it.chapterId }
                suwayomiClient.updateChaptersRead(chapterIds = chapterIds, isRead = isRead)
                states.forEach {
                    pendingReadStateStore.delete(
                        serverKey = it.serverKey,
                        mangaId = it.mangaId,
                        chapterId = it.chapterId,
                    )
                }
                pushed += states.size
            }
        return pushed
    }

    private fun applyServerDownloadStatus(downloadStatus: SuwayomiDownloadStatusDto) {
        serverActiveDownloadsByChapterId = downloadStatus.queue
            .associateBy { it.chapter.id.toLong() }

        updateSuccessState { state ->
            if (!state.isServerBacked) return@updateSuccessState state
            state.copy(
                chapters = state.chapters.map { chapterItem ->
                    chapterItem.withServerDownloadState()
                },
            )
        }
    }

    private suspend fun loadInitialServerMangaState(fetchChapters: Boolean = false) {
        try {
            loadServerMangaState(fetchChapters = fetchChapters)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to load server manga state" }
            if (loadServerMangaSnapshotState(e)) return
            mutableState.update {
                State.Error(message = with(context) { e.formattedMessage })
            }
        }
    }

    fun retryServerMangaLoad() {
        mutableState.update { State.Loading }
        screenModelScope.launchIO {
            loadInitialServerMangaState(fetchChapters = fetchChaptersOnOpen)
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            fetchAllFromSource(
                manualFetch = manualFetch,
                fetchDetails = true,
                fetchChapters = true,
            )
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun refreshServerManga() {
        screenModelScope.launchIO {
            updateSuccessState { it.copy(isRefreshingData = true) }
            try {
                val errors = loadServerMangaState(fetchChapters = true)
                if (errors.isNotEmpty()) {
                    val message = errors.joinToString("; ")
                    logcat(LogPriority.ERROR) { "Server manga refresh returned partial data: $message" }
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to refresh server manga state" }
                if (loadServerMangaSnapshotState(e)) return@launchIO
                updateSuccessState { it.copy(isRefreshingData = false) }
                snackbarHostState.showSnackbar(
                    message = with(context) { e.formattedMessage },
                    duration = SnackbarDuration.Short,
                    withDismissAction = true,
                )
            }
        }
    }

    private suspend fun fetchAllFromSource(
        manualFetch: Boolean,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) {
        val state = successState ?: return
        try {
            withUIContext {
                val update = updateMangaFromRemote(
                    source = state.source,
                    manga = state.manga,
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                    manualFetch = manualFetch,
                )
                    .getOrThrow()

                if (manualFetch) {
                    refreshServerManga()
                }
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (e: Exception) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
    }

    // Manga info - start

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                // Local manga downloads are retired; server-backed removal is handled by toggleServerLibrary().
            },
        )
    }

    fun toggleServerLibrary() {
        val state = successState ?: return
        screenModelScope.launchIO {
            val removeFromLibrary = state.manga.favorite
            val updatedManga = runCatching {
                suwayomiClient.updateMangaLibrary(
                    mangaId = state.manga.id.toInt(),
                    inLibrary = !removeFromLibrary,
                )
            }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi library state" }
                withUIContext {
                    snackbarHostState.showSnackbar(message = with(context) { error.formattedMessage })
                }
                return@launchIO
            }

            updateSuccessState {
                val currentEstimate = state.manga.fetchEstimate()
                it.copy(manga = updatedManga.toDomainManga(currentEstimate), staleSnapshot = null)
            }
            ServerStateSync.requestRefresh()
            if (removeFromLibrary) {
                promptDeleteServerDownloads()
            }
        }
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        if (state.isServerBacked) {
            toggleServerLibrary()
            return
        }
        screenModelScope.launchIO {
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    // Remove covers and update last modified in db
                    if (manga.removeCovers() != manga) {
                        updateManga.awaitUpdateCoverLastModified(manga.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicates = getDuplicateLibraryManga(manga)

                    if (duplicates.isNotEmpty()) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launchIO
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> showChangeCategoryDialog()
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.manga ?: return
        screenModelScope.launch {
            val categories = runCatching { getCategories() }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load manga categories" }
                snackbarHostState.showSnackbar(message = with(context) { error.formattedMessage })
                return@launch
            }
            val selection = runCatching { getMangaCategoryIds(manga) }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load manga category selection" }
                snackbarHostState.showSnackbar(message = with(context) { error.formattedMessage })
                return@launch
            }
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection },
                    ),
                )
            }
        }
    }

    fun showSetFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedManga) }
            }
        }
    }

    private suspend fun promptDeleteServerDownloads() {
        if (!hasServerDownloads()) return
        val result = snackbarHostState.showSnackbar(
            message = context.stringResource(MR.strings.delete_downloads_for_manga),
            actionLabel = context.stringResource(MR.strings.action_delete),
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            deleteServerDownloads()
        }
    }

    private fun hasServerDownloads(): Boolean {
        return successState?.chapters.orEmpty()
            .any { it.downloadState == Download.State.DOWNLOADED }
    }

    private suspend fun deleteServerDownloads() {
        val chapterIds = successState?.chapters.orEmpty()
            .filter { it.downloadState == Download.State.DOWNLOADED }
            .map { it.chapter.id.toInt() }
        if (chapterIds.isEmpty()) return
        runServerDownloadAction {
            suwayomiClient.deleteDownloadedChapters(chapterIds)
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        if (successState?.isServerBacked == true) {
            return suwayomiClient.getCategories()
                .map { it.toCategory() }
        }
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        if (successState?.isServerBacked == true) {
            return suwayomiClient.getMangaCategories(manga.id.toInt()).map { it.id.toLong() }
        }
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        if (successState?.isServerBacked == true) {
            screenModelScope.launchIO {
                runCatching {
                    val updatedManga = if (!manga.favorite) {
                        suwayomiClient.updateMangaLibrary(
                            mangaId = manga.id.toInt(),
                            inLibrary = true,
                        )
                    } else {
                        null
                    }
                    suwayomiClient.updateMangaCategories(
                        mangaId = manga.id.toInt(),
                        categoryIds = categories.toServerCategoryIds(),
                    )
                    if (updatedManga != null) {
                        updateSuccessState {
                            it.copy(manga = updatedManga.toDomainManga(it.manga.fetchEstimate()))
                        }
                    }
                    loadServerMangaState()
                    ServerStateSync.requestRefresh()
                }.onFailure { error ->
                    logcat(LogPriority.ERROR, error) { "Failed to add Suwayomi manga to categories" }
                    withUIContext {
                        snackbarHostState.showSnackbar(message = with(context) { error.formattedMessage })
                    }
                }
            }
            return
        }

        moveMangaToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        screenModelScope.launchIO {
            if (successState?.isServerBacked == true) {
                runCatching {
                    suwayomiClient.updateMangaCategories(
                        mangaId = mangaId.toInt(),
                        categoryIds = categoryIds.toServerCategoryIds(),
                    )
                    loadServerMangaState()
                    ServerStateSync.requestRefresh()
                }.onFailure { error ->
                    logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi manga categories" }
                    withUIContext {
                        snackbarHostState.showSnackbar(message = with(context) { error.formattedMessage })
                    }
                }
                return@launchIO
            }
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    private fun SuwayomiCategoryDto.toCategory(): Category {
        return Category(
            id = id.toLong(),
            name = name,
            order = order.toLong(),
            flags = 0L,
        )
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    private fun List<Long>.toServerCategoryIds(): List<Int> {
        return filterNot { it == Category.UNCATEGORIZED_ID }
            .map { it.toInt() }
    }

    // Manga info - end

    // Chapters list - start

    private fun List<Chapter>.toChapterListItems(manga: Manga): List<ChapterList.Item> {
        return map { chapter ->
            ChapterList.Item(
                chapter = chapter.withPendingReadState(),
                downloadState = Download.State.NOT_DOWNLOADED,
                downloadProgress = 0,
                selected = chapter.id in selectedChapterIds,
                deviceCopyState = DeviceCopyState.NONE,
                deviceCopyProgress = 0,
            )
        }
    }

    private fun List<SuwayomiChapterDto>.toServerChapterListItems(manga: Manga): List<ChapterList.Item> {
        return map { serverChapter ->
            val chapter = serverChapter.toDomainChapter()

            ChapterList.Item(
                chapter = chapter.withPendingReadState(),
                downloadState = if (serverChapter.isDownloaded) {
                    Download.State.DOWNLOADED
                } else {
                    Download.State.NOT_DOWNLOADED
                },
                downloadProgress = 0,
                selected = chapter.id in selectedChapterIds,
                deviceCopyState = deviceCopyStatesByChapterId[chapter.id] ?: DeviceCopyState.NONE,
                deviceCopyProgress = deviceCopyProgressByChapterId[chapter.id] ?: 0,
            ).withServerDownloadState()
        }
    }

    private fun ChapterList.Item.withServerDownloadState(): ChapterList.Item {
        val activeDownload = serverActiveDownloadsByChapterId[chapter.id]
        return when {
            activeDownload != null -> copy(
                downloadState = activeDownload.state.toServerDownloadState(),
                downloadProgress = activeDownload.progress.toDownloadProgress(),
            )
            downloadState != Download.State.DOWNLOADING && downloadState != Download.State.QUEUE -> this
            else -> copy(
                downloadState = Download.State.NOT_DOWNLOADED,
                downloadProgress = 0,
            )
        }
    }

    private fun Chapter.withPendingReadState(): Chapter {
        val pendingRead = pendingReadStatesByChapterId[id] ?: return this
        return copy(
            read = pendingRead,
            lastPageRead = if (pendingRead) 0L else lastPageRead,
        )
    }

    private fun String.toServerDownloadState(): Download.State {
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

    private fun Double.toDownloadProgress(): Int {
        return when {
            this <= 1.0 -> (this * 100).toInt()
            else -> toInt()
        }.coerceIn(0, 100)
    }

    private fun SuwayomiMangaDto.toDomainManga(
        fetchEstimate: SuwayomiFetchEstimate? = null,
    ): Manga {
        return Manga(
            id = id.toLong(),
            source = sourceId.toLongOrNull() ?: 0L,
            favorite = inLibrary,
            lastUpdate = chaptersLastFetchedAt?.seconds?.inWholeMilliseconds
                ?: latestFetchedChapter?.fetchedAt?.seconds?.inWholeMilliseconds
                ?: 0L,
            nextUpdate = fetchEstimate?.nextUpdate ?: 0L,
            fetchInterval = fetchEstimate?.fetchInterval ?: 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = serverChapterFlags(),
            coverLastModified = serverCoverLastModified(),
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = normalizedGenre(),
            status = status.toDomainStatus(),
            thumbnailUrl = thumbnailUrl?.let { resolveServerUrl(suwayomiProvider.baseUrl(), it) },
            updateStrategy = updateStrategy.toDomainUpdateStrategy(),
            initialized = initialized,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            notes = serverNotes(),
            memo = buildJsonObject {
                realUrl?.let { put(SUWAYOMI_MANGA_REAL_URL_META_KEY, it) }
            },
        )
    }

    private fun Manga.fetchEstimate(): SuwayomiFetchEstimate? {
        return if (nextUpdate > 0L && fetchInterval > 0) {
            SuwayomiFetchEstimate(
                nextUpdate = nextUpdate,
                fetchInterval = fetchInterval,
            )
        } else {
            null
        }
    }

    private fun SuwayomiMangaDto.serverNotes(): String {
        return meta.firstOrNull { it.key == SERVER_MANGA_NOTES_META_KEY }?.value.orEmpty()
    }

    private fun SuwayomiChapterDto.toDomainChapter(): Chapter {
        return Chapter(
            id = id.toLong(),
            mangaId = mangaId.toLong(),
            read = isRead,
            bookmark = isBookmarked,
            lastPageRead = lastPageRead.toLong(),
            dateFetch = fetchedAt.toLongOrNull()?.seconds?.inWholeMilliseconds ?: 0L,
            sourceOrder = sourceOrder.toLong(),
            url = url,
            name = name,
            dateUpload = uploadDate,
            chapterNumber = chapterNumber.toDouble(),
            scanlator = scanlator,
            lastModifiedAt = 0L,
            version = 0L,
            memo = JsonObject.EMPTY,
        )
    }

    private fun SuwayomiMangaDto.serverChapterFlags(): Long {
        return meta.firstOrNull { it.key == SERVER_CHAPTER_FLAGS_META_KEY }
            ?.value
            ?.toLongOrNull()
            ?: defaultServerChapterFlags()
    }

    private fun SuwayomiMangaDto.excludedScanlators(): Set<String> {
        val value = meta.firstOrNull { it.key == SERVER_EXCLUDED_SCANLATORS_META_KEY }?.value ?: return emptySet()
        return runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), value).toSet()
        }.getOrDefault(emptySet())
    }

    private fun defaultServerChapterFlags(): Long {
        return 0L
            .setChapterFlag(libraryPreferences.filterChapterByRead.get(), Manga.CHAPTER_UNREAD_MASK)
            .setChapterFlag(libraryPreferences.filterChapterByDownloaded.get(), Manga.CHAPTER_DOWNLOADED_MASK)
            .setChapterFlag(
                libraryPreferences.filterChapterByLocalDownloaded.get(),
                Manga.CHAPTER_LOCAL_DOWNLOADED_MASK,
            )
            .setChapterFlag(libraryPreferences.filterChapterByBookmarked.get(), Manga.CHAPTER_BOOKMARKED_MASK)
            .setChapterFlag(Manga.CHAPTER_SORTING_NUMBER, Manga.CHAPTER_SORTING_MASK)
            .setChapterFlag(Manga.CHAPTER_SORT_DESC, Manga.CHAPTER_SORT_DIR_MASK)
            .setChapterFlag(libraryPreferences.displayChapterByNameOrNumber.get(), Manga.CHAPTER_DISPLAY_MASK)
    }

    private fun Long.setChapterFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private fun MangaStatus.toDomainStatus(): Long {
        return when (this) {
            MangaStatus.UNKNOWN -> SManga.UNKNOWN
            MangaStatus.ONGOING -> SManga.ONGOING
            MangaStatus.COMPLETED -> SManga.COMPLETED
            MangaStatus.LICENSED -> SManga.LICENSED
            MangaStatus.PUBLISHING_FINISHED -> SManga.PUBLISHING_FINISHED
            MangaStatus.CANCELLED -> SManga.CANCELLED
            MangaStatus.ON_HIATUS -> SManga.ON_HIATUS
        }.toLong()
    }

    private fun SuwayomiUpdateStrategy.toDomainUpdateStrategy(): eu.kanade.tachiyomi.source.model.UpdateStrategy {
        return when (this) {
            SuwayomiUpdateStrategy.ALWAYS_UPDATE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE
            SuwayomiUpdateStrategy.ONLY_FETCH_ONCE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun getBookmarkedChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> chapter.bookmark && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (successState.isServerBacked) {
                runServerDownloadAction {
                    suwayomiClient.enqueueChapterDownloads(chapters.map { it.id.toInt() })
                    suwayomiClient.startDownloader()
                }
                return@launchNonCancellable
            }

            toggleAllSelection(false)
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        val state = successState
        if (state?.isServerBacked == true) {
            val isServerAction = action == ChapterDownloadAction.START ||
                action == ChapterDownloadAction.START_NOW ||
                action == ChapterDownloadAction.CANCEL ||
                action == ChapterDownloadAction.DELETE
            if (isServerAction && state.staleSnapshot != null) {
                screenModelScope.launchNonCancellable {
                    snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.server_offline_actions_disabled),
                    )
                }
                return
            }
            when (action) {
                ChapterDownloadAction.START -> {
                    startDownload(items.map { it.chapter }, false)
                }
                ChapterDownloadAction.START_NOW -> {
                    val chapter = items.singleOrNull()?.chapter ?: return
                    startDownload(listOf(chapter), true)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterId = items.singleOrNull()?.id ?: return
                    cancelDownload(chapterId)
                }
                ChapterDownloadAction.DELETE -> {
                    deleteChapters(items.map { it.chapter })
                }
                ChapterDownloadAction.SAVE_DEVICE,
                ChapterDownloadAction.REFRESH_DEVICE,
                -> {
                    if (state.staleSnapshot != null) {
                        screenModelScope.launchNonCancellable {
                            snackbarHostState.showSnackbar(
                                message = context.stringResource(MR.strings.save_device_copy_offline_disabled),
                            )
                        }
                        return
                    }
                    items.forEach { item ->
                        ClientDeviceChapterCopyWorker.enqueueSave(
                            context = context,
                            mangaTitle = state.manga.title,
                            chapterId = item.id.toInt(),
                        )
                    }
                    val savingIds = items.map { it.id }.toSet()
                    deviceCopyStatesByChapterId = deviceCopyStatesByChapterId + savingIds.associateWith {
                        DeviceCopyState.DOWNLOADING
                    }
                    deviceCopyProgressByChapterId = deviceCopyProgressByChapterId + savingIds.associateWith { 0 }
                    updateSuccessState { current ->
                        current.copy(
                            chapters = current.chapters.map { chapterItem ->
                                if (chapterItem.id in savingIds) {
                                    chapterItem.copy(
                                        deviceCopyState = DeviceCopyState.DOWNLOADING,
                                        deviceCopyProgress = 0,
                                    )
                                } else {
                                    chapterItem
                                }
                            },
                        )
                    }
                    toggleAllSelection(false)
                }
                ChapterDownloadAction.REMOVE_DEVICE -> {
                    val removedIds = items.map { it.id }.toSet()
                    items.forEach { item ->
                        ClientDeviceChapterCopyWorker.enqueueRemove(
                            context = context,
                            mangaId = state.manga.id.toInt(),
                            chapterId = item.id.toInt(),
                        )
                    }
                    deviceCopyStatesByChapterId = deviceCopyStatesByChapterId - removedIds
                    deviceCopyProgressByChapterId = deviceCopyProgressByChapterId - removedIds
                    updateSuccessState { current ->
                        current.copy(
                            chapters = current.chapters.map { chapterItem ->
                                if (chapterItem.id in removedIds) {
                                    chapterItem.copy(deviceCopyState = DeviceCopyState.NONE, deviceCopyProgress = 0)
                                } else {
                                    chapterItem
                                }
                            },
                        )
                    }
                    toggleAllSelection(false)
                }
            }
            return
        }

        toggleAllSelection(false)
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_CHAPTERS -> getUnreadChaptersSorted().take(25)
            DownloadAction.UNREAD_CHAPTERS -> getUnreadChapters()
            DownloadAction.BOOKMARKED_CHAPTERS -> getBookmarkedChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        if (successState?.isServerBacked == true) {
            screenModelScope.launchNonCancellable {
                runServerDownloadAction {
                    suwayomiClient.dequeueChapterDownloads(listOf(chapterId.toInt()))
                }
            }
            return
        }

        toggleAllSelection(false)
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        screenModelScope.launchIO {
            if (successState?.isServerBacked == true) {
                try {
                    val chaptersToUpdate = chapters.filter { it.read != read || (!read && it.lastPageRead > 0) }
                    if (successState?.staleSnapshot != null) {
                        queueServerReadStates(chaptersToUpdate, read)
                        applyPendingReadStateToUi(chaptersToUpdate.map { it.id }.toSet(), read)
                    } else {
                        suwayomiClient.updateChaptersRead(
                            chapterIds = chaptersToUpdate.map { it.id.toInt() },
                            isRead = read,
                        )
                        syncTrackerProgressAfterReadStateChange(
                            read = read,
                            changedMangaIds = chaptersToUpdate.map { mangaId.toInt() },
                            trackProgress = suwayomiClient::trackProgress,
                            onFailure = { error ->
                                logcat(LogPriority.ERROR, error) { "Failed to update server tracker progress" }
                            },
                        )
                        loadServerMangaState()
                        ServerStateSync.requestRefresh()
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to update server chapter read state" }
                    if (e.isSuwayomiServerUnavailable()) {
                        val chaptersToUpdate = chapters.filter { it.read != read || (!read && it.lastPageRead > 0) }
                        queueServerReadStates(chaptersToUpdate, read)
                        applyPendingReadStateToUi(chaptersToUpdate.map { it.id }.toSet(), read)
                    } else {
                        snackbarHostState.showSnackbar(
                            message = with(context) { e.formattedMessage },
                            duration = SnackbarDuration.Short,
                            withDismissAction = true,
                        )
                    }
                }
                return@launchIO
            }

            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )
        }
    }

    private suspend fun queueServerReadStates(chapters: List<Chapter>, read: Boolean) {
        chapters.forEach { chapter ->
            pendingReadStateStore.upsert(
                serverKey = suwayomiProvider.serverKey(),
                mangaId = chapter.mangaId.toInt(),
                chapterId = chapter.id.toInt(),
                isRead = read,
            )
        }
        pendingReadStatesByChapterId = pendingReadStateStore
            .getForManga(suwayomiProvider.serverKey(), mangaId.toInt())
            .associate { it.chapterId.toLong() to it.isRead }
    }

    private suspend fun applyPendingReadStateToUi(chapterIds: Set<Long>, read: Boolean) {
        val pendingCount = pendingReadStateStore.count(suwayomiProvider.serverKey()).toInt()
        updateSuccessState { current ->
            current.copy(
                chapters = current.chapters.map { chapterItem ->
                    if (chapterItem.id in chapterIds) {
                        chapterItem.copy(
                            chapter = chapterItem.chapter.copy(
                                read = read,
                                lastPageRead = if (read) 0L else chapterItem.chapter.lastPageRead,
                            ),
                        )
                    } else {
                        chapterItem
                    }
                },
                pendingReadStateCount = pendingCount,
            )
        }
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            if (successState?.isServerBacked == true) {
                try {
                    val chaptersToUpdate = chapters
                        .filterNot { it.bookmark == bookmarked }
                    suwayomiClient.updateChaptersBookmark(
                        chapterIds = chaptersToUpdate.map { it.id.toInt() },
                        isBookmarked = bookmarked,
                    )
                    loadServerMangaState()
                    ServerStateSync.requestRefresh()
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to update server chapter bookmark state" }
                    snackbarHostState.showSnackbar(
                        message = with(context) { e.formattedMessage },
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                }
                return@launchIO
            }

            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    if (state.isServerBacked) {
                        runServerDownloadAction {
                            suwayomiClient.deleteDownloadedChapters(chapters.map { it.id.toInt() })
                        }
                        return@launchNonCancellable
                    }
                    toggleAllSelection(false)
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private suspend fun runServerDownloadAction(action: suspend () -> Unit) {
        try {
            action()
            loadServerMangaState()
            toggleAllSelection(false)
            ServerStateSync.requestRefresh()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to update Suwayomi download state" }
            snackbarHostState.showSnackbar(
                message = with(context) { e.formattedMessage },
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        if (successState?.isServerBacked == true) {
            setServerChapterFlags(manga.chapterFlags.setChapterFlag(flag, Manga.CHAPTER_UNREAD_MASK))
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        if (successState?.isServerBacked == true) {
            setServerChapterFlags(manga.chapterFlags.setChapterFlag(flag, Manga.CHAPTER_DOWNLOADED_MASK))
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the local device copy filter and requests an UI update.
     * @param state whether to display only locally downloaded chapters or all chapters.
     */
    fun setLocalDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_LOCAL_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_LOCAL_DOWNLOADED
        }

        if (successState?.isServerBacked == true) {
            setServerChapterFlags(manga.chapterFlags.setChapterFlag(flag, Manga.CHAPTER_LOCAL_DOWNLOADED_MASK))
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetLocalDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        if (successState?.isServerBacked == true) {
            setServerChapterFlags(manga.chapterFlags.setChapterFlag(flag, Manga.CHAPTER_BOOKMARKED_MASK))
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        if (successState?.isServerBacked == true) {
            setServerChapterFlags(manga.chapterFlags.setChapterFlag(mode, Manga.CHAPTER_DISPLAY_MASK))
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        if (successState?.isServerBacked == true) {
            val newFlags = manga.chapterFlags.let {
                if (manga.sorting == sort) {
                    val orderFlag = if (manga.sortDescending()) {
                        Manga.CHAPTER_SORT_ASC
                    } else {
                        Manga.CHAPTER_SORT_DESC
                    }
                    it.setChapterFlag(orderFlag, Manga.CHAPTER_SORT_DIR_MASK)
                } else {
                    it
                        .setChapterFlag(sort, Manga.CHAPTER_SORTING_MASK)
                        .setChapterFlag(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DIR_MASK)
                }
            }
            setServerChapterFlags(newFlags)
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (successState?.isServerBacked == true) {
                snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
                return@launchNonCancellable
            }
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        if (successState?.isServerBacked == true) {
            setServerChapterFlags(defaultServerChapterFlags())
            return
        }
        screenModelScope.launchNonCancellable {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    private fun setServerChapterFlags(chapterFlags: Long) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            runCatching {
                suwayomiClient.setMangaMeta(
                    mangaId = manga.id.toInt(),
                    key = SERVER_CHAPTER_FLAGS_META_KEY,
                    value = chapterFlags.toString(),
                )
                updateSuccessState { it.copy(manga = it.manga.copy(chapterFlags = chapterFlags)) }
                ServerStateSync.requestRefresh()
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi manga chapter settings" }
                snackbarHostState.showSnackbar(
                    message = with(context) { error.formattedMessage },
                    duration = SnackbarDuration.Short,
                    withDismissAction = true,
                )
            }
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val selectedIndex = successState.processedChapters.indexOfFirst { it.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.id, selected)

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
                                selectedChapterIds.add(inbetweenItem.id)
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
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        data class SetFetchInterval(val manga: Manga) : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(target = manga, current = duplicate)) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        if (successState?.isServerBacked == true) {
            setServerExcludedScanlators(excludedScanlators)
            return
        }
        screenModelScope.launchIO {
            setExcludedScanlators.await(mangaId, excludedScanlators)
        }
    }

    private fun setServerExcludedScanlators(excludedScanlators: Set<String>) {
        val manga = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            runCatching {
                suwayomiClient.setMangaMeta(
                    mangaId = manga.id.toInt(),
                    key = SERVER_EXCLUDED_SCANLATORS_META_KEY,
                    value = Json.encodeToString(
                        ListSerializer(String.serializer()),
                        excludedScanlators.sortedWith(String.CASE_INSENSITIVE_ORDER),
                    ),
                )
                updateSuccessState { it.copy(excludedScanlators = excludedScanlators) }
                ServerStateSync.requestRefresh()
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi manga excluded scanlators" }
                snackbarHostState.showSnackbar(
                    message = with(context) { error.formattedMessage },
                    duration = SnackbarDuration.Short,
                    withDismissAction = true,
                )
            }
        }
    }

    private companion object {
        const val SERVER_CHAPTER_FLAGS_META_KEY = "amatsubu.chapterFlags"
        const val SERVER_EXCLUDED_SCANLATORS_META_KEY = "amatsubu.excludedScanlators"
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val message: String,
        ) : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val isFromSource: Boolean,
            val isServerBacked: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val hideMissingChapters: Boolean = false,
            val staleSnapshot: SuwayomiStaleSnapshotState? = null,
            val pendingReadStateCount: Int = 0,
        ) : State {
            val processedChapters by lazy {
                chapters.applyFilters(manga).toList()
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            val chapterListItems by lazy {
                if (hideMissingChapters) {
                    return@lazy processedChapters
                }

                processedChapters.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (manga.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.chapter.chapterNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherChapter.chapter, lowerChapter.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered()

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val localDownloadedFilter = manga.localDownloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> chapter.scanlator !in excludedScanlators }
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded } }
                    .filter { applyFilter(localDownloadedFilter) { it.isLocallyDownloaded } }
                    .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
            }
        }
    }
}

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
        val deviceCopyState: DeviceCopyState = DeviceCopyState.NONE,
        val deviceCopyProgress: Int = 0,
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
        val isLocallyDownloaded = deviceCopyState == DeviceCopyState.FRESH
    }
}

enum class DeviceCopyState {
    NONE,
    FRESH,
    STALE,
    UNVERIFIED,
    INCOMPLETE,
    DOWNLOADING,
    FAILED,
    ORPHANED,
}

private fun ClientDeviceChapterCopy.toDeviceCopyState(): DeviceCopyState {
    return when {
        status == ClientChapterCopyStatus.DOWNLOADING || status == ClientChapterCopyStatus.QUEUED -> {
            DeviceCopyState.DOWNLOADING
        }
        status == ClientChapterCopyStatus.FAILED -> DeviceCopyState.FAILED
        !isComplete || status == ClientChapterCopyStatus.INCOMPLETE -> DeviceCopyState.INCOMPLETE
        freshness == ClientChapterCopyFreshness.FRESH -> DeviceCopyState.FRESH
        freshness == ClientChapterCopyFreshness.STALE -> DeviceCopyState.STALE
        freshness == ClientChapterCopyFreshness.UNVERIFIED -> DeviceCopyState.UNVERIFIED
        freshness == ClientChapterCopyFreshness.ORPHANED -> DeviceCopyState.ORPHANED
        freshness == ClientChapterCopyFreshness.INCOMPLETE -> DeviceCopyState.INCOMPLETE
        else -> DeviceCopyState.UNVERIFIED
    }
}

private fun ClientDeviceChapterCopy.toDeviceCopyProgress(): Int {
    if (expectedPageCount <= 0) return 0
    return ((downloadedPageCount.toDouble() / expectedPageCount.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
