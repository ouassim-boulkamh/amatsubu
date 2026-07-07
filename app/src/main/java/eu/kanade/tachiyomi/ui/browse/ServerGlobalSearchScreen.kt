package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.SourceFilter
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.FetchSourceMangaType
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceDto
import eu.kanade.tachiyomi.data.suwayomi.hasNsfwContent
import eu.kanade.tachiyomi.data.suwayomi.isLocalFolderSource
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.absoluteValue

data class ServerGlobalSearchScreen(
    private val initialQuery: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val provider = remember { SuwayomiClientProvider() }
        val baseUrl = remember { provider.baseUrl() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        var query by remember { mutableStateOf<String?>(initialQuery) }
        val initialSourceFilter = remember {
            if (sourcePreferences.pinnedSources.get().isEmpty()) SourceFilter.All else SourceFilter.PinnedOnly
        }
        var sourceFilter by remember { mutableStateOf(initialSourceFilter) }
        var sources by remember { mutableStateOf<List<SuwayomiSourceDto>>(emptyList()) }
        var listError by remember { mutableStateOf<Throwable?>(null) }
        var hasSearched by remember { mutableStateOf(false) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        val results = remember { mutableStateMapOf<String, ServerSearchItemResult>() }
        val onlyShowHasResults by sourcePreferences.globalSearchFilterState.changes()
            .collectAsState(sourcePreferences.globalSearchFilterState.get())

        fun visibleSources(allSources: List<SuwayomiSourceDto>): List<SuwayomiSourceDto> {
            val enabledLanguages = sourcePreferences.enabledLanguages.get()
            val disabledSources = sourcePreferences.disabledSources.get()
            val pinnedSources = sourcePreferences.pinnedSources.get()
            val showNsfwSources = sourcePreferences.showNsfwSource.get()
            return allSources
                .filterNot(SuwayomiSourceDto::isLocalFolderSource)
                .filter { showNsfwSources || !it.hasNsfwContent() }
                .filter { it.lang in enabledLanguages }
                .filterNot { it.domainId().toString() in disabledSources }
                .filter { sourceFilter != SourceFilter.PinnedOnly || it.domainId().toString() in pinnedSources }
                .sortedWith(compareBy<SuwayomiSourceDto> { it.lang }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        fun search(submittedQuery: String) {
            if (submittedQuery.isBlank()) return
            searchJob?.cancel()
            results.clear()
            listError = null
            hasSearched = true
            searchJob = scope.launch {
                val selectedSources = runCatching {
                    withIOContext {
                        visibleSources(provider.graphQlClient.sourceList())
                    }
                }.getOrElse { error ->
                    listError = error
                    sources = emptyList()
                    return@launch
                }

                sources = selectedSources
                selectedSources.forEach { source -> results[source.id] = ServerSearchItemResult.Loading }

                val semaphore = Semaphore(5)
                selectedSources.forEach { source ->
                    launch {
                        semaphore.withPermit {
                            val result = runCatching {
                                withIOContext {
                                    provider.graphQlClient.fetchSourceManga(
                                        sourceId = source.id,
                                        type = FetchSourceMangaType.SEARCH,
                                        page = 1,
                                        queryText = submittedQuery,
                                    ).mangas.distinctBy { it.id }
                                }
                            }.fold(
                                onSuccess = ServerSearchItemResult::Success,
                                onFailure = ServerSearchItemResult::Error,
                            )
                            results[source.id] = result
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (initialQuery.isNotBlank()) {
                search(initialQuery)
            }
        }

        val progress = results.values.count { it !is ServerSearchItemResult.Loading }
        val total = results.size
        Scaffold(
            topBar = { scrollBehavior ->
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    Box {
                        SearchToolbar(
                            searchQuery = query,
                            onChangeSearchQuery = { query = it },
                            onSearch = { submitted ->
                                query = submitted
                                search(submitted)
                            },
                            onClickCloseSearch = navigator::pop,
                            navigateUp = navigator::pop,
                            scrollBehavior = scrollBehavior,
                        )
                        if (progress in 1..<total) {
                            LinearProgressIndicator(
                                progress = { progress / total.toFloat() },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = sourceFilter == SourceFilter.PinnedOnly,
                            onClick = {
                                sourceFilter = SourceFilter.PinnedOnly
                                query?.let(::search)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(MR.strings.pinned_sources)) },
                        )
                        FilterChip(
                            selected = sourceFilter == SourceFilter.All,
                            onClick = {
                                sourceFilter = SourceFilter.All
                                query?.let(::search)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(MR.strings.all)) },
                        )
                        FilterChip(
                            selected = onlyShowHasResults,
                            onClick = { sourcePreferences.globalSearchFilterState.toggle() },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(MR.strings.has_results)) },
                        )
                    }
                    HorizontalDivider()
                }
            },
        ) { contentPadding ->
            val visibleResults = sources
                .mapNotNull { source -> results[source.id]?.let { source to it } }
                .filter { (_, result) ->
                    !onlyShowHasResults ||
                        (result is ServerSearchItemResult.Success && result.mangas.isNotEmpty())
                }

            when {
                listError != null -> EmptyScreen(
                    message = listError?.message ?: stringResource(MR.strings.server_sources_load_error),
                    modifier = Modifier.padding(contentPadding),
                )
                query.isNullOrBlank() -> EmptyScreen(
                    message = stringResource(MR.strings.action_search),
                    modifier = Modifier.padding(contentPadding),
                )
                hasSearched && total == 0 -> EmptyScreen(
                    message = stringResource(MR.strings.server_sources_empty),
                    modifier = Modifier.padding(contentPadding),
                )
                visibleResults.isEmpty() && total > 0 && progress == total -> EmptyScreen(
                    message = stringResource(MR.strings.no_results_found),
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    visibleResults.forEach { (source, result) ->
                        item(key = source.id) {
                            ServerSearchSourceResult(
                                source = source,
                                result = result,
                                baseUrl = baseUrl,
                                onClickSource = {
                                    navigator.push(
                                        ServerSourceMangaScreen(
                                            sourceId = source.id,
                                            sourceName = source.name,
                                            sourceDisplayName = source.name,
                                            supportsLatest = source.supportsLatest,
                                            isConfigurable = source.isConfigurable,
                                            initialTypeName = FetchSourceMangaType.SEARCH.name,
                                            initialQuery = query,
                                        ),
                                    )
                                },
                                onClickManga = { manga -> navigator.push(MangaScreen(manga.id.toLong(), true)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSearchSourceResult(
    source: SuwayomiSourceDto,
    result: ServerSearchItemResult,
    baseUrl: String,
    onClickSource: () -> Unit,
    onClickManga: (SuwayomiMangaDto) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.extraSmall,
                )
                .fillMaxWidth()
                .clickable(onClick = onClickSource),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = source.lang)
            }
            IconButton(onClick = onClickSource) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
        when (result) {
            ServerSearchItemResult.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.medium),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                }
            }
            is ServerSearchItemResult.Error -> {
                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        )
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = Icons.Outlined.Error, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.throwable.message ?: stringResource(MR.strings.unknown_error),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            is ServerSearchItemResult.Success -> {
                if (result.mangas.isEmpty()) {
                    Text(
                        text = stringResource(MR.strings.no_results_found),
                        modifier = Modifier
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        items(result.mangas, key = { it.id }) { manga ->
                            ServerSearchMangaItem(
                                manga = manga,
                                baseUrl = baseUrl,
                                onClick = { onClickManga(manga) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSearchMangaItem(
    manga: SuwayomiMangaDto,
    baseUrl: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        MangaCover.Book(
            data = manga.thumbnailUrl?.let { resolveServerUrl(baseUrl, it) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = manga.title,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private sealed interface ServerSearchItemResult {
    data object Loading : ServerSearchItemResult
    data class Error(val throwable: Throwable) : ServerSearchItemResult
    data class Success(val mangas: List<SuwayomiMangaDto>) : ServerSearchItemResult
}

private fun SuwayomiSourceDto.domainId(): Long {
    return id.toLongOrNull() ?: id.hashCode().absoluteValue.toLong()
}
