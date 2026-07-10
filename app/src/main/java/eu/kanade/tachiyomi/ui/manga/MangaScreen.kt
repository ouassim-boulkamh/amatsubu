package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_MANGA_REAL_URL_META_KEY
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.browse.ServerGlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.ServerMigrateSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.ServerTrackInfoDialogScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import eu.kanade.presentation.migration.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.time.Duration.Companion.seconds

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
    private val fetchChaptersOnOpen: Boolean = fromSource,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            MangaScreenModel(
                context = context,
                mangaId = mangaId,
                isFromSource = fromSource,
                fetchChaptersOnOpen = fetchChaptersOnOpen,
                dependencies = context.appDependencies,
            )
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        if (state is MangaScreenModel.State.Error) {
            EmptyScreen(
                message = (state as MangaScreenModel.State.Error).message,
                actions = listOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = screenModel::retryServerMangaLoad,
                    ),
                ),
            )
            return
        }

        val successState = state as MangaScreenModel.State.Success
        val isServerBacked = successState.isServerBacked
        val isOfflineSnapshot = successState.staleSnapshot != null
        fun hasFreshDeviceCopy(chapter: Chapter): Boolean {
            return successState.chapters.any { item ->
                item.id == chapter.id &&
                    item.deviceCopyState == DeviceCopyState.FRESH
            }
        }

        DisposableEffect(lifecycleOwner, screenModel, isServerBacked) {
            val observer = LifecycleEventObserver { _, event ->
                if (isServerBacked && !isOfflineSnapshot && event == Lifecycle.Event.ON_RESUME) {
                    screenModel.refreshServerManga()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(screenModel, isServerBacked) {
            if (!isServerBacked || isOfflineSnapshot) return@LaunchedEffect

            while (true) {
                delay(SERVER_CHAPTER_STATE_POLL_INTERVAL)
                screenModel.refreshServerManga()
            }
        }

        LaunchedEffect(screenModel, isServerBacked, successState.chapters) {
            if (!isServerBacked) return@LaunchedEffect
            while (successState.chapters.any { it.deviceCopyState == DeviceCopyState.DOWNLOADING }) {
                delay(DEVICE_COPY_STATE_POLL_INTERVAL)
                screenModel.refreshDeviceCopyStates()
            }
        }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isServerBacked) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, isServerBacked)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = {
                if (isOfflineSnapshot && !hasFreshDeviceCopy(it)) {
                    scope.launch {
                        screenModel.snackbarHostState.showSnackbar(
                            context.stringResource(MR.strings.server_offline_reader_unavailable),
                        )
                    }
                } else {
                    openChapter(context, it, isServerBacked)
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf {
                isServerBacked
            },
            onAddToLibraryClicked = if (isServerBacked) {
                {
                    if (isOfflineSnapshot) {
                        scope.launch {
                            screenModel.snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.server_offline_actions_disabled),
                            )
                        }
                    } else {
                        screenModel.toggleServerLibrary()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            } else {
                {
                    screenModel.toggleFavorite()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onWebViewClicked = {
                openMangaInWebView(
                    navigator,
                    screenModel.manga,
                    isServerBacked,
                )
            }.takeIf { !isOfflineSnapshot && isServerBacked },
            onWebViewLongClicked = {
                copyMangaUrl(
                    context,
                    screenModel.manga,
                    isServerBacked,
                )
            }.takeIf { !isOfflineSnapshot && isServerBacked },
            onTrackingClicked = if (isServerBacked) {
                {
                    if (isOfflineSnapshot) {
                        scope.launch {
                            screenModel.snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.server_offline_actions_disabled),
                            )
                        }
                    } else {
                        screenModel.showTrackDialog()
                    }
                }
            } else {
                null
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = if (isServerBacked) {
                {
                    if (isOfflineSnapshot) {
                        scope.launch {
                            screenModel.snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.server_offline_actions_disabled),
                            )
                        }
                    } else {
                        screenModel.refreshServerManga()
                    }
                }
            } else {
                { screenModel.fetchAllFromSource() }
            },
            onContinueReading = {
                val nextUnreadChapter = screenModel.getNextUnreadChapter()
                if (isOfflineSnapshot && nextUnreadChapter?.let(::hasFreshDeviceCopy) != true) {
                    scope.launch {
                        screenModel.snackbarHostState.showSnackbar(
                            context.stringResource(MR.strings.server_offline_reader_unavailable),
                        )
                    }
                } else {
                    continueReading(context, nextUnreadChapter, isServerBacked)
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = {
                shareManga(context, screenModel.manga, isServerBacked)
            }.takeIf { !isOfflineSnapshot && isServerBacked },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf {
                !isOfflineSnapshot && isServerBacked
            },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf {
                successState.manga.favorite && !isOfflineSnapshot
            },
            onEditFetchIntervalClicked = screenModel::showSetFetchIntervalDialog.takeIf {
                successState.manga.favorite && !isServerBacked
            },
            onMigrateClicked = {
                navigator.push(ServerMigrateSearchScreen(successState.manga.id))
            }.takeIf { successState.manga.favorite && isServerBacked && !isOfflineSnapshot },
            onEditNotesClicked = {
                navigator.push(MangaNotesScreen(manga = successState.manga))
            }.takeIf { isServerBacked },
            onMultiBookmarkClicked = { chapters, bookmarked ->
                if (!isOfflineSnapshot) screenModel.bookmarkChapters(chapters, bookmarked)
            },
            onMultiMarkAsReadClicked = { chapters, read ->
                screenModel.markChaptersRead(chapters, read)
            },
            onMarkPreviousAsReadClicked = {
                screenModel.markPreviousChapterRead(it)
            },
            onMultiDeleteClicked = if (isServerBacked) {
                if (isOfflineSnapshot) null else screenModel::deleteChapters
            } else {
                screenModel::showDeleteChapterDialog
            },
            onChapterSwipe = { item, action ->
                if (!isOfflineSnapshot || action == LibraryPreferences.ChapterSwipeAction.ToggleRead) {
                    screenModel.chapterSwipe(item, action)
                }
            },
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    sourceNamesById = successState.sourceNamesById,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(it) },
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                basePreferences = context.appDependencies.basePreferences,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onLocalDownloadFilterChanged = screenModel::setLocalDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                onResetToDefault = screenModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            MangaScreenModel.Dialog.TrackSheet -> {
                if (!successState.isServerBacked) return@Content
                val sheetScreen = ServerTrackInfoDialogScreen(
                    mangaId = successState.manga.id.toInt(),
                    mangaTitle = successState.manga.title,
                )
                NavigatorAdaptiveSheet(
                    screen = sheetScreen,
                    enableSwipeDismiss = { it.lastItem::class == sheetScreen::class },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.FullCover -> {
                val appDependencies = context.appDependencies
                val sm = rememberScreenModel {
                    MangaCoverScreenModel(
                        initialManga = successState.manga,
                        imageSaver = appDependencies.imageSaver,
                        coverCache = appDependencies.coverCache,
                    )
                }
                LaunchedEffect(successState.manga) {
                    sm.updateManga(successState.manga)
                }
                val manga by sm.state.collectAsState()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover(appDependencies.coverCache) },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onRefreshClick = if (successState.isServerBacked) {
                            { screenModel.refreshServerManga(clearCoverCache = true) }
                        } else {
                            null
                        },
                        onEditClick = if (successState.isServerBacked) {
                            null
                        } else {
                            { action ->
                                when (action) {
                                    EditCoverAction.EDIT -> getContent.launch("image/*")
                                    EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                                }
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is MangaScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?, isServerBacked: Boolean) {
        if (unreadChapter != null) openChapter(context, unreadChapter, isServerBacked)
    }

    private fun openChapter(context: Context, chapter: Chapter, isServerBacked: Boolean) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, isServerBacked))
    }

    private fun getMangaUrl(manga_: Manga?, isServerBacked: Boolean = false): String? {
        val manga = manga_ ?: return null
        if (isServerBacked) {
            manga.memo[SUWAYOMI_MANGA_REAL_URL_META_KEY]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { return it }

            manga.url
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { return it }
        }
        return null
    }

    private fun openMangaInWebView(
        navigator: Navigator,
        manga_: Manga?,
        isServerBacked: Boolean = false,
    ) {
        getMangaUrl(manga_, isServerBacked)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = null,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, isServerBacked: Boolean = false) {
        try {
            getMangaUrl(manga_, isServerBacked)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(ServerGlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String) {
        if (navigator.size < 2) {
            return
        }

        performSearch(navigator, genreName, global = false)
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(
        context: Context,
        manga_: Manga?,
        isServerBacked: Boolean = false,
    ) {
        val url = getMangaUrl(manga_, isServerBacked) ?: return
        context.copyToClipboard(url, url)
    }
}

private val SERVER_CHAPTER_STATE_POLL_INTERVAL = 30.seconds
private val DEVICE_COPY_STATE_POLL_INTERVAL = 1.seconds
