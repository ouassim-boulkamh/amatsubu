package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaCover
import eu.kanade.domain.manga.model.UpdateStrategy
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverHistoryClearAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverHistoryLibraryAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverHistoryReadAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverHistoryRefreshAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.toDomainStatus
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.util.Date

class HistoryScreenModel internal constructor(
    private val libraryPreferences: LibraryPreferences,
    private val suwayomiProvider: SuwayomiClientProvider,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val serverHistoryRefreshes = MutableStateFlow(ServerRefreshRequest())

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged(),
                serverHistoryRefreshes,
            ) { query, request -> query to request.emitErrors }
                .map { (query, emitErrors) ->
                    runCatching {
                        suwayomiClient.getReadingHistory()
                            .filter { it.matchesHistoryQuery(query) }
                            .toServerHistoryUiModels()
                    }.onFailure { error ->
                        logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi reading history" }
                        mutableState.update { it.copy(serverUnavailable = error.isSuwayomiServerUnavailable()) }
                        if (emitErrors) {
                            _events.send(
                                if (error.isSuwayomiServerUnavailable()) {
                                    Event.ServerUnavailable
                                } else {
                                    Event.InternalError
                                },
                            )
                        }
                    }.onSuccess {
                        mutableState.update { it.copy(serverUnavailable = false) }
                    }.getOrDefault(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { newList -> mutableState.update { it.copy(list = newList) } }
        }

        ServerStateSync.invalidations
            .onEach { invalidation ->
                if (invalidation.affectsAny(ServerStateEntity.History, ServerStateEntity.Library)) {
                    refreshServerState()
                }
            }
            .launchIn(screenModelScope)
    }

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getFirstServerHistoryChapter() }
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            _events.send(Event.OpenChapter(getNextServerChapter(mangaId, chapterId)))
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun syncServerState() {
        if (state.value.isRefreshing) return

        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            val result = runCatching {
                suwayomiClient.testConnection()
                ServerStateSync.requestRefresh(*serverHistoryRefreshAffectedEntities().toTypedArray())
            }
            mutableState.update { it.copy(isRefreshing = false) }
            result.onSuccess {
                _events.send(Event.ServerSyncSuccess)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to sync server state from history" }
                _events.send(
                    if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.ServerSyncFailed,
                )
            }
        }
    }

    fun refreshServerState(emitErrors: Boolean = true) {
        serverHistoryRefreshes.update {
            ServerRefreshRequest(sequence = it.sequence + 1, emitErrors = emitErrors)
        }
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.updateChapterRead(history.chapterId.toInt(), isRead = false)
                refreshServerState()
                ServerStateSync.requestRefresh(
                    *serverHistoryReadAffectedEntities(listOf(history.mangaId.toInt())).toTypedArray(),
                )
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to remove Suwayomi history entry" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
    }

    fun clearHistory() {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.getReadingHistoryChapterIds()
                    .chunked(CLEAR_HISTORY_CHUNK_SIZE)
                    .forEach { chapterIds ->
                        suwayomiClient.updateChaptersRead(chapterIds, isRead = false)
                    }
                refreshServerState()
                ServerStateSync.requestRefresh(*serverHistoryClearAffectedEntities().toTypedArray())
                _events.send(Event.HistoryCleared)
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to clear Suwayomi reading history" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
    }

    private suspend fun getFirstServerHistoryChapter(): Chapter? {
        return suwayomiClient.getReadingHistory(limit = 1)
            .firstOrNull()
            ?.toDomainChapter()
    }

    private suspend fun getNextServerChapter(mangaId: Long, chapterId: Long): Chapter? {
        val chapters = suwayomiClient.getChapters(mangaId.toInt())
            .sortedWith(compareBy<SuwayomiChapterDto> { it.sourceOrder }.thenBy { it.id })
        val currentIndex = chapters.indexOfFirst { it.id.toLong() == chapterId }
        if (currentIndex < 0) return null
        val current = chapters[currentIndex]
        return if (!current.isRead) {
            current.toDomainChapter()
        } else {
            chapters.drop(currentIndex + 1)
                .firstOrNull()
                ?.toDomainChapter()
        }
    }

    private fun SuwayomiChapterWithMangaDto.matchesHistoryQuery(query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        val normalized = query.lowercase()
        return manga.title.lowercase().contains(normalized) ||
            name.lowercase().contains(normalized) ||
            manga.author?.lowercase()?.contains(normalized) == true ||
            scanlator?.lowercase()?.contains(normalized) == true
    }

    private fun List<SuwayomiChapterWithMangaDto>.toServerHistoryUiModels(): List<HistoryUiModel> {
        return map { HistoryUiModel.Item(it.toHistoryWithRelations(), read = it.isRead) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    private fun SuwayomiChapterWithMangaDto.toHistoryWithRelations(): HistoryWithRelations {
        val sourceId = manga.sourceId.toLongOrNull() ?: 0L
        return HistoryWithRelations(
            id = id.toLong(),
            chapterId = id.toLong(),
            mangaId = mangaId.toLong(),
            title = manga.title,
            chapterNumber = chapterNumber.toDouble(),
            readAt = lastReadAt.takeIf { it > 0L }?.let { Date(it * 1000L) },
            readDuration = 0L,
            coverData = MangaCover(
                mangaId = mangaId.toLong(),
                sourceId = sourceId,
                isMangaFavorite = manga.inLibrary,
                url = manga.thumbnailUrl?.let { resolveServerUrl(suwayomiProvider.baseUrl(), it) },
                lastModified = 0L,
            ),
        )
    }

    private fun SuwayomiChapterWithMangaDto.toDomainChapter(): Chapter {
        return Chapter.create().copy(
            id = id.toLong(),
            mangaId = mangaId.toLong(),
            read = isRead,
            bookmark = isBookmarked,
            lastPageRead = lastPageRead.toLong(),
            dateFetch = fetchedAt.toLongOrNull() ?: 0L,
            sourceOrder = sourceOrder.toLong(),
            url = url,
            name = name,
            dateUpload = uploadDate,
            chapterNumber = chapterNumber.toDouble(),
            scanlator = scanlator,
        )
    }

    private fun SuwayomiChapterDto.toDomainChapter(): Chapter {
        return Chapter.create().copy(
            id = id.toLong(),
            mangaId = mangaId.toLong(),
            read = isRead,
            bookmark = isBookmarked,
            lastPageRead = lastPageRead.toLong(),
            dateFetch = fetchedAt.toLongOrNull() ?: 0L,
            sourceOrder = sourceOrder.toLong(),
            url = url,
            name = name,
            dateUpload = uploadDate,
            chapterNumber = chapterNumber.toDouble(),
            scanlator = scanlator,
        )
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return suwayomiClient.getCategories()
            .map { it.toCategory() }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        screenModelScope.launchIO {
            runCatching {
                if (!manga.favorite) {
                    suwayomiClient.updateMangaLibrary(
                        mangaId = manga.id.toInt(),
                        inLibrary = true,
                    )
                }
                suwayomiClient.updateMangaCategories(
                    mangaId = manga.id.toInt(),
                    categoryIds = categories.toServerCategoryIds(),
                )
                refreshServerState()
                ServerStateSync.requestRefresh(*serverHistoryLibraryAffectedEntities(manga.id.toInt()).toTypedArray())
                mutableState.update { it.copy(dialog = null) }
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to add Suwayomi history manga to categories" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return suwayomiClient.getMangaCategories(manga.id.toInt()).map { it.id.toLong() }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = runCatching { suwayomiClient.getManga(mangaId.toInt()).toDomainManga() }
                .getOrElse { error ->
                    logcat(LogPriority.ERROR, error) {
                        "Failed to load Suwayomi history manga before adding to library"
                    }
                    _events.send(
                        if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError,
                    )
                    return@launchIO
                }

            if (manga.favorite) {
                refreshServerState()
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            runCatching {
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }

                when {
                    defaultCategory != null -> {
                        suwayomiClient.updateMangaLibrary(
                            mangaId = manga.id.toInt(),
                            inLibrary = true,
                        )
                        suwayomiClient.updateMangaCategories(
                            mangaId = manga.id.toInt(),
                            categoryIds = listOf(defaultCategory.id).toServerCategoryIds(),
                        )
                        refreshServerState()
                        ServerStateSync.requestRefresh(
                            *serverHistoryLibraryAffectedEntities(manga.id.toInt()).toTypedArray(),
                        )
                        mutableState.update { it.copy(dialog = null) }
                    }

                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        suwayomiClient.updateMangaLibrary(
                            mangaId = manga.id.toInt(),
                            inLibrary = true,
                        )
                        refreshServerState()
                        ServerStateSync.requestRefresh(
                            *serverHistoryLibraryAffectedEntities(manga.id.toInt()).toTypedArray(),
                        )
                        mutableState.update { it.copy(dialog = null) }
                    }

                    else -> showChangeCategoryDialog(manga)
                }
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error) { "Failed to add Suwayomi history manga to library" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
            }
        }
    }

    fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = runCatching { getCategories() }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi categories from history" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
                return@launch
            }
            val selection = runCatching { getMangaCategoryIds(manga) }.getOrElse { error ->
                logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi manga categories from history" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
                return@launch
            }
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection },
                    ),
                )
            }
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

    private fun List<Long>.toServerCategoryIds(): List<Int> {
        return filterNot { it == Category.UNCATEGORIZED_ID }
            .map { it.toInt() }
    }

    private fun SuwayomiMangaDto.toDomainManga(): Manga {
        return Manga.create().copy(
            id = id.toLong(),
            source = sourceId.toLongOrNull() ?: 0L,
            favorite = inLibrary,
            dateAdded = inLibraryAt ?: 0L,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status.toDomainStatus(),
            thumbnailUrl = thumbnailUrl?.let { resolveServerUrl(suwayomiProvider.baseUrl(), it) },
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = initialized,
        )
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<HistoryUiModel>? = null,
        val serverUnavailable: Boolean = false,
        val dialog: Dialog? = null,
        val isRefreshing: Boolean = false,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: HistoryWithRelations) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object ServerUnavailable : Event
        data object HistoryCleared : Event
        data object ServerSyncSuccess : Event
        data object ServerSyncFailed : Event
    }

    private companion object {
        const val CLEAR_HISTORY_CHUNK_SIZE = 100
    }
}

private data class ServerRefreshRequest(
    val sequence: Int = 0,
    val emitErrors: Boolean = true,
)
