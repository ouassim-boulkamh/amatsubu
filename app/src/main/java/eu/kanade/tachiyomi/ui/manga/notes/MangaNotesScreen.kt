package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.ui.browse.migration.SERVER_MIGRATION_NOTES_META_KEY as SERVER_MANGA_NOTES_META_KEY

class MangaNotesScreen(
    private val manga: Manga,
    private val isServerBacked: Boolean = false,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { Model(manga, isServerBacked) }
        val state by screenModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }

    private class Model(
        private val manga: Manga,
        private val isServerBacked: Boolean,
        private val updateMangaNotes: UpdateMangaNotes = Injekt.get(),
        private val suwayomiProvider: SuwayomiClientProvider = SuwayomiClientProvider(),
    ) : StateScreenModel<State>(State(manga, manga.notes)) {

        fun updateNotes(content: String) {
            if (content == state.value.notes) return

            mutableState.update {
                it.copy(notes = content)
            }

            if (isServerBacked) {
                screenModelScope.launchIO {
                    runCatching {
                        suwayomiProvider.graphQlClient.setMangaMeta(
                            mangaId = manga.id.toInt(),
                            key = SERVER_MANGA_NOTES_META_KEY,
                            value = content,
                        )
                        ServerStateSync.requestRefresh()
                    }.onFailure { error ->
                        logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi manga notes" }
                    }
                }
                return
            }

            screenModelScope.launchNonCancellable {
                updateMangaNotes(manga.id, content)
            }
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
