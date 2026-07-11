package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.extension.model.Extension
import eu.kanade.domain.extension.model.ExtensionStore
import eu.kanade.domain.extension.model.InstallStep
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.Pin
import eu.kanade.domain.source.model.Pins
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.browse.ExtensionUiModel
import eu.kanade.presentation.browse.ExtensionsState
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.browse.MigrateSourceState
import eu.kanade.presentation.browse.SourceGroup
import eu.kanade.presentation.browse.SourceListing
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.browse.SourcesState
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.suwayomi.FetchSourceMangaType
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateInvalidation
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionStoreDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiGraphQlClient
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceDto
import eu.kanade.tachiyomi.data.suwayomi.currentLibVersion
import eu.kanade.tachiyomi.data.suwayomi.currentVersionCode
import eu.kanade.tachiyomi.data.suwayomi.hasNsfwContent
import eu.kanade.tachiyomi.data.suwayomi.isLocalFolderSource
import eu.kanade.tachiyomi.data.suwayomi.isSuwayomiServerUnavailable
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverBrowseRefreshAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.serverExtensionActionAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.sourceNodes
import eu.kanade.tachiyomi.data.suwayomi.webUrl
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.ServerForegroundRefreshEffect
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.TreeMap
import kotlin.math.absoluteValue
import tachiyomi.core.common.i18n.stringResource as contextStringResource

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(ServerGlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        var isSyncing by remember { mutableStateOf(false) }
        var sourcesRefreshVersion by remember { mutableIntStateOf(0) }
        var extensionsRefreshVersion by remember { mutableIntStateOf(0) }
        var migrationRefreshVersion by remember { mutableIntStateOf(0) }
        val state = rememberPagerState { BROWSE_PAGE_COUNT }
        val syncServerState: (SnackbarHostState) -> Unit = { snackbarHostState ->
            if (!isSyncing) {
                scope.launch {
                    isSyncing = true
                    val result = runCatching {
                        withIOContext {
                            provider.graphQlClient.testConnection()
                        }
                        ServerStateSync.requestRefresh(*serverBrowseRefreshAffectedEntities().toTypedArray())
                    }
                    isSyncing = false
                    result.onSuccess {
                        snackbarHostState.showSnackbar(
                            context.contextStringResource(MR.strings.sync_server_state_success),
                        )
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        logcat(LogPriority.ERROR, error) { "Failed to sync server state from browse" }
                        snackbarHostState.showSnackbar(
                            context.contextStringResource(MR.strings.sync_server_state_failed),
                        )
                    }
                }
            }
        }

        val tabs = listOf(
            serverSourcesTab(
                serverRefreshVersion = sourcesRefreshVersion,
                isActive = state.currentPage == 0,
                isSyncing = isSyncing,
                onForegroundRefresh = { sourcesRefreshVersion++ },
                onSyncServerState = syncServerState,
            ),
            serverExtensionsTab(
                serverRefreshVersion = extensionsRefreshVersion,
                isActive = state.currentPage == 1,
                isSyncing = isSyncing,
                onForegroundRefresh = { extensionsRefreshVersion++ },
                onSyncServerState = syncServerState,
            ),
            serverMigrateSourceTab(
                serverRefreshVersion = migrationRefreshVersion,
                isActive = state.currentPage == 2,
                isSyncing = isSyncing,
                onForegroundRefresh = { migrationRefreshVersion++ },
                onSyncServerState = syncServerState,
            ),
        )

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
        )
        LaunchedEffect(Unit) {
            switchToExtensionTabChannel.receiveAsFlow()
                .collectLatest { state.scrollToPage(1) }
        }

        LaunchedEffect(Unit) {
            ServerStateSync.invalidations
                .filter(ServerStateInvalidation::affectsBrowse)
                .collectLatest {
                    sourcesRefreshVersion++
                    extensionsRefreshVersion++
                    migrationRefreshVersion++
                }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    sourcesRefreshVersion++
                    extensionsRefreshVersion++
                    migrationRefreshVersion++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private fun ServerStateInvalidation.affectsBrowse(): Boolean {
    return affectsAny(
        ServerStateEntity.Sources,
        ServerStateEntity.Extensions,
        ServerStateEntity.ExtensionStores,
    )
}

@Composable
private fun serverSourcesTab(
    serverRefreshVersion: Int,
    isActive: Boolean,
    isSyncing: Boolean,
    onForegroundRefresh: () -> Unit,
    onSyncServerState: (SnackbarHostState) -> Unit,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    return TabContent(
        titleRes = MR.strings.label_sources,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_search),
                icon = Icons.Outlined.Search,
                onClick = { navigator.push(ServerGlobalSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(ServerSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            ServerForegroundRefreshEffect(enabled = isActive, onRefresh = onForegroundRefresh)
            ServerSourcesContent(
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                serverRefreshVersion = serverRefreshVersion,
                isSyncing = isSyncing,
                onSyncServerState = { onSyncServerState(snackbarHostState) },
            )
        },
    )
}

@Composable
private fun serverExtensionsTab(
    serverRefreshVersion: Int,
    isActive: Boolean,
    isSyncing: Boolean,
    onForegroundRefresh: () -> Unit,
    onSyncServerState: (SnackbarHostState) -> Unit,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    return TabContent(
        titleRes = MR.strings.label_extensions,
        actions = listOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(ServerExtensionsFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            ServerForegroundRefreshEffect(enabled = isActive, onRefresh = onForegroundRefresh)
            ServerExtensionsContent(
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                serverRefreshVersion = serverRefreshVersion,
                isSyncing = isSyncing,
                onSyncServerState = { onSyncServerState(snackbarHostState) },
            )
        },
    )
}

@Composable
private fun serverMigrateSourceTab(
    serverRefreshVersion: Int,
    isActive: Boolean,
    isSyncing: Boolean,
    onForegroundRefresh: () -> Unit,
    onSyncServerState: (SnackbarHostState) -> Unit,
): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
    var sortingMode by remember {
        androidx.compose.runtime.mutableStateOf(sourcePreferences.migrationSortingMode.get())
    }
    var sortingDirection by remember {
        androidx.compose.runtime.mutableStateOf(sourcePreferences.migrationSortingDirection.get())
    }
    val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
    val baseUrl = remember { runCatching { provider.baseUrl() }.getOrNull() }
    ServerForegroundRefreshEffect(enabled = isActive, onRefresh = onForegroundRefresh)
    val state by produceState<ServerMigrationSourcesState>(
        initialValue = ServerMigrationSourcesState.Loading,
        key1 = sortingMode,
        key2 = sortingDirection,
        key3 = serverRefreshVersion,
    ) {
        value = runCatching {
            provider.baseUrl()
            withIOContext {
                val sources = provider.graphQlClient.sourceList()
                    .filterNot(SuwayomiSourceDto::isLocalFolderSource)
                    .associateBy { it.id }
                val counts = provider.graphQlClient.getLibraryMangas()
                    .groupingBy { it.sourceId }
                    .eachCount()

                val migrationSources = counts.mapNotNull { (sourceId, count) ->
                    sources[sourceId]?.toDomainSource()?.let { it to count.toLong() }
                }
                    .sortedWith(serverMigrationComparator(sortingMode, sortingDirection))
                val sourceIdsByDomainId = sources.values.associate { it.domainId() to it.id }
                val sourceIconUrlsByDomainId = sources.values.associate { source ->
                    source.domainId() to source.iconUrl
                }
                ServerMigrationSourcesResult(
                    sources = migrationSources,
                    sourceIdsByDomainId = sourceIdsByDomainId,
                    sourceIconUrlsByDomainId = sourceIconUrlsByDomainId,
                )
            }
        }.fold(
            onSuccess = ServerMigrationSourcesState::Success,
            onFailure = ServerMigrationSourcesState::Error,
        )
    }

    return TabContent(
        titleRes = MR.strings.label_migration,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://github.com/Suwayomi/Suwayomi-Server/wiki")
                },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            PullRefresh(
                refreshing = isSyncing,
                enabled = state !is ServerMigrationSourcesState.Loading,
                onRefresh = { onSyncServerState(snackbarHostState) },
                indicatorPadding = contentPadding,
            ) {
                when (val current = state) {
                    ServerMigrationSourcesState.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                    is ServerMigrationSourcesState.Error -> {
                        val errorMessage = if (current.throwable.isSuwayomiServerUnavailable()) {
                            stringResource(MR.strings.server_unreachable)
                        } else {
                            stringResource(MR.strings.server_sources_load_error)
                        }
                        LaunchedEffect(current.throwable) {
                            snackbarHostState.showSnackbar(errorMessage)
                        }
                        EmptyScreen(
                            message = errorMessage,
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                    is ServerMigrationSourcesState.Success -> {
                        MigrateSourceScreen(
                            state = MigrateSourceState(
                                isLoading = false,
                                items = current.result.sources,
                                sortingMode = sortingMode,
                                sortingDirection = sortingDirection,
                            ),
                            contentPadding = contentPadding,
                            onClickItem = { source ->
                                current.result.sourceIdsByDomainId[source.id]?.let { sourceId ->
                                    navigator.push(MigrateMangaScreen(sourceId))
                                }
                            },
                            onToggleSortingDirection = {
                                sortingDirection = when (sortingDirection) {
                                    SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                                    SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
                                }
                                sourcePreferences.migrationSortingDirection.set(sortingDirection)
                            },
                            onToggleSortingMode = {
                                sortingMode = when (sortingMode) {
                                    SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                                    SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
                                }
                                sourcePreferences.migrationSortingMode.set(sortingMode)
                            },
                            sourceIcon = { source ->
                                val iconUrl = current.result.sourceIconUrlsByDomainId[source.id]
                                if (iconUrl != null && baseUrl != null) {
                                    AsyncImage(
                                        model = resolveServerUrl(baseUrl, iconUrl),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                } else {
                                    SourceIcon(source = source)
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ServerExtensionsContent(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    serverRefreshVersion: Int,
    isSyncing: Boolean,
    onSyncServerState: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val errorMessage = stringResource(MR.strings.server_sources_load_error)
    val scope = rememberCoroutineScope()
    val loadingActions = remember { mutableStateMapOf<String, InstallStep>() }
    var reloadVersion by remember { mutableIntStateOf(0) }
    val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
    val enabledLanguages by sourcePreferences.enabledLanguages.changes()
        .collectAsState(sourcePreferences.enabledLanguages.get())
    val showNsfwSources by sourcePreferences.showNsfwSource.changes()
        .collectAsState(sourcePreferences.showNsfwSource.get())
    val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
    val baseUrl = remember { runCatching { provider.baseUrl() }.getOrNull() }
    val state by produceState<ServerExtensionsState>(
        initialValue = ServerExtensionsState.Loading,
        key1 = reloadVersion,
        key2 = showNsfwSources,
        key3 = serverRefreshVersion,
    ) {
        value = runCatching {
            provider.baseUrl()
            withIOContext {
                provider.graphQlClient.extensionList()
                    .filter { showNsfwSources || !it.hasNsfwContent() }
                    .sortedWith(compareBy<SuwayomiExtensionDto> { it.lang }.thenBy { it.name.lowercase() })
            }
        }.fold(
            onSuccess = ServerExtensionsState::Success,
            onFailure = ServerExtensionsState::Error,
        )
    }

    when (val current = state) {
        ServerExtensionsState.Loading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
        is ServerExtensionsState.Error -> {
            val currentErrorMessage = if (current.throwable.isSuwayomiServerUnavailable()) {
                stringResource(MR.strings.server_unreachable)
            } else {
                errorMessage
            }
            LaunchedEffect(current.throwable) {
                snackbarHostState.showSnackbar(currentErrorMessage)
            }
            EmptyScreen(
                message = currentErrorMessage,
                modifier = Modifier.padding(contentPadding),
            )
        }
        is ServerExtensionsState.Success -> {
            if (current.extensions.isEmpty()) {
                EmptyScreen(
                    message = stringResource(MR.strings.label_extensions),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                val updateExtensions = current.extensions.filter { it.isInstalled && it.hasUpdate }
                val installedExtensions = current.extensions.filter { it.isInstalled && !it.hasUpdate }
                val availableExtensions = current.extensions
                    .filterNot { it.isInstalled }
                    .filter { it.lang in enabledLanguages }
                fun runAction(
                    pkgName: String,
                    install: Boolean = false,
                    update: Boolean = false,
                    uninstall: Boolean = false,
                ) {
                    scope.launch {
                        loadingActions[pkgName] = InstallStep.Installing
                        runCatching {
                            withIOContext {
                                provider.graphQlClient.updateExtension(
                                    pkgName = pkgName,
                                    install = install,
                                    uninstall = uninstall,
                                    update = update,
                                )
                            }
                        }.onSuccess {
                            ServerStateSync.requestRefresh(*serverExtensionActionAffectedEntities().toTypedArray())
                            reloadVersion++
                            loadingActions.remove(pkgName)
                        }.onFailure {
                            loadingActions[pkgName] = InstallStep.Error
                            snackbarHostState.showSnackbar(errorMessage)
                        }
                    }
                }
                val iconUrls = current.extensions.associate { extension ->
                    extension.pkgName to extension.iconUrl?.let { iconUrl ->
                        baseUrl?.let { resolveServerUrl(it, iconUrl) }
                    }
                }
                val items = buildMap {
                    val updates = updateExtensions.map { it.toInstalledExtension().toExtensionItem(loadingActions) }
                    if (updates.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
                    }

                    val installed = installedExtensions.map {
                        it.toInstalledExtension().toExtensionItem(loadingActions)
                    }
                    if (installed.isNotEmpty()) {
                        put(ExtensionUiModel.Header.Resource(MR.strings.ext_installed), installed)
                    }

                    availableExtensions
                        .groupBy { it.lang }
                        .toSortedMap(LocaleHelper.comparator)
                        .forEach { (lang, extensions) ->
                            put(
                                ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)),
                                extensions
                                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                                    .map { it.toAvailableExtension(baseUrl.orEmpty()).toExtensionItem(loadingActions) },
                            )
                        }
                }

                ExtensionScreen(
                    state = ExtensionsState(
                        isLoading = false,
                        isRefreshing = isSyncing,
                        items = items,
                    ),
                    contentPadding = contentPadding,
                    searchQuery = null,
                    onLongClickItem = { extension ->
                        when (extension) {
                            is Extension.Available -> runAction(extension.pkgName, install = true)
                            is Extension.Installed -> runAction(extension.pkgName, uninstall = true)
                        }
                    },
                    onClickItemCancel = {},
                    onClickUpdateAll = {
                        updateExtensions.forEach { runAction(it.pkgName, update = true) }
                    },
                    onOpenWebView = { extension ->
                        extension.sources.firstOrNull { it.baseUrl.isNotBlank() }?.let { source ->
                            navigator?.push(
                                WebViewScreen(
                                    url = source.baseUrl,
                                    initialTitle = source.name,
                                    sourceId = source.id,
                                ),
                            )
                        }
                    },
                    onInstallExtension = { extension -> runAction(extension.pkgName, install = true) },
                    onOpenExtension = { extension -> navigator?.push(ServerExtensionDetailsScreen(extension.pkgName)) },
                    onUninstallExtension = { extension -> runAction(extension.pkgName, uninstall = true) },
                    onUpdateExtension = { extension -> runAction(extension.pkgName, update = true) },
                    onRefresh = onSyncServerState,
                    indicatorPadding = contentPadding,
                    extensionIcon = { extension, modifier ->
                        AsyncImage(
                            model = iconUrls[extension.pkgName],
                            contentDescription = null,
                            modifier = modifier,
                        )
                    },
                )
            }
        }
    }
}

private fun Extension.toExtensionItem(loadingActions: Map<String, InstallStep>): ExtensionUiModel.Item {
    return ExtensionUiModel.Item(this, loadingActions[pkgName] ?: InstallStep.Idle)
}

internal fun SuwayomiExtensionDto.toInstalledExtension(): Extension.Installed {
    return Extension.Installed(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = currentVersionCode(),
        libVersion = currentLibVersion(),
        lang = lang,
        isNsfw = hasNsfwContent(),
        pkgFactory = null,
        sources = sourceNodes().map { it.toExtensionSource() },
        icon = null,
        hasUpdate = hasUpdate,
        isObsolete = isObsolete,
        isShared = true,
        store = extensionStore?.toExtensionStore() ?: storeIndexUrl?.toExtensionStore() ?: repo?.toExtensionStore(),
    )
}

internal fun SuwayomiExtensionDto.toAvailableExtension(baseUrl: String): Extension.Available {
    return Extension.Available(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = currentVersionCode(),
        libVersion = currentLibVersion(),
        lang = lang,
        isNsfw = hasNsfwContent(),
        sources = sourceNodes().map {
            Extension.Available.Source(
                id = it.domainId(),
                lang = it.lang,
                name = it.name,
                baseUrl = it.webUrl().orEmpty(),
            )
        },
        apkUrl = "",
        iconUrl = iconUrl?.let { resolveServerUrl(baseUrl, it) }.orEmpty(),
        store = extensionStore?.toExtensionStore() ?: storeIndexUrl?.toExtensionStore() ?: repo.toExtensionStore(),
    )
}

internal fun String?.toExtensionStore(): ExtensionStore {
    val url = this.orEmpty()
    return ExtensionStore(
        indexUrl = url,
        name = url,
        badgeLabel = "",
        signingKey = "",
        contact = ExtensionStore.Contact(website = url, discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )
}

internal fun SuwayomiExtensionStoreDto.toExtensionStore(): ExtensionStore {
    return ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(website = contactWebsite, discord = contactDiscord),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )
}

internal fun SuwayomiSourceDto.toExtensionSource(): Extension.Installed.Source {
    return Extension.Installed.Source(
        id = domainId(),
        lang = lang,
        name = name,
        baseUrl = webUrl().orEmpty(),
        supportsLatest = supportsLatest,
        isConfigurable = isConfigurable,
    )
}

@Composable
private fun ServerSourcesContent(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    serverRefreshVersion: Int,
    isSyncing: Boolean,
    onSyncServerState: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val errorMessage = stringResource(MR.strings.server_sources_load_error)
    val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
    val enabledLanguages by sourcePreferences.enabledLanguages.changes()
        .collectAsState(sourcePreferences.enabledLanguages.get())
    val disabledSources by sourcePreferences.disabledSources.changes()
        .collectAsState(sourcePreferences.disabledSources.get())
    val pinnedSources by sourcePreferences.pinnedSources.changes()
        .collectAsState(sourcePreferences.pinnedSources.get())
    val lastUsedSource by sourcePreferences.lastUsedSource.changes()
        .collectAsState(sourcePreferences.lastUsedSource.get())
    val showNsfwSources by sourcePreferences.showNsfwSource.changes()
        .collectAsState(sourcePreferences.showNsfwSource.get())
    var dialogSource by remember { androidx.compose.runtime.mutableStateOf<Source?>(null) }
    val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
    val baseUrl = remember { runCatching { provider.baseUrl() }.getOrNull() }
    val state by produceState<ServerSourcesState>(
        initialValue = ServerSourcesState.Loading,
        showNsfwSources,
        enabledLanguages,
        disabledSources,
        serverRefreshVersion,
    ) {
        value = runCatching {
            provider.baseUrl()
            withIOContext {
                provider.graphQlClient.sourceList()
                    .filterNot(SuwayomiSourceDto::isLocalFolderSource)
                    .filter { showNsfwSources || !it.hasNsfwContent() }
                    .filter { it.lang in enabledLanguages }
                    .filterNot { it.domainId().toString() in disabledSources }
            }
        }.fold(
            onSuccess = ServerSourcesState::Success,
            onFailure = ServerSourcesState::Error,
        )
    }

    PullRefresh(
        refreshing = isSyncing,
        enabled = state !is ServerSourcesState.Loading,
        onRefresh = onSyncServerState,
        indicatorPadding = contentPadding,
    ) {
        when (val current = state) {
            ServerSourcesState.Loading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
            is ServerSourcesState.Error -> {
                val currentErrorMessage = if (current.throwable.isSuwayomiServerUnavailable()) {
                    stringResource(MR.strings.server_unreachable)
                } else {
                    errorMessage
                }
                LaunchedEffect(current.throwable) {
                    snackbarHostState.showSnackbar(currentErrorMessage)
                }
                EmptyScreen(
                    message = currentErrorMessage,
                    modifier = Modifier.padding(contentPadding),
                )
            }
            is ServerSourcesState.Success -> {
                if (current.sources.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(MR.strings.server_sources_empty),
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    val sourcesByDomainId = current.sources.associateBy { it.domainId() }
                    val sourceItems = current.sources.toSourceUiModels(
                        pinnedSources = pinnedSources,
                        lastUsedSource = lastUsedSource,
                    )
                    SourcesScreen(
                        state = SourcesState(
                            isLoading = false,
                            items = sourceItems,
                        ),
                        contentPadding = contentPadding,
                        onClickItem = { source, listing ->
                            sourcesByDomainId[source.id]?.let { serverSource ->
                                sourcePreferences.lastUsedSource.set(source.id)
                                navigator?.push(
                                    ServerSourceMangaScreen(
                                        sourceId = serverSource.id,
                                        sourceName = serverSource.name,
                                        sourceDisplayName = serverSource.name,
                                        supportsLatest = serverSource.supportsLatest,
                                        isConfigurable = serverSource.isConfigurable,
                                        initialTypeName = when (listing) {
                                            SourceListing.Latest -> FetchSourceMangaType.LATEST.name
                                            else -> FetchSourceMangaType.POPULAR.name
                                        },
                                    ),
                                )
                            }
                        },
                        onClickPin = { source ->
                            sourcePreferences.pinnedSources.getAndSet { pinned ->
                                val sourceId = source.id.toString()
                                if (sourceId in pinned) pinned - sourceId else pinned + sourceId
                            }
                        },
                        onLongClickItem = { source -> dialogSource = source },
                        sourceIcon = { source ->
                            val iconUrl = sourcesByDomainId[source.id]?.iconUrl
                            if (iconUrl != null && baseUrl != null) {
                                AsyncImage(
                                    model = resolveServerUrl(baseUrl, iconUrl),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                SourceIcon(source = source)
                            }
                        },
                    )

                    dialogSource?.let { source ->
                        SourceOptionsDialog(
                            source = source,
                            onClickPin = {
                                sourcePreferences.pinnedSources.getAndSet { pinned ->
                                    val sourceId = source.id.toString()
                                    if (sourceId in pinned) pinned - sourceId else pinned + sourceId
                                }
                                dialogSource = null
                            },
                            onClickDisable = {
                                sourcePreferences.disabledSources.getAndSet { disabled ->
                                    val sourceId = source.id.toString()
                                    if (sourceId in disabled) disabled - sourceId else disabled + sourceId
                                }
                                dialogSource = null
                            },
                            onDismiss = { dialogSource = null },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface ServerSourcesState {
    data object Loading : ServerSourcesState
    data class Success(val sources: List<SuwayomiSourceDto>) : ServerSourcesState
    data class Error(val throwable: Throwable) : ServerSourcesState
}

private sealed interface ServerExtensionsState {
    data object Loading : ServerExtensionsState
    data class Success(val extensions: List<SuwayomiExtensionDto>) : ServerExtensionsState
    data class Error(val throwable: Throwable) : ServerExtensionsState
}

private sealed interface ServerMigrationSourcesState {
    data object Loading : ServerMigrationSourcesState
    data class Success(val result: ServerMigrationSourcesResult) : ServerMigrationSourcesState
    data class Error(val throwable: Throwable) : ServerMigrationSourcesState
}

private data class ServerMigrationSourcesResult(
    val sources: List<Pair<Source, Long>>,
    val sourceIdsByDomainId: Map<Long, String>,
    val sourceIconUrlsByDomainId: Map<Long, String?>,
)

private fun serverMigrationComparator(
    mode: SetMigrateSorting.Mode,
    direction: SetMigrateSorting.Direction,
): Comparator<Pair<Source, Long>> {
    val comparator = when (mode) {
        SetMigrateSorting.Mode.ALPHABETICAL -> compareBy<Pair<Source, Long>, String>(String.CASE_INSENSITIVE_ORDER) {
            it.first.name
        }
        SetMigrateSorting.Mode.TOTAL -> compareBy<Pair<Source, Long>> { it.second }
    }
    return if (direction == SetMigrateSorting.Direction.ASCENDING) {
        comparator
    } else {
        comparator.reversed()
    }
}

private fun SuwayomiSourceDto.toDomainSource(): Source {
    return Source(
        id = domainId(),
        lang = lang,
        name = name,
        supportsLatest = supportsLatest,
        isStub = false,
    )
}

private fun List<SuwayomiSourceDto>.toSourceUiModels(
    pinnedSources: Set<String>,
    lastUsedSource: Long,
): List<SourceUiModel> {
    val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
        when {
            d1 == SourceGroup.LAST_USED_KEY && d2 != SourceGroup.LAST_USED_KEY -> -1
            d2 == SourceGroup.LAST_USED_KEY && d1 != SourceGroup.LAST_USED_KEY -> 1
            d1 == SourceGroup.PINNED_KEY && d2 != SourceGroup.PINNED_KEY -> -1
            d2 == SourceGroup.PINNED_KEY && d1 != SourceGroup.PINNED_KEY -> 1
            d1 == "" && d2 != "" -> 1
            d2 == "" && d1 != "" -> -1
            else -> d1.compareTo(d2)
        }
    }
    val sources = sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .flatMap { dto ->
            val domainId = dto.domainId()
            val pin = if (domainId.toString() in pinnedSources) Pins.pinned else Pins.unpinned
            val source = dto.toDomainSource().copy(pin = pin)
            val items = mutableListOf(source)
            if (domainId == lastUsedSource) {
                items.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
            }
            items
        }

    sources.groupByTo(map) {
        when {
            it.isUsedLast -> SourceGroup.LAST_USED_KEY
            Pin.Actual in it.pin -> SourceGroup.PINNED_KEY
            else -> it.lang
        }
    }

    return map.flatMap { (language, languageSources) ->
        listOf(
            SourceUiModel.Header(language),
            *languageSources.map(SourceUiModel::Item).toTypedArray(),
        )
    }
}

private fun SuwayomiSourceDto.domainId(): Long {
    return id.toLongOrNull() ?: id.hashCode().absoluteValue.toLong()
}

private const val BROWSE_PAGE_COUNT = 3
