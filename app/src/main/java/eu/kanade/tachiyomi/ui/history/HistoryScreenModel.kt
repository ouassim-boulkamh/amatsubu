package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
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
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.MangaWithChapterCount
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class HistoryScreenModel(
    private val getCategories: GetCategories = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val serverHistoryRefreshes = MutableStateFlow(0)

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged(),
                serverHistoryRefreshes,
            ) { query, _ -> query }
                .map { query ->
                    runCatching {
                        suwayomiClient.getReadingHistory()
                            .filter { it.matchesHistoryQuery(query) }
                            .toServerHistoryUiModels()
                    }.onFailure { error ->
                        logcat(LogPriority.ERROR, error) { "Failed to load Suwayomi reading history" }
                        mutableState.update { it.copy(serverUnavailable = error.isSuwayomiServerUnavailable()) }
                        _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.InternalError)
                    }.onSuccess {
                        mutableState.update { it.copy(serverUnavailable = false) }
                    }.getOrDefault(emptyList())
                }
                .flowOn(Dispatchers.IO)
                .collect { newList -> mutableState.update { it.copy(list = newList) } }
        }

        ServerStateSync.refreshes
            .onEach { serverHistoryRefreshes.update { it + 1 } }
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
                ServerStateSync.requestRefresh()
            }
            mutableState.update { it.copy(isRefreshing = false) }
            result.onSuccess {
                _events.send(Event.ServerSyncSuccess)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to sync server state from history" }
                _events.send(if (error.isSuwayomiServerUnavailable()) Event.ServerUnavailable else Event.ServerSyncFailed)
            }
        }
    }

    fun removeFromHistory(history: HistoryWithRelations) {
        screenModelScope.launchIO {
            runCatching {
                suwayomiClient.updateChapterRead(history.chapterId.toInt(), isRead = false)
                serverHistoryRefreshes.update { it + 1 }
                ServerStateSync.requestRefresh()
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
                serverHistoryRefreshes.update { it + 1 }
                ServerStateSync.requestRefresh()
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
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId) ?: return@launchIO

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }
        }
    }

    fun showMigrateDialog(target: Manga, current: Manga) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(target = target, current = current))
        }
    }

    fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
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
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
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
