package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionStoreDto
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI

object SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInLibraryItems,
                        title = stringResource(MR.strings.pref_hide_in_library_items),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_extensions),
                preferenceItems = listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.extensionStores),
                        subtitle = stringResource(MR.strings.pref_extension_stores_summary),
                        onClick = { navigator.push(ServerExtensionReposScreen()) },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_nsfw_content),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.showNsfwSource,
                        title = stringResource(MR.strings.pref_show_nsfw_source),
                        subtitle = stringResource(MR.strings.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.parental_controls_info)),
                ),
            ),
        )
    }
}

private class ServerExtensionReposScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val provider = remember { SuwayomiClientProvider() }
        var reloadVersion by remember { mutableIntStateOf(0) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showAddDialog by rememberSaveable { mutableStateOf(false) }
        val state by produceState<ExtensionReposState>(
            initialValue = ExtensionReposState.Loading,
            key1 = reloadVersion,
        ) {
            value = runCatching {
                withIOContext {
                    provider.graphQlClient.extensionStores()
                }
            }.fold(
                onSuccess = ExtensionReposState::Success,
                onFailure = ExtensionReposState::Error,
            )
        }

        fun addStore(indexUrl: String) {
            scope.launch {
                runCatching {
                    withIOContext {
                        provider.graphQlClient.addExtensionStore(indexUrl)
                    }
                }.onSuccess {
                    ServerStateSync.requestRefresh()
                    reloadVersion++
                }.onFailure { error ->
                    errorMessage = error.message ?: context.stringResource(MR.strings.server_sources_load_error)
                }
            }
        }

        fun removeStore(indexUrl: String) {
            scope.launch {
                runCatching {
                    withIOContext {
                        provider.graphQlClient.removeExtensionStore(indexUrl)
                    }
                }.onSuccess {
                    ServerStateSync.requestRefresh()
                    reloadVersion++
                }.onFailure { error ->
                    errorMessage = error.message ?: context.stringResource(MR.strings.server_sources_load_error)
                }
            }
        }

        PreferenceScaffold(
            titleRes = MR.strings.extensionStores,
            onBackPressed = navigator::pop,
            actions = {
                AppBarActions(
                    listOf(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_add),
                            icon = Icons.Outlined.Add,
                            onClick = { showAddDialog = true },
                        ),
                    ),
                )
            },
        ) {
            when (val current = state) {
                ExtensionReposState.Loading -> emptyList()
                is ExtensionReposState.Error -> listOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = current.throwable.message ?: stringResource(MR.strings.server_sources_load_error),
                    ),
                )
                is ExtensionReposState.Success -> {
                    val stores = current.stores
                    listOf(
                        Preference.PreferenceGroup(
                            title = stringResource(MR.strings.label_extensions),
                            preferenceItems = buildList {
                                errorMessage?.let {
                                    add(Preference.PreferenceItem.InfoPreference(title = it))
                                }
                                if (stores.isEmpty()) {
                                    add(
                                        Preference.PreferenceItem.InfoPreference(
                                            title = stringResource(MR.strings.extensionStoresScreen_emptyLabel),
                                        ),
                                    )
                                }
                                stores.forEach { store ->
                                    add(
                                        Preference.PreferenceItem.TextPreference(
                                            title = store.name,
                                            subtitle = store.summary(),
                                            widget = {
                                                Row {
                                                    IconButton(
                                                        onClick = { context.copyToClipboard(store.indexUrl, store.indexUrl) },
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ContentCopy,
                                                            contentDescription = stringResource(
                                                                MR.strings.action_copy_to_clipboard,
                                                            ),
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { removeStore(store.indexUrl) },
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Delete,
                                                            contentDescription = stringResource(MR.strings.action_delete),
                                                        )
                                                    }
                                                }
                                            },
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }

        if (showAddDialog) {
            AddExtensionRepoDialog(
                onDismissRequest = { showAddDialog = false },
                onAdd = { repo ->
                    addStore(repo.trim())
                    showAddDialog = false
                },
            )
        }
    }
}

@Composable
private fun AddExtensionRepoDialog(
    onDismissRequest: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val isValid = remember(text) { text.trim().isValidRepoUrl() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.extensionStoresScreen_addStore_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(text = stringResource(MR.strings.extensionStoresScreen_addStoreInput_inputLabel)) },
                isError = text.isNotBlank() && !isValid,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onAdd(text) },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private sealed interface ExtensionReposState {
    data object Loading : ExtensionReposState
    data class Success(val stores: List<SuwayomiExtensionStoreDto>) : ExtensionReposState
    data class Error(val throwable: Throwable) : ExtensionReposState
}

@Composable
private fun SuwayomiExtensionStoreDto.summary(): String {
    return buildList {
        add(indexUrl)
        extensionListUrl?.takeIf { it.isNotBlank() && it != indexUrl }?.let { listUrl ->
            add(stringResource(MR.strings.pref_extension_store_list_url, listUrl))
        }
        if (badgeLabel.isNotBlank()) add(badgeLabel)
        if (isLegacy) add(stringResource(MR.strings.pref_extension_store_legacy))
        if (contactWebsite.isNotBlank()) {
            add(stringResource(MR.strings.pref_extension_store_contact, contactWebsite))
        }
        contactDiscord?.takeIf { it.isNotBlank() }?.let { contact ->
            add(stringResource(MR.strings.pref_extension_store_contact, contact))
        }
        if (signingKey.isNotBlank()) {
            add(stringResource(MR.strings.pref_extension_store_signing_key, signingKey))
        }
    }.joinToString("\n")
}

private fun String.isValidRepoUrl(): Boolean {
    return runCatching {
        val uri = URI(trim())
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
