package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceDto
import eu.kanade.tachiyomi.data.suwayomi.hasNsfwContent
import eu.kanade.tachiyomi.data.suwayomi.isLocalFolderSource
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.math.absoluteValue

data class ServerSourcesFilterScreen(
    private val refreshKey: Int = 0,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
        val enabledLanguages by sourcePreferences.enabledLanguages.changes()
            .collectAsState(sourcePreferences.enabledLanguages.get())
        val disabledSources by sourcePreferences.disabledSources.changes()
            .collectAsState(sourcePreferences.disabledSources.get())
        val showNsfwSources by sourcePreferences.showNsfwSource.changes()
            .collectAsState(sourcePreferences.showNsfwSource.get())
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        val baseUrl = remember { provider.baseUrl() }
        val errorMessage = stringResource(MR.strings.server_sources_load_error)
        val state by produceState<ServerSourcesFilterState>(
            initialValue = ServerSourcesFilterState.Loading,
            key1 = refreshKey,
            key2 = showNsfwSources,
        ) {
            value = runCatching {
                withIOContext {
                    provider.graphQlClient.sourceList()
                        .filterNot(SuwayomiSourceDto::isLocalFolderSource)
                        .filter { showNsfwSources || !it.hasNsfwContent() }
                }
            }.fold(
                onSuccess = ServerSourcesFilterState::Success,
                onFailure = ServerSourcesFilterState::Error,
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_sources),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when (val current = state) {
                ServerSourcesFilterState.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                is ServerSourcesFilterState.Error -> {
                    LaunchedEffect(current.throwable) {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                    EmptyScreen(
                        message = errorMessage,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                is ServerSourcesFilterState.Success -> {
                    ServerSourcesFilterContent(
                        sources = current.sources,
                        enabledLanguages = enabledLanguages,
                        disabledSources = disabledSources,
                        baseUrl = baseUrl,
                        contentPadding = contentPadding,
                        sourcePreferences = sourcePreferences,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSourcesFilterContent(
    sources: List<SuwayomiSourceDto>,
    enabledLanguages: Set<String>,
    disabledSources: Set<String>,
    baseUrl: String,
    contentPadding: PaddingValues,
    sourcePreferences: SourcePreferences,
) {
    if (sources.isEmpty()) {
        EmptyScreen(
            message = stringResource(MR.strings.server_sources_empty),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    val context = LocalContext.current
    val sourcesByLanguage = sources
        .groupBy { it.lang }
        .toSortedMap(
            compareBy<String> { it !in enabledLanguages }.then(LocaleHelper.comparator),
        )
    LazyColumn(contentPadding = contentPadding) {
        sourcesByLanguage.forEach { (language, languageSources) ->
            val languageEnabled = language in enabledLanguages
            item(key = "language-$language") {
                SwitchPreferenceWidget(
                    title = LocaleHelper.getSourceDisplayName(language, context),
                    checked = languageEnabled,
                    onCheckedChanged = {
                        sourcePreferences.enabledLanguages.getAndSet { enabled ->
                            if (language in enabled) enabled - language else enabled + language
                        }
                    },
                )
                HorizontalDivider()
            }
            if (languageEnabled) {
                val sortedSources = languageSources.sortedWith(
                    compareBy<SuwayomiSourceDto> { it.domainId().toString() in disabledSources }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                items(sortedSources, key = { "source-${it.id}" }) { source ->
                    val sourceKey = source.domainId().toString()
                    val sourceEnabled = sourceKey !in disabledSources
                    ListItem(
                        modifier = Modifier.clickable {
                            sourcePreferences.disabledSources.getAndSet { disabled ->
                                if (sourceKey in disabled) disabled - sourceKey else disabled + sourceKey
                            }
                        },
                        leadingContent = {
                            AsyncImage(
                                model = source.iconUrl?.let { resolveServerUrl(baseUrl, it) },
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(source.name) },
                        trailingContent = {
                            Checkbox(checked = sourceEnabled, onCheckedChange = null)
                        },
                    )
                }
            }
        }
    }
}

private sealed interface ServerSourcesFilterState {
    data object Loading : ServerSourcesFilterState
    data class Success(val sources: List<SuwayomiSourceDto>) : ServerSourcesFilterState
    data class Error(val throwable: Throwable) : ServerSourcesFilterState
}

private fun SuwayomiSourceDto.domainId(): Long {
    return id.toLongOrNull() ?: id.hashCode().absoluteValue.toLong()
}
