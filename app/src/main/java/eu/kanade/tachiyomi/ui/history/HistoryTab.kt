package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.history.HistoryClearAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.ServerForegroundRefreshEffect
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object HistoryTab : Tab {

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            HistoryScreenModel(
                libraryPreferences = context.appDependencies.libraryPreferences,
                suwayomiProvider = context.appDependencies.suwayomiClientProvider,
            )
        }
        val state by screenModel.state.collectAsState()

        HistoryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = screenModel::updateSearchQuery,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = screenModel::getNextChapterForManga,
            onClickFavorite = screenModel::addFavorite,
            onDialogChange = screenModel::setDialog,
            onRefresh = screenModel::syncServerState,
        )

        ServerForegroundRefreshEffect {
            screenModel.refreshServerState(emitErrors = false)
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryScreenModel.Dialog.DeleteAll -> {
                HistoryClearAllDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = screenModel::clearHistory,
                )
            }
            is HistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.removeFromHistory(dialog.history) },
                )
            }
            is HistoryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            null -> {}
        }

        LaunchedEffect(state.list) {
            if (state.list != null) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { e ->
                when (e) {
                    HistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryScreenModel.Event.ServerUnavailable ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.server_unreachable))
                    HistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    HistoryScreenModel.Event.ServerSyncSuccess ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.sync_server_state_success))
                    HistoryScreenModel.Event.ServerSyncFailed ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.sync_server_state_failed))
                    is HistoryScreenModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, screenModel.getNextChapter())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, isServerBacked = true)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
}
