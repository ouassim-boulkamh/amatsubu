package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.ui.browse.migration.toMigrationManga
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga

internal class MigrateMangaScreenModel(
    private val sourceId: String,
    private val provider: SuwayomiClientProvider = SuwayomiClientProvider(),
) : StateScreenModel<MigrateMangaScreenModel.State>(State()) {

    private val _events: Channel<MigrationMangaEvent> = Channel()
    val events: Flow<MigrationMangaEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            runCatching {
                withIOContext {
                    val source = provider.graphQlClient.sourceList()
                        .firstOrNull { it.id == sourceId }
                    val manga = provider.graphQlClient.getLibraryMangas()
                        .filter { it.sourceId == sourceId }
                        .map { it.toMigrationManga(provider) }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                    (source?.displayName ?: source?.name ?: sourceId) to manga
                }
            }.onSuccess { (sourceName, manga) ->
                mutableState.update { it.copy(sourceName = sourceName, titleList = manga) }
            }.onFailure { error ->
                logcat(LogPriority.ERROR, error)
                _events.send(MigrationMangaEvent.FailedFetchingFavorites)
                mutableState.update { state ->
                    state.copy(sourceName = sourceId, titleList = listOf())
                }
            }
        }
    }

    fun toggleSelection(item: Manga) {
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                if (!list.remove(item.id)) list.add(item.id)
            }
            state.copy(selection = selection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptySet()) }
    }

    @Immutable
    data class State(
        val sourceName: String? = null,
        val selection: Set<Long> = emptySet(),
        private val titleList: List<Manga>? = null,
    ) {

        val titles: List<Manga>
            get() = titleList ?: listOf()

        val isLoading: Boolean
            get() = titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()

        val selectionMode = selection.isNotEmpty()
    }
}

sealed interface MigrationMangaEvent {
    data object FailedFetchingFavorites : MigrationMangaEvent
}
