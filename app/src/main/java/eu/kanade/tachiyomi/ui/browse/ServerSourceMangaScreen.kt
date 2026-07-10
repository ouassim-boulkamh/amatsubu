package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.FetchSourceMangaType
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterDto
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.ServerForegroundRefreshEffect
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class ServerSourceMangaScreen(
    private val sourceId: String,
    private val sourceName: String,
    private val sourceDisplayName: String,
    private val supportsLatest: Boolean,
    private val isConfigurable: Boolean,
    private val initialTypeName: String = FetchSourceMangaType.POPULAR.name,
    private val initialQuery: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        val baseUrl = remember { provider.baseUrl() }
        var sourceType by remember { mutableStateOf(FetchSourceMangaType.valueOf(initialTypeName)) }
        var toolbarQuery by remember { mutableStateOf<String?>(initialQuery) }
        var submittedQuery by remember { mutableStateOf(initialQuery) }
        var page by remember { mutableIntStateOf(1) }
        var hasNextPage by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var mangas by remember { mutableStateOf<List<SuwayomiMangaDto>>(emptyList()) }
        var filterDtos by remember { mutableStateOf<List<SuwayomiSourceFilterDto>>(emptyList()) }
        var filters by remember { mutableStateOf(SourceFilterList()) }
        var filtersLoading by remember { mutableStateOf(false) }
        var filtersError by remember { mutableStateOf<String?>(null) }
        var showFilterSheet by remember { mutableStateOf(false) }
        val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
        val hideInLibraryItems = remember { sourcePreferences.hideInLibraryItems.get() }
        val visibleMangas = remember(mangas) {
            if (hideInLibraryItems) {
                mangas.filterNot { it.inLibrary }
            } else {
                mangas
            }
        }

        fun loadPage(reset: Boolean) {
            if (isLoading) return
            scope.launch {
                isLoading = true
                errorMessage = null
                runCatching {
                    withIOContext {
                        provider.graphQlClient.fetchSourceManga(
                            sourceId = sourceId,
                            type = sourceType,
                            page = if (reset) 1 else page,
                            queryText = submittedQuery,
                            filters = filterDtos.toFilterChanges(filters),
                        )
                    }
                }.onSuccess { result ->
                    if (reset) {
                        mangas = result.mangas
                        page = 2
                    } else {
                        mangas = mangas + result.mangas
                        page += 1
                    }
                    hasNextPage = result.hasNextPage
                }.onFailure { error ->
                    errorMessage = error.message ?: error.toString()
                }
                isLoading = false
            }
        }

        fun switchType(type: FetchSourceMangaType) {
            if (sourceType == type) return
            sourceType = type
            if (type != FetchSourceMangaType.SEARCH) {
                toolbarQuery = null
                submittedQuery = null
            }
            page = 1
            hasNextPage = true
            mangas = emptyList()
            loadPage(reset = true)
        }

        fun resetFilters() {
            filters = filterDtos.toSourceFilterList()
            page = 1
            hasNextPage = true
            mangas = emptyList()
            loadPage(reset = true)
        }

        fun applyFilters() {
            sourceType = FetchSourceMangaType.SEARCH
            page = 1
            hasNextPage = true
            mangas = emptyList()
            loadPage(reset = true)
        }

        fun loadFilters() {
            if (filtersLoading) return
            scope.launch {
                filtersLoading = true
                filtersError = null
                runCatching {
                    withIOContext {
                        provider.graphQlClient.sourceFilters(sourceId)
                    }
                }.onSuccess { result ->
                    filterDtos = result
                    filters = result.toSourceFilterList()
                }.onFailure { error ->
                    filtersError = error.message ?: error.toString()
                }
                filtersLoading = false
            }
        }

        LaunchedEffect(Unit) {
            loadFilters()
            loadPage(reset = true)
        }

        ServerForegroundRefreshEffect {
            loadPage(reset = true)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    loadPage(reset = true)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Scaffold(
            topBar = {
                Column {
                    SearchToolbar(
                        searchQuery = toolbarQuery,
                        onChangeSearchQuery = { toolbarQuery = it },
                        titleContent = { Text(sourceDisplayName.ifBlank { sourceName }) },
                        navigateUp = navigator::pop,
                        onSearch = { query ->
                            sourceType = FetchSourceMangaType.SEARCH
                            submittedQuery = query
                            page = 1
                            hasNextPage = true
                            mangas = emptyList()
                            loadPage(reset = true)
                        },
                        actions = {
                            if (isConfigurable) {
                                AppBarActions(
                                    actions = listOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_settings),
                                            icon = Icons.Outlined.Settings,
                                            onClick = {
                                                navigator.push(
                                                    ServerSourcePreferencesScreen(
                                                        sourceId = sourceId,
                                                        sourceDisplayName = sourceDisplayName.ifBlank { sourceName },
                                                    ),
                                                )
                                            },
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = sourceType == FetchSourceMangaType.POPULAR,
                            onClick = { switchType(FetchSourceMangaType.POPULAR) },
                            label = { Text(stringResource(MR.strings.popular)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        )
                        if (supportsLatest) {
                            FilterChip(
                                selected = sourceType == FetchSourceMangaType.LATEST,
                                onClick = { switchType(FetchSourceMangaType.LATEST) },
                                label = { Text(stringResource(MR.strings.latest)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                        FilterChip(
                            selected = sourceType == FetchSourceMangaType.SEARCH,
                            onClick = {
                                sourceType = FetchSourceMangaType.SEARCH
                                toolbarQuery = submittedQuery.orEmpty()
                            },
                            label = { Text(stringResource(MR.strings.action_search)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        )
                        if (filtersLoading || filters.isNotEmpty() || filtersError != null) {
                            FilterChip(
                                selected = sourceType == FetchSourceMangaType.SEARCH &&
                                    filterDtos.toFilterChanges(filters).isNotEmpty(),
                                onClick = {
                                    if (filtersError != null && filters.isEmpty()) {
                                        loadFilters()
                                    } else {
                                        showFilterSheet = true
                                    }
                                },
                                label = {
                                    Text(
                                        when {
                                            filtersLoading -> stringResource(MR.strings.loading)
                                            filtersError != null && filters.isEmpty() -> stringResource(
                                                MR.strings.action_retry,
                                            )
                                            else -> stringResource(MR.strings.action_filter)
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            },
        ) { contentPadding ->
            when {
                isLoading && mangas.isEmpty() -> LoadingScreen(Modifier.padding(contentPadding))
                errorMessage != null && mangas.isEmpty() -> EmptyScreen(
                    message = errorMessage.orEmpty(),
                    modifier = Modifier.padding(contentPadding),
                )
                visibleMangas.isEmpty() && !hasNextPage -> EmptyScreen(
                    message = stringResource(MR.strings.no_results_found),
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        end = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleMangas, key = { it.id }) { manga ->
                        ServerSourceMangaItem(
                            manga = manga,
                            baseUrl = baseUrl,
                            onClick = { navigator.push(MangaScreen(manga.id.toLong(), true)) },
                        )
                    }
                    if (hasNextPage || isLoading || errorMessage != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (hasNextPage && !isLoading && errorMessage == null) {
                                LaunchedEffect(page, visibleMangas.size) {
                                    loadPage(reset = false)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when {
                                    isLoading -> CircularProgressIndicator()
                                    errorMessage != null -> Button(onClick = { loadPage(reset = false) }) {
                                        Text(stringResource(MR.strings.action_retry))
                                    }
                                    hasNextPage -> CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFilterSheet) {
            SourceFilterDialog(
                onDismissRequest = { showFilterSheet = false },
                filters = filters,
                onReset = ::resetFilters,
                onFilter = ::applyFilters,
                onUpdate = { filters = it },
            )
        }
    }
}

@Composable
private fun ServerSourceMangaItem(
    manga: SuwayomiMangaDto,
    baseUrl: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            MangaCover.Book(
                data = manga.thumbnailUrl?.let { resolveServerUrl(baseUrl, it) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (manga.inLibrary) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(MR.strings.in_library),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = manga.title,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
