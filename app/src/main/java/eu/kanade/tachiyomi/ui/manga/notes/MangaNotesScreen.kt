package eu.kanade.tachiyomi.ui.manga.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.manga.MangaNotesScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.SERVER_MANGA_NOTES_META_KEY
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.serverMangaNotesAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverNotes
import eu.kanade.tachiyomi.di.appDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

class MangaNotesScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val dependencies = context.appDependencies
        val screenModel = rememberScreenModel {
            Model(manga, dependencies.suwayomiClientProvider)
        }
        val state by screenModel.state.collectAsState()

        MangaNotesScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateNotes,
        )
    }

    private class Model(
        private val manga: Manga,
        private val suwayomiProvider: SuwayomiClientProvider,
    ) : StateScreenModel<State>(State(manga, manga.notes)) {

        fun updateNotes(content: String) {
            if (content == state.value.notes) return
            val previousNotes = state.value.notes

            mutableState.update {
                it.copy(notes = content)
            }

            screenModelScope.launchIO {
                runCatching {
                    val mangaId = manga.id.toInt()
                    suwayomiProvider.graphQlClient.setMangaMeta(
                        mangaId = mangaId,
                        key = SERVER_MANGA_NOTES_META_KEY,
                        value = content,
                    )
                    val refetched = suwayomiProvider.graphQlClient.getManga(mangaId)
                    mutableState.update { it.copy(notes = refetched.serverNotes()) }
                    ServerStateSync.requestRefresh(*serverMangaNotesAffectedEntities(mangaId).toTypedArray())
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    logcat(LogPriority.ERROR, error) { "Failed to update Suwayomi manga notes" }
                    mutableState.update { it.copy(notes = previousNotes) }
                }
            }
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
        val notes: String,
    )
}
