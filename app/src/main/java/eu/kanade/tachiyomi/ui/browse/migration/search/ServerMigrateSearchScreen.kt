package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import eu.kanade.tachiyomi.ui.browse.ServerSourceMangaScreen
import eu.kanade.tachiyomi.ui.browse.migration.toMigrationManga
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.absoluteValue

data class ServerMigrateSearchScreen(
    private val currentMangaId: Long,
    private val currentMangaIds: List<Long> = listOf(currentMangaId),
    private val currentIndex: Int = 0,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val provider = remember { SuwayomiClientProvider() }
        val baseUrl = remember { provider.baseUrl() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val queue = remember(currentMangaId, currentMangaIds) {
            currentMangaIds.ifEmpty { listOf(currentMangaId) }
        }
        val queueIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val activeMangaId = queue[queueIndex]
        var currentManga by remember { mutableStateOf<SuwayomiMangaDto?>(null) }
        var query by remember { mutableStateOf<String?>(null) }
        var loadError by remember { mutableStateOf<Throwable?>(null) }
        var hasSearched by remember { mutableStateOf(false) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var dialogTarget by remember { mutableStateOf<SuwayomiMangaDto?>(null) }
        val sources = remember { mutableStateOf<List<SuwayomiSourceDto>>(emptyList()) }
        val results = remember { mutableStateMapOf<String, ServerMigrationSearchResult>() }

        fun visibleSources(allSources: List<SuwayomiSourceDto>): List<SuwayomiSourceDto> {
            val enabledLanguages = sourcePreferences.enabledLanguages.get()
            val disabledSources = sourcePreferences.disabledSources.get()
            val showNsfwSources = sourcePreferences.showNsfwSource.get()
            val migrationSources = sourcePreferences.migrationSources.get().toSet()
            return allSources
                .filterNot(SuwayomiSourceDto::isLocalFolderSource)
                .filter { showNsfwSources || !it.hasNsfwContent() }
                .filter { it.lang in enabledLanguages }
                .filterNot { it.domainId().toString() in disabledSources }
                .filter { migrationSources.isEmpty() || it.domainId() in migrationSources }
                .sortedWith(compareBy<SuwayomiSourceDto> { it.lang }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        fun search(submittedQuery: String) {
            if (submittedQuery.isBlank()) return
            query = submittedQuery
            hasSearched = true
            loadError = null
            searchJob?.cancel()
            results.clear()
            searchJob = scope.launch {
                val selectedSources = runCatching {
                    withIOContext { visibleSources(provider.graphQlClient.sourceList()) }
                }.getOrElse { error ->
                    loadError = error
                    sources.value = emptyList()
                    return@launch
                }

                sources.value = selectedSources
                selectedSources.forEach { results[it.id] = ServerMigrationSearchResult.Loading }
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
                                onSuccess = ServerMigrationSearchResult::Success,
                                onFailure = ServerMigrationSearchResult::Error,
                            )
                            results[source.id] = result
                        }
                    }
                }
            }
        }

        LaunchedEffect(activeMangaId) {
            searchJob?.cancel()
            currentManga = null
            query = null
            loadError = null
            hasSearched = false
            results.clear()
            sources.value = emptyList()
            runCatching {
                withIOContext { provider.graphQlClient.getManga(activeMangaId.toInt()) }
            }.onSuccess {
                currentManga = it
                search(it.title)
            }.onFailure {
                loadError = it
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = query,
                    onChangeSearchQuery = { query = it },
                    navigateUp = navigator::pop,
                    onSearch = ::search,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val progress = results.values.count { it !is ServerMigrationSearchResult.Loading }
            val total = results.size
            when {
                currentManga == null && loadError == null -> LoadingScreen(Modifier.padding(contentPadding))
                loadError != null -> EmptyScreen(
                    message = loadError?.message.orEmpty(),
                    modifier = Modifier.padding(contentPadding),
                )
                query.isNullOrBlank() -> EmptyScreen(
                    message = "",
                    modifier = Modifier.padding(contentPadding),
                )
                hasSearched && total == 0 -> EmptyScreen(
                    message = "",
                    modifier = Modifier.padding(contentPadding),
                )
                results.isEmpty() && progress == total -> EmptyScreen(
                    message = "",
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    sources.value.mapNotNull { source -> results[source.id]?.let { source to it } }
                        .forEach { (source, result) ->
                            item(key = source.id) {
                                ServerMigrationSourceResult(
                                    source = source,
                                    result = result,
                                    baseUrl = baseUrl,
                                    onClickSource = {
                                        navigator.push(
                                            ServerSourceMangaScreen(
                                                sourceId = source.id,
                                                sourceName = source.name,
                                                sourceDisplayName = source.displayName.ifBlank { source.name },
                                                supportsLatest = source.supportsLatest,
                                                isConfigurable = source.isConfigurable,
                                                initialTypeName = FetchSourceMangaType.SEARCH.name,
                                                initialQuery = query,
                                            ),
                                        )
                                    },
                                    onClickManga = { manga -> dialogTarget = manga },
                                )
                            }
                        }
                }
            }
        }

        val current = currentManga
        val target = dialogTarget
        if (current != null && target != null) {
            MigrateMangaDialog(
                current = current.toMigrationManga(provider),
                target = target.toMigrationManga(provider),
                onClickTitle = { navigator.push(MangaScreen(target.id.toLong(), true)) },
                onDismissRequest = { dialogTarget = null },
                onComplete = {
                    dialogTarget = null
                    val nextIndex = queueIndex + 1
                    if (nextIndex < queue.size) {
                        navigator.replace(
                            ServerMigrateSearchScreen(
                                currentMangaId = queue[nextIndex],
                                currentMangaIds = queue,
                                currentIndex = nextIndex,
                            ),
                        )
                    } else {
                        navigator.replace(MangaScreen(target.id.toLong(), true))
                    }
                },
            )
        }
    }
}

@Composable
private fun ServerMigrationSourceResult(
    source: SuwayomiSourceDto,
    result: ServerMigrationSearchResult,
    baseUrl: String,
    onClickSource: () -> Unit,
    onClickManga: (SuwayomiMangaDto) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.extraSmall)
                .fillMaxWidth()
                .clickable(onClick = onClickSource),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = source.displayName.ifBlank { source.name }, style = MaterialTheme.typography.titleMedium)
                Text(text = source.lang)
            }
            IconButton(onClick = onClickSource) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
        when (result) {
            ServerMigrationSearchResult.Loading -> {
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
            is ServerMigrationSearchResult.Error -> {
                Column(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = Icons.Outlined.Error, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(text = result.throwable.message.orEmpty(), textAlign = TextAlign.Center)
                }
            }
            is ServerMigrationSearchResult.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    items(result.mangas, key = { it.id }) { manga ->
                        ServerMigrationMangaItem(
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

@Composable
private fun ServerMigrationMangaItem(
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

private sealed interface ServerMigrationSearchResult {
    data object Loading : ServerMigrationSearchResult
    data class Error(val throwable: Throwable) : ServerMigrationSearchResult
    data class Success(val mangas: List<SuwayomiMangaDto>) : ServerMigrationSearchResult
}

private fun SuwayomiSourceDto.domainId(): Long {
    return id.toLongOrNull() ?: id.hashCode().absoluteValue.toLong()
}
