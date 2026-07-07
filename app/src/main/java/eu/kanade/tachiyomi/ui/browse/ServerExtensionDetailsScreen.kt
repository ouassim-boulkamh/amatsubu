package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.ExtensionDetailsScreen
import eu.kanade.presentation.browse.ExtensionDetailsState
import eu.kanade.presentation.browse.ExtensionSourceItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceDto
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.sourceNodes
import eu.kanade.tachiyomi.data.suwayomi.webUrl
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.absoluteValue

data class ServerExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val network = remember { Injekt.get<NetworkHelper>() }
        val disabledSources by sourcePreferences.disabledSources.changes()
            .collectAsState(sourcePreferences.disabledSources.get())
        val incognitoExtensions by sourcePreferences.incognitoExtensions.changes()
            .collectAsState(sourcePreferences.incognitoExtensions.get())
        val provider = remember { SuwayomiClientProvider() }
        val baseUrl = remember { provider.baseUrl() }
        var reloadVersion by remember { mutableIntStateOf(0) }
        val state by produceState<ServerExtensionDetailsState>(
            ServerExtensionDetailsState.Loading,
            pkgName,
            reloadVersion,
            disabledSources,
            incognitoExtensions,
        ) {
            value = runCatching {
                withIOContext {
                    provider.graphQlClient.extensionList()
                        .firstOrNull { it.pkgName == pkgName }
                }
            }.fold(
                onSuccess = { extension ->
                    if (extension == null || !extension.isInstalled) {
                        ServerExtensionDetailsState.Empty
                    } else {
                        ServerExtensionDetailsState.Success(
                            extension = extension,
                            mihonState = extension.toMihonDetailsState(
                                disabledSources = disabledSources,
                                incognitoExtensions = incognitoExtensions,
                            ),
                        )
                    }
                },
                onFailure = ServerExtensionDetailsState::Error,
            )
        }

        when (val current = state) {
            ServerExtensionDetailsState.Loading -> {
                ServerExtensionDetailsScaffold(navigateUp = navigator::pop) { contentPadding ->
                    LoadingScreen(Modifier.padding(contentPadding))
                }
            }
            ServerExtensionDetailsState.Empty -> {
                ServerExtensionDetailsScaffold(navigateUp = navigator::pop) { contentPadding ->
                    EmptyScreen(
                        message = pkgName,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is ServerExtensionDetailsState.Error -> {
                ServerExtensionDetailsScaffold(navigateUp = navigator::pop) { contentPadding ->
                    EmptyScreen(
                        message = stringResource(MR.strings.server_sources_load_error),
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is ServerExtensionDetailsState.Success -> {
                val extensionSources = current.extension.sourceNodes()
                val sourcesById = extensionSources.associateBy { it.domainId() }
                ExtensionDetailsScreen(
                    navigateUp = navigator::pop,
                    state = current.mihonState,
                    onClickSourcePreferences = { sourceId ->
                        sourcesById[sourceId]?.let { source ->
                            navigator.push(
                                ServerSourcePreferencesScreen(
                                    sourceId = source.id,
                                    sourceDisplayName = source.name,
                                ),
                            )
                        }
                    },
                    onClickEnableAll = {
                        sourcePreferences.disabledSources.getAndSet { disabled ->
                            disabled - extensionSources.map { it.domainId().toString() }.toSet()
                        }
                    },
                    onClickDisableAll = {
                        sourcePreferences.disabledSources.getAndSet { disabled ->
                            disabled + extensionSources.map { it.domainId().toString() }
                        }
                    },
                    onClickClearCookies = {
                        val cleared = extensionSources.sumOf { source ->
                            source.webUrl()
                                ?.toHttpUrlOrNull()
                                ?.let(network.cookieJar::remove)
                                ?: 0
                        }
                        logcat { "Cleared $cleared cookies for extension: $pkgName" }
                    },
                    onClickUninstall = {
                        scope.launch {
                            runCatching {
                                withIOContext {
                                    provider.graphQlClient.updateExtension(pkgName = pkgName, uninstall = true)
                                }
                            }.onSuccess {
                                ServerStateSync.requestRefresh()
                                navigator.pop()
                            }
                        }
                    },
                    onClickSource = { sourceId ->
                        sourcesById[sourceId]?.let { source ->
                            navigator.push(
                                ServerSourceMangaScreen(
                                    sourceId = source.id,
                                    sourceName = source.name,
                                    sourceDisplayName = source.name,
                                    supportsLatest = source.supportsLatest,
                                    isConfigurable = source.isConfigurable,
                                ),
                            )
                        }
                    },
                    onClickIncognito = { enable ->
                        sourcePreferences.incognitoExtensions.getAndSet { incognito ->
                            if (enable) incognito + pkgName else incognito - pkgName
                        }
                    },
                    extensionIcon = { _, modifier ->
                        AsyncImage(
                            model = current.extension.iconUrl?.let { resolveServerUrl(baseUrl, it) },
                            contentDescription = null,
                            modifier = modifier,
                        )
                    },
                    showAppInfoButton = false,
                )
            }
        }

    }
}

@Composable
private fun ServerExtensionDetailsScaffold(
    navigateUp: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            eu.kanade.presentation.components.AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        content(contentPadding)
    }
}

private fun SuwayomiExtensionDto.toMihonDetailsState(
    disabledSources: Set<String>,
    incognitoExtensions: Set<String>,
): ExtensionDetailsState {
    val installed = toInstalledExtension()
    return ExtensionDetailsState(
        extension = installed,
        isIncognito = pkgName in incognitoExtensions,
        _sources = installed.sources.map { source ->
            ExtensionSourceItem(
                source = source,
                enabled = source.id.toString() !in disabledSources,
                labelAsName = installed.sources.size > 1 &&
                    installed.sources.map { it.name }.distinct().size != 1,
            )
        },
    )
}

private sealed interface ServerExtensionDetailsState {
    data object Loading : ServerExtensionDetailsState
    data object Empty : ServerExtensionDetailsState
    data class Success(
        val extension: SuwayomiExtensionDto,
        val mihonState: ExtensionDetailsState,
    ) : ServerExtensionDetailsState
    data class Error(val throwable: Throwable) : ServerExtensionDetailsState
}

private fun SuwayomiSourceDto.domainId(): Long {
    return id.toLongOrNull() ?: id.hashCode().absoluteValue.toLong()
}
