package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionFilterScreen
import eu.kanade.presentation.browse.ExtensionFilterState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.hasNsfwContent
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class ServerExtensionsFilterScreen(
    private val refreshKey: Int = 0,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val sourcePreferences = remember(context) { context.appDependencies.sourcePreferences }
        val enabledLanguages by sourcePreferences.enabledLanguages.changes()
            .collectAsState(sourcePreferences.enabledLanguages.get())
        val showNsfwSources by sourcePreferences.showNsfwSource.changes()
            .collectAsState(sourcePreferences.showNsfwSource.get())
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        val state by produceState<ServerExtensionsFilterState>(
            initialValue = ServerExtensionsFilterState.Loading,
            key1 = showNsfwSources,
            key2 = refreshKey,
        ) {
            value = runCatching {
                withIOContext {
                    provider.graphQlClient.extensionList()
                        .asSequence()
                        .filter { showNsfwSources || !it.hasNsfwContent() }
                        .map { it.lang }
                        .distinct()
                        .sortedWith(LocaleHelper.comparator)
                        .toList()
                }
            }.fold(
                onSuccess = ServerExtensionsFilterState::Success,
                onFailure = ServerExtensionsFilterState::Error,
            )
        }

        when (val current = state) {
            ServerExtensionsFilterState.Loading -> {
                ServerExtensionsFilterScaffold(navigateUp = navigator::pop) { contentPadding ->
                    LoadingScreen(Modifier.padding(contentPadding))
                }
            }
            is ServerExtensionsFilterState.Error -> {
                ServerExtensionsFilterScaffold(navigateUp = navigator::pop) { contentPadding ->
                    EmptyScreen(
                        message = stringResource(MR.strings.server_sources_load_error),
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is ServerExtensionsFilterState.Success -> {
                ExtensionFilterScreen(
                    navigateUp = navigator::pop,
                    state = ExtensionFilterState.Success(
                        languages = current.languages,
                        enabledLanguages = enabledLanguages,
                    ),
                    onClickToggle = { language ->
                        sourcePreferences.enabledLanguages.getAndSet { enabled ->
                            if (language in enabled) enabled - language else enabled + language
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ServerExtensionsFilterScaffold(
    navigateUp: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        content(contentPadding)
    }
}

private sealed interface ServerExtensionsFilterState {
    data object Loading : ServerExtensionsFilterState
    data class Success(val languages: List<String>) : ServerExtensionsFilterState
    data class Error(val throwable: Throwable) : ServerExtensionsFilterState
}
