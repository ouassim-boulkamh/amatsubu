package eu.kanade.tachiyomi.ui.library

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyStore
import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiFetchEstimate
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiLibraryUpdateStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSnapshot
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiStaleSnapshotState
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiStatsTrackRecordDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackerDto
import eu.kanade.tachiyomi.data.suwayomi.estimateFetchInterval
import eu.kanade.tachiyomi.data.suwayomi.isLocalFolderSource
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.data.suwayomi.oldestPositive
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverCoverLastModified
import eu.kanade.tachiyomi.data.suwayomi.syncTrackerProgressAfterReadStateChange
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.common.extensions.EMPTY
import mihon.core.common.utils.mutate
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy as SuwayomiUpdateStrategy

class LibraryScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val json = Injekt.get<Json>()
    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val clientDeviceChapterCopyStore: ClientDeviceChapterCopyStore = Injekt.get()
    private val serverDownloadCounts = mutableMapOf<Long, Int>()
    private val localDownloadCounts = mutableMapOf<Long, Int>()
    private val serverSourceLanguages = mutableMapOf<Long, String>()
    private val serverSourceNames = mutableMapOf<Long, String>()
    private val serverLocalSourceIds = mutableSetOf<Long>()
    private val serverStaleSnapshotSyncedAt = mutableMapOf<Long, Long>()
    private val serverLibraryRefreshes = MutableStateFlow(0)
    private var activeDownloadChapterMangaIds: Map<Int, Long> = emptyMap()

    init {
        mutableState.update { state ->
            state.copy(activeCategoryIndex = libraryPreferences.lastUsedCategory.get())
        }
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(0.25.seconds),
                serverLibraryRefreshes.flatMapLatest { getServerCategoriesFlow() },
                getFavoritesFlow(),
                getTrackingStateFlow(),
                getLibraryItemPreferencesFlow(),
            ) { searchQuery, categories, favorites, trackingState, itemPreferences ->
                val staleSnapshot = favorites.mapNotNull { it.staleSnapshotSyncedAt }.minOrNull()
                val showSystemCategory = favorites.any { it.libraryManga.categories.contains(0) }
                val filteredFavorites = favorites
                    .applyFilters(trackingState.recordsByMangaId, trackingState.filters, itemPreferences)
                    .let { if (searchQuery == null) it else it.filter { m -> m.matches(searchQuery) } }

                LibraryData(
                    isInitialized = true,
                    showSystemCategory = showSystemCategory,
                    categories = categories,
                    favorites = filteredFavorites,
                    trackingState = trackingState,
                    hasActiveFilters = itemPreferences.hasActiveFilters || trackingState.hasActiveFilters,
                    staleSnapshot = staleSnapshot?.let(::SuwayomiStaleSnapshotState),
                )
            }
                .distinctUntilChanged()
                .collectLatest { libraryData ->
                    mutableState.update { state ->
                        state.copy(
                            libraryData = libraryData,
                            hasActiveFilters = libraryData.hasActiveFilters,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            state
                .dropWhile { !it.libraryData.isInitialized }
                .map { it.libraryData }
                .distinctUntilChanged()
                .map { data ->
                    data.favorites
                        .applyGrouping(data.categories, data.showSystemCategory)
                        .applySort(data.favoritesById, data.trackingState)
                }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            groupedFavorites = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs.changes(),
            libraryPreferences.categoryNumberOfItems.changes(),
            libraryPreferences.showContinueReadingButton.changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        ServerStateSync.refreshes
            .onEach { refreshServerLibrary() }
            .launchIn(screenModelScope)

        suwayomiProvider.liveStatusClient.libraryUpdateStatusFlow()
            .onEach { status ->
                mutableState.update { state ->
                    state.copy(libraryUpdateStatus = status.toLibraryUpdateState())
                }
            }
            .launchIn(screenModelScope)

        suwayomiProvider.liveStatusClient.downloadStatusFlow()
            .onEach(::refreshLibraryWhenDownloadLeavesQueue)
            .launchIn(screenModelScope)
    }

    private fun refreshLibraryWhenDownloadLeavesQueue(status: SuwayomiDownloadStatusDto) {
        val nextActiveChapterMangaIds = status.queue.associate { it.chapter.id to it.manga.id.toLong() }
        val removedMangaIds = activeDownloadChapterMangaIds
            .filterKeys { it !in nextActiveChapterMangaIds }
            .values
            .toSet()
        activeDownloadChapterMangaIds = nextActiveChapterMangaIds

        val libraryMangaIds = state.value.libraryData.favoritesById.keys
        if (removedMangaIds.any { it in libraryMangaIds }) {
            refreshServerLibrary()
        }
    }

    private fun List<LibraryItem>.applyFilters(
        trackingRecords: Map<Long, List<SuwayomiStatsTrackRecordDto>>,
        trackingFilters: Map<Int, TriState>,
        preferences: ItemPreferences,
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = { item ->
            trackingFilters.all { (trackerId, filter) ->
                applyFilter(filter) {
                    trackingRecords[item.id].orEmpty().any { it.trackerId == trackerId }
                }
            }
        }

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }
    }

    private fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
    ): Map<Category, List</* LibraryItem */ Long>> {
        return groupServerLibraryMangaByCategory(
            favorites = map { it.libraryManga },
            categories = categories,
            showSystemCategory = showSystemCategory,
        )
    }

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackingState: LibraryTrackingState,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
            val title1 = manga1.libraryManga.manga.title.lowercase()
            val title2 = manga2.libraryManga.manga.title.lowercase()
            title1.compareToWithCollator(title2)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            trackingState.recordsByMangaId.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else -> entry.value.map { it.score }.average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
            when (this.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(manga1, manga2)
                }
                LibrarySort.Type.LastRead -> {
                    manga1.libraryManga.lastRead.compareTo(manga2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    manga1.libraryManga.manga.lastUpdate.compareTo(manga2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                    manga1.libraryManga.unreadCount == 0L -> if (this.isAscending) 1 else -1
                    manga2.libraryManga.unreadCount == 0L -> if (this.isAscending) -1 else 1
                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    manga1.libraryManga.latestUpload.compareTo(manga2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    manga1.libraryManga.chapterFetchedAt.compareTo(manga2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    manga1.libraryManga.manga.dateAdded.compareTo(manga2.libraryManga.manga.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == LibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed.get()))
            }

            val manga = value.mapNotNull { favoritesById[it] }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            manga.sortedWith(comparator).map { it.id }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.localDownloadBadge.changes(),
            libraryPreferences.unreadBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            libraryPreferences.autoUpdateMangaRestrictions.changes(),

            preferences.downloadedOnly.changes(),
            libraryPreferences.filterDownloaded.changes(),
            libraryPreferences.filterUnread.changes(),
            libraryPreferences.filterStarted.changes(),
            libraryPreferences.filterBookmarked.changes(),
            libraryPreferences.filterCompleted.changes(),
            libraryPreferences.filterIntervalCustom.changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                localDownloadBadge = it[1] as Boolean,
                unreadBadge = it[2] as Boolean,
                localBadge = it[3] as Boolean,
                languageBadge = it[4] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (it[5] as Set<*>),
                globalFilterDownloaded = it[6] as Boolean,
                filterDownloaded = it[7] as TriState,
                filterUnread = it[8] as TriState,
                filterStarted = it[9] as TriState,
                filterBookmarked = it[10] as TriState,
                filterCompleted = it[11] as TriState,
                filterIntervalCustom = it[12] as TriState,
            )
        }
    }

    private fun getTrackingStateFlow(): Flow<LibraryTrackingState> {
        return serverLibraryRefreshes
            .flatMapLatest { getServerLibraryTrackingFlow() }
            .flatMapLatest { trackingState ->
                val trackerIds = trackingState.loggedInTrackers.map { it.id }
                if (trackerIds.isEmpty()) {
                    return@flatMapLatest flowOf(trackingState)
                }
                val filterFlows = trackerIds.map { trackerId ->
                    libraryPreferences.filterTracking(trackerId).changes().map { trackerId to it }
                }
                combine(filterFlows) { filters ->
                    trackingState.copy(filters = filters.toMap())
                }
            }
    }

    private fun getServerLibraryTrackingFlow(): Flow<LibraryTrackingState> = flow {
        val trackingData = runCatching {
            suwayomiClient.getLibraryTrackingData()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load server library tracking data" }
        }.getOrNull()

        val loggedInTrackers = trackingData
            ?.trackers
            ?.nodes
            .orEmpty()
            .filter { it.isLoggedIn }
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toSet()
        val recordsByMangaId = trackingData
            ?.libraryTrackingMangas
            ?.nodes
            .orEmpty()
            .associate { manga ->
                manga.id.toLong() to manga.trackRecords.nodes.fastFilter { it.trackerId in loggedInTrackerIds }
            }

        emit(
            LibraryTrackingState(
                loggedInTrackers = loggedInTrackers,
                recordsByMangaId = recordsByMangaId,
            ),
        )
    }

    private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
        return combine(
            serverLibraryRefreshes.flatMapLatest { getServerLibraryMangaFlow() },
            getLibraryItemPreferencesFlow(),
        ) { libraryManga, preferences ->
            libraryManga.toLibraryItems(preferences)
        }
    }

    fun refreshServerLibrary() {
        serverLibraryRefreshes.update { it + 1 }
    }

    suspend fun updateServerLibrary(category: Category? = null): Boolean {
        return try {
            val started = category
                ?.let { suwayomiClient.updateCategoryMangas(it.id.toInt()) }
                ?: suwayomiClient.updateLibraryMangas()
            libraryPreferences.lastUpdatedTimestamp.set(System.currentTimeMillis())
            refreshServerLibrary()
            ServerStateSync.requestRefresh()
            if (started) {
                ServerNotificationSyncJob.schedulePromptReconciliation(Injekt.get<Application>())
            }
            started
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to trigger server library update" }
            false
        }
    }

    fun stopServerLibraryUpdate() {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.stopLibraryUpdate()
            }.onSuccess {
                refreshServerLibrary()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to stop server library update" }
            }
        }
    }

    private fun getServerCategoriesFlow(): Flow<List<Category>> {
        val serverCategoriesFlow = flow {
            val result = runCatching {
                suwayomiClient.getCategories()
                    .ifEmpty { listOf(SuwayomiCategoryDto(id = 0, name = "Default")) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to load server library categories" }
                if (error.isSuwayomiServerUnavailable()) {
                    mutableState.update { it.copy(serverUnavailable = true) }
                }
            }.onSuccess {
                mutableState.update { it.copy(serverUnavailable = false) }
            }
            emit(result.getOrDefault(listOf(SuwayomiCategoryDto(id = 0, name = "Default"))))
        }

        return combine(
            serverCategoriesFlow,
            libraryPreferences.sortingMode.changes(),
            libraryPreferences.categorizedDisplaySettings.changes(),
            libraryPreferences.categorySortingModes.changes(),
        ) { serverCategories, sortingMode, categorizedDisplaySettings, categorySortingModes ->
            val globalFlags = sortingMode.type + sortingMode.direction

            serverCategories.map { category ->
                val flags = if (categorizedDisplaySettings) {
                    categorySortingModes[category.id.toLong()]?.flag ?: globalFlags
                } else {
                    globalFlags
                }
                category.toCategory(flags)
            }
        }
    }

    private fun getServerLibraryMangaFlow(): Flow<List<LibraryManga>> = flow {
        var serverUnavailable = false
        val categories = runCatching {
            suwayomiClient.getCategories()
                .ifEmpty { listOf(SuwayomiCategoryDto(id = 0, name = "Default")) }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load server library categories" }
            serverUnavailable = error.isSuwayomiServerUnavailable()
        }.getOrDefault(listOf(SuwayomiCategoryDto(id = 0, name = "Default")))

        serverDownloadCounts.clear()
        localDownloadCounts.clear()
        serverSourceLanguages.clear()
        serverSourceNames.clear()
        serverLocalSourceIds.clear()
        serverStaleSnapshotSyncedAt.clear()

        if (serverUnavailable) {
            val snapshot = suwayomiClient.getLibraryMangasSnapshot()
            mutableState.update { it.copy(serverUnavailable = true) }
            emit(snapshot.toLibraryMangaSnapshotItems())
            return@flow
        }

        runCatching {
            suwayomiClient.sourceList().forEach { source ->
                val sourceId = source.id.toLongOrNull()
                    ?: 0L.takeIf { source.isLocalFolderSource() }
                sourceId?.let {
                    serverSourceLanguages[sourceId] = source.lang
                    serverSourceNames[sourceId] = source.displayName.ifBlank { source.name }
                    if (source.isLocalFolderSource()) {
                        serverLocalSourceIds += sourceId
                    }
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load server source languages for Library" }
        }

        val libraryChaptersByMangaId = runCatching {
            suwayomiClient.getLibraryChapters()
                .groupBy { it.mangaId.toLong() }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error) { "Failed to load server chapter dates for Library" }
        }.getOrDefault(emptyMap())

        val mangaById = linkedMapOf<Long, LibraryManga>()
        categories.forEach { category ->
            val categoryMangasResult = runCatching {
                suwayomiClient.getCategoryMangas(category.id)
            }.recoverCatching { error ->
                if (error is CancellationException) throw error
                if (category.id == 0) {
                    logcat(LogPriority.ERROR, error) {
                        "Failed to load default category manga; falling back to full server library"
                    }
                    suwayomiClient.getLibraryMangas()
                } else {
                    throw error
                }
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load server category ${category.id} manga" }
                serverUnavailable = serverUnavailable || error.isSuwayomiServerUnavailable()
            }
            val categorySnapshot = if (categoryMangasResult.isFailure && serverUnavailable) {
                if (category.id == 0) {
                    suwayomiClient.getLibraryMangasSnapshot()
                } else {
                    suwayomiClient.getCategoryMangasSnapshot(category.id)
                }
            } else {
                null
            }
            val categoryMangas = categoryMangasResult.getOrNull()
                ?: categorySnapshot?.value
                ?: emptyList()
            val categorySyncedAt = categorySnapshot?.syncedAt

            categoryMangas.forEach { manga ->
                val mangaId = manga.id.toLong()
                val existing = mangaById[mangaId]
                val categoriesForManga = existing?.categories.orEmpty() + category.id.toLong()
                val baseManga = manga.toDomainManga()
                val excludedScanlators = manga.serverExcludedScanlators(json)
                val chapterSnapshot = if (serverUnavailable && libraryChaptersByMangaId[mangaId].isNullOrEmpty()) {
                    suwayomiClient.getChaptersSnapshot(manga.id)
                } else {
                    null
                }
                val snapshotSyncedAt = categorySyncedAt.oldestPositive(chapterSnapshot?.syncedAt)
                val chaptersForAggregate = libraryChaptersByMangaId[mangaId] ?: chapterSnapshot?.value
                val deviceCopyChapterIds = clientDeviceChapterCopyStore
                    .getCopiesForManga(suwayomiProvider.serverKey(), manga.id)
                    .filter { it.isComplete }
                    .map { it.chapterId }
                    .toSet()
                val chapterAggregate = chaptersForAggregate
                    ?.toLibraryChapterAggregate(excludedScanlators)
                    ?: manga.toFallbackLibraryChapterAggregate()
                serverDownloadCounts[mangaId] = chapterAggregate.downloadCount
                localDownloadCounts[mangaId] = chaptersForAggregate
                    ?.deviceCopyCount(
                        excludedScanlators = excludedScanlators,
                        deviceCopyChapterIds = deviceCopyChapterIds,
                    )
                    ?: deviceCopyChapterIds.size
                snapshotSyncedAt?.let { syncedAt ->
                    serverStaleSnapshotSyncedAt[mangaId] =
                        serverStaleSnapshotSyncedAt[mangaId].oldestPositive(syncedAt)!!
                }
                mangaById[mangaId] = manga.toLibraryManga(
                    categories = categoriesForManga.distinct(),
                    chapterAggregate = chapterAggregate,
                    fetchEstimate = chaptersForAggregate?.estimateFetchInterval(baseManga),
                )
            }
        }
        mutableState.update { it.copy(serverUnavailable = serverUnavailable) }
        emit(mangaById.values.toList())
    }

    private suspend fun SuwayomiSnapshot<List<SuwayomiMangaDto>>?.toLibraryMangaSnapshotItems(): List<LibraryManga> {
        val snapshot = this ?: return emptyList()
        return snapshot.value.map { manga ->
            val chaptersSnapshot = suwayomiClient.getChaptersSnapshot(manga.id)
            val baseManga = manga.toDomainManga()
            val excludedScanlators = manga.serverExcludedScanlators(json)
            val chapters = chaptersSnapshot?.value
            val deviceCopyChapterIds = clientDeviceChapterCopyStore
                .getCopiesForManga(suwayomiProvider.serverKey(), manga.id)
                .filter { it.isComplete }
                .map { it.chapterId }
                .toSet()
            val chapterAggregate = chapters
                ?.toLibraryChapterAggregate(excludedScanlators)
                ?: manga.toFallbackLibraryChapterAggregate()
            serverDownloadCounts[manga.id.toLong()] = chapterAggregate.downloadCount
            localDownloadCounts[manga.id.toLong()] = chapters
                ?.deviceCopyCount(
                    excludedScanlators = excludedScanlators,
                    deviceCopyChapterIds = deviceCopyChapterIds,
                )
                ?: deviceCopyChapterIds.size
            serverStaleSnapshotSyncedAt[manga.id.toLong()] =
                snapshot.syncedAt.oldestPositive(chaptersSnapshot?.syncedAt)!!
            manga.toLibraryManga(
                categories = listOf(Category.UNCATEGORIZED_ID),
                chapterAggregate = chapterAggregate,
                fetchEstimate = chapters?.estimateFetchInterval(baseManga),
            )
        }
    }

    private fun List<LibraryManga>.toLibraryItems(preferences: ItemPreferences): List<LibraryItem> {
        return map { manga ->
            val serverDownloadCount = serverDownloadCounts[manga.manga.id] ?: 0
            val localDownloadCount = localDownloadCounts[manga.manga.id] ?: 0
            val downloadCount = maxOf(serverDownloadCount, localDownloadCount)
            val isLocal = manga.manga.source in serverLocalSourceIds
            val sourceName = serverSourceNames[manga.manga.source].orEmpty()
            LibraryItem(
                libraryManga = manga,
                downloadCount = downloadCount,
                unreadCount = manga.unreadCount,
                isLocal = isLocal,
                sourceName = sourceName,
                badges = LibraryItem.Badges(
                    downloadCount = if (preferences.downloadBadge) serverDownloadCount else 0,
                    localDownloadCount = if (preferences.localDownloadBadge) localDownloadCount else 0,
                    unreadCount = if (preferences.unreadBadge) manga.unreadCount else 0,
                    isLocal = preferences.localBadge && isLocal,
                    sourceLanguage = if (preferences.languageBadge) {
                        serverSourceLanguages[manga.manga.source].orEmpty()
                    } else {
                        ""
                    },
                ),
                staleSnapshotSyncedAt = serverStaleSnapshotSyncedAt[manga.manga.id],
            )
        }
    }

    private fun SuwayomiCategoryDto.toCategory(flags: Long): Category {
        return Category(
            id = id.toLong(),
            name = name,
            order = order.toLong(),
            flags = flags,
        )
    }

    private fun SuwayomiMangaDto.toLibraryManga(
        categories: List<Long>,
        chapterAggregate: ServerLibraryChapterAggregate,
        fetchEstimate: SuwayomiFetchEstimate?,
    ): LibraryManga {
        return LibraryManga(
            manga = toDomainManga(fetchEstimate).copy(favorite = true),
            categories = categories,
            totalChapters = chapterAggregate.totalChapters,
            readCount = chapterAggregate.readCount,
            bookmarkCount = chapterAggregate.bookmarkCount,
            latestUpload = latestUploadedChapter?.uploadDate ?: chapterAggregate.latestUpload,
            chapterFetchedAt = latestFetchedChapter?.fetchedAt?.seconds?.inWholeMilliseconds
                ?: chapterAggregate.chapterFetchedAt,
            lastRead = latestReadChapter?.lastReadAt ?: 0L,
            unreadCount = chapterAggregate.unreadCount,
        )
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
            dateAdded = inLibraryAt ?: 0L,
            viewerFlags = 0L,
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC,
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
            notes = "",
            memo = kotlinx.serialization.json.JsonObject.EMPTY,
        )
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
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val commonCategoryIds = mangas
            .map { getCategoryIdsForManga(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }

        return state.value.displayedCategories.filter { it.id in commonCategoryIds }
    }

    suspend fun getServerNextUnreadChapter(manga: Manga): Chapter? {
        return suwayomiClient.getChapters(manga.id.toInt())
            .filterNot { it.isRead }
            .minByOrNull { it.sourceOrder }
            ?.let { chapter ->
                Chapter(
                    id = chapter.id.toLong(),
                    mangaId = chapter.mangaId.toLong(),
                    read = chapter.isRead,
                    bookmark = false,
                    lastPageRead = chapter.lastPageRead.toLong(),
                    dateFetch = chapter.fetchedAt.toLongOrNull()?.seconds?.inWholeMilliseconds ?: 0L,
                    sourceOrder = chapter.sourceOrder.toLong(),
                    url = chapter.url,
                    name = chapter.name,
                    dateUpload = chapter.uploadDate,
                    chapterNumber = chapter.chapterNumber.toDouble(),
                    scanlator = chapter.scanlator,
                    lastModifiedAt = 0L,
                    version = 0L,
                    memo = kotlinx.serialization.json.JsonObject.EMPTY,
                )
            }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategoryIdsForManga(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        val mixedCategoryIds = mangaCategories.flatten().distinct().subtract(common)

        return state.value.displayedCategories.filter { it.id in mixedCategoryIds }
    }

    /**
     * Queues the amount specified of unread chapters from the list of selected manga
     */
    fun performDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.NEXT_1_CHAPTER -> downloadNextChapters(1)
            DownloadAction.NEXT_5_CHAPTERS -> downloadNextChapters(5)
            DownloadAction.NEXT_10_CHAPTERS -> downloadNextChapters(10)
            DownloadAction.NEXT_25_CHAPTERS -> downloadNextChapters(25)
            DownloadAction.UNREAD_CHAPTERS -> downloadNextChapters(null)
            DownloadAction.BOOKMARKED_CHAPTERS -> downloadBookmarkedChapters()
        }
        clearSelection()
    }

    private fun downloadNextChapters(amount: Int?) {
        val mangas = state.value.selectedManga
        screenModelScope.launchNonCancellable {
            enqueueServerDownloads(mangas) { manga, chapters ->
                chapters
                    .filterNot { it.isRead || it.isDownloaded }
                    .sortedWith(manga.serverChapterSort(sortDescending = false))
                    .let { if (amount != null) it.take(amount) else it }
            }
        }
    }

    private fun downloadBookmarkedChapters() {
        val mangas = state.value.selectedManga
        screenModelScope.launchNonCancellable {
            enqueueServerDownloads(mangas) { manga, chapters ->
                chapters
                    .filter { it.isBookmarked && !it.isDownloaded }
                    .sortedWith(manga.serverChapterSort(sortDescending = false))
            }
        }
    }

    private suspend fun enqueueServerDownloads(
        mangas: List<Manga>,
        selectChapters: (Manga, List<SuwayomiChapterDto>) -> List<SuwayomiChapterDto>,
    ) {
        try {
            val queuedChapterIds = suwayomiClient.getDownloadStatus()
                .queue
                .map { it.chapter.id }
                .toSet()
            val chapterIds = mangas
                .flatMap { manga ->
                    selectChapters(manga, suwayomiClient.getChapters(manga.id.toInt()))
                }
                .map { it.id }
                .filterNot { it in queuedChapterIds }
                .distinct()

            if (chapterIds.isNotEmpty()) {
                suwayomiClient.enqueueChapterDownloads(chapterIds)
                suwayomiClient.startDownloader()
                refreshServerLibrary()
                ServerStateSync.requestRefresh()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Failed to enqueue server chapter downloads from Library" }
        }
    }

    private fun Manga.serverChapterSort(sortDescending: Boolean): Comparator<SuwayomiChapterDto> {
        return Comparator { chapter1, chapter2 ->
            when (sorting) {
                Manga.CHAPTER_SORTING_SOURCE -> if (sortDescending) {
                    chapter1.sourceOrder.compareTo(chapter2.sourceOrder)
                } else {
                    chapter2.sourceOrder.compareTo(chapter1.sourceOrder)
                }
                Manga.CHAPTER_SORTING_NUMBER -> if (sortDescending) {
                    chapter2.chapterNumber.compareTo(chapter1.chapterNumber)
                } else {
                    chapter1.chapterNumber.compareTo(chapter2.chapterNumber)
                }
                Manga.CHAPTER_SORTING_UPLOAD_DATE -> if (sortDescending) {
                    chapter2.uploadDate.compareTo(chapter1.uploadDate)
                } else {
                    chapter1.uploadDate.compareTo(chapter2.uploadDate)
                }
                Manga.CHAPTER_SORTING_ALPHABET -> if (sortDescending) {
                    chapter2.name.compareToWithCollator(chapter1.name)
                } else {
                    chapter1.name.compareToWithCollator(chapter2.name)
                }
                else -> chapter2.sourceOrder.compareTo(chapter1.sourceOrder)
            }
        }
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val selection = state.value.selectedManga
        screenModelScope.launchNonCancellable {
            try {
                selection.forEach { manga ->
                    val chapterIds = suwayomiClient.getChapters(manga.id.toInt())
                        .filter { it.isRead != read || (!read && it.lastPageRead > 0) }
                        .map { it.id }
                    suwayomiClient.updateChaptersRead(chapterIds, read)
                    syncTrackerProgressAfterReadStateChange(
                        read = read,
                        changedMangaIds = chapterIds.map { manga.id.toInt() },
                        trackProgress = suwayomiClient::trackProgress,
                        onFailure = { error ->
                            logcat(LogPriority.ERROR, error) { "Failed to update server tracker progress from Library" }
                        },
                    )
                }
                refreshServerLibrary()
                ServerStateSync.requestRefresh()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to update server chapter read state from Library" }
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            if (deleteFromLibrary) {
                mangas.forEach {
                    it.removeCovers(coverCache)
                    suwayomiClient.updateMangaLibrary(
                        mangaId = it.id.toInt(),
                        inLibrary = false,
                    )
                }
                refreshServerLibrary()
                ServerStateSync.requestRefresh()
            }

            if (deleteChapters) {
                mangas.forEach { manga ->
                    val downloadedChapterIds = suwayomiClient.getChapters(manga.id.toInt())
                        .filter { it.isDownloaded }
                        .map { it.id }
                    if (downloadedChapterIds.isNotEmpty()) {
                        suwayomiClient.deleteDownloadedChapters(downloadedChapterIds)
                    }
                }
                refreshServerLibrary()
                ServerStateSync.requestRefresh()
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        screenModelScope.launchNonCancellable {
            suwayomiClient.updateMangasCategories(
                mangaIds = mangaList.map { it.id.toInt() },
                addCategoryIds = addCategories.map { it.toInt() },
                removeCategoryIds = removeCategories.map { it.toInt() },
            )
            refreshServerLibrary()
            ServerStateSync.requestRefresh()
        }
    }

    private suspend fun getCategoryIdsForManga(mangaId: Long): List<Long> {
        return state.value.libraryData.favoritesById[mangaId]
            ?.libraryManga
            ?.categories
            ?: suwayomiClient.getMangaCategories(mangaId.toInt()).map { it.id.toLong() }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(screenModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(screenModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    private var lastSelectionCategory: Long? = null

    fun clearSelection() {
        lastSelectionCategory = null
        mutableState.update { it.copy(selection = setOf()) }
    }

    fun toggleSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { set ->
                if (!set.remove(manga.id)) set.add(manga.id)
            }
            lastSelectionCategory = category.id.takeIf { newSelection.isNotEmpty() }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(category: Category, manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelectionCategory != category.id) {
                    list.add(manga.id)
                    return@mutate
                }

                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga.id)

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    curMangaIndex < lastMangaIndex -> curMangaIndex..lastMangaIndex
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                selectionRange.mapNotNull { items[it] }.let(list::addAll)
            }
            lastSelectionCategory = category.id
            state.copy(selection = newSelection)
        }
    }

    fun selectAll() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).map { it.id }.let(list::addAll)
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection() {
        lastSelectionCategory = null
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove)
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun updateActiveCategoryIndex(index: Int) {
        val newIndex = mutableState.updateAndGet { state ->
            state.copy(activeCategoryIndex = index)
        }
            .coercedActiveCategoryIndex

        libraryPreferences.lastUsedCategory.set(newIndex)
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected manga
            val mangaList = state.value.selectedManga

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.displayedCategories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }

            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(state.value.selectedManga)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val localDownloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    ) {
        val hasActiveFilters = listOf(
            filterDownloaded,
            filterUnread,
            filterStarted,
            filterBookmarked,
            filterCompleted,
            filterIntervalCustom,
        ).any { it != TriState.DISABLED }
    }

    @Immutable
    data class LibraryTrackingState(
        val loggedInTrackers: List<SuwayomiTrackerDto> = emptyList(),
        val recordsByMangaId: Map</* Manga */ Long, List<SuwayomiStatsTrackRecordDto>> = emptyMap(),
        val filters: Map</* Tracker */ Int, TriState> = emptyMap(),
    ) {
        val hasActiveFilters = filters.values.any { it != TriState.DISABLED }
    }

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val trackingState: LibraryTrackingState = LibraryTrackingState(),
        val hasActiveFilters: Boolean = false,
        val staleSnapshot: SuwayomiStaleSnapshotState? = null,
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val serverUnavailable: Boolean = false,
        val searchQuery: String? = null,
        val selection: Set</* Manga */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val libraryUpdateStatus: LibraryUpdateState = LibraryUpdateState(),
        val libraryData: LibraryData = LibraryData(),
        private val activeCategoryIndex: Int = 0,
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
    ) {
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = activeCategoryIndex.coerceIn(
            minimumValue = 0,
            maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
        )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val selectedManga by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryManga?.manga } }

        val selectedMangaContainsLocal by lazy { selection.any { libraryData.favoritesById[it]?.isLocal == true } }

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            return groupedFavorites[category].orEmpty().mapNotNull { libraryData.favoritesById[it] }
        }

        fun getItemCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) groupedFavorites[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }
}

@Immutable
data class LibraryUpdateState(
    val isRunning: Boolean = false,
    val totalJobs: Int = 0,
    val finishedJobs: Int = 0,
) {
    val hasProgress: Boolean = totalJobs > 0
}

private fun SuwayomiLibraryUpdateStatusDto.toLibraryUpdateState(): LibraryUpdateState {
    return LibraryUpdateState(
        isRunning = jobsInfo.isRunning,
        totalJobs = jobsInfo.totalJobs,
        finishedJobs = jobsInfo.finishedJobs,
    )
}
