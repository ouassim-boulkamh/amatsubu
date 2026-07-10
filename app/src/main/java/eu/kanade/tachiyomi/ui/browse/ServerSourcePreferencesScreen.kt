package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.di.appDependencies
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourcePreferenceChange
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourcePreferenceDto
import eu.kanade.tachiyomi.data.suwayomi.serverSourcePreferenceAffectedEntities
import eu.kanade.tachiyomi.ui.ServerForegroundRefreshEffect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class ServerSourcePreferencesScreen(
    private val sourceId: String,
    private val sourceDisplayName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        var preferences by remember { mutableStateOf<List<SuwayomiSourcePreferenceDto>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        fun loadPreferences(showLoading: Boolean = preferences.isEmpty()) {
            scope.launch {
                if (showLoading) {
                    isLoading = true
                }
                errorMessage = null
                runCatching {
                    withIOContext { provider.graphQlClient.sourcePreferences(sourceId) }
                }.onSuccess {
                    preferences = it
                }.onFailure {
                    errorMessage = it.message ?: it.toString()
                }
                if (showLoading) {
                    isLoading = false
                }
            }
        }

        fun updatePreference(change: SuwayomiSourcePreferenceChange) {
            scope.launch {
                runCatching {
                    withIOContext { provider.graphQlClient.updateSourcePreference(sourceId, change) }
                }.onSuccess {
                    preferences = it
                    errorMessage = null
                    ServerStateSync.requestRefresh(*serverSourcePreferenceAffectedEntities(sourceId).toTypedArray())
                }.onFailure {
                    errorMessage = it.message ?: it.toString()
                }
            }
        }

        LaunchedEffect(sourceId) {
            loadPreferences()
        }

        LaunchedEffect(sourceId) {
            ServerStateSync.invalidations
                .collectLatest { invalidation ->
                    if (
                        invalidation.affectsAny(
                            ServerStateEntity.Sources,
                            ServerStateEntity.SourcePreferences(sourceId),
                        )
                    ) {
                        loadPreferences(showLoading = false)
                    }
            }
        }

        ServerForegroundRefreshEffect {
            loadPreferences(showLoading = false)
        }

        DisposableEffect(lifecycleOwner, sourceId) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    loadPreferences(showLoading = false)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = sourceDisplayName,
                    subtitle = stringResource(MR.strings.label_settings),
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            actions = listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_retry),
                                    icon = Icons.Outlined.Refresh,
                                    onClick = ::loadPreferences,
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                isLoading -> LoadingScreen(modifier = Modifier.fillMaxSize())
                errorMessage != null && preferences.isEmpty() -> EmptyScreen(
                    message = errorMessage.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
                preferences.visiblePreferences().isEmpty() -> EmptyScreen(
                    message = stringResource(MR.strings.no_results_found),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    preferences.visiblePreferences().forEach { positioned ->
                        item(key = "${positioned.index}-${positioned.preference.key}-${positioned.preference.type}") {
                            ServerSourcePreferenceItem(
                                positioned = positioned,
                                onChange = ::updatePreference,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        errorMessage?.takeIf { preferences.isNotEmpty() }?.let {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                TextPreferenceWidget(title = it)
            }
        }
    }
}

@Composable
private fun ServerSourcePreferenceItem(
    positioned: PositionedSourcePreference,
    onChange: (SuwayomiSourcePreferenceChange) -> Unit,
) {
    val pref = positioned.preference
    val title = pref.title ?: pref.key ?: pref.type
    val subtitle = pref.summaryForCurrentValue()
    when (pref.type) {
        "CheckBoxPreference" -> SwitchPreferenceWidget(
            title = title,
            subtitle = subtitle,
            checked = pref.currentBoolean ?: pref.defaultBoolean ?: false,
            onCheckedChanged = {
                onChange(SuwayomiSourcePreferenceChange(position = positioned.index, checkBoxState = it))
            },
        )
        "SwitchPreference" -> SwitchPreferenceWidget(
            title = title,
            subtitle = subtitle,
            checked = pref.currentBoolean ?: pref.defaultBoolean ?: false,
            onCheckedChanged = {
                onChange(SuwayomiSourcePreferenceChange(position = positioned.index, switchState = it))
            },
        )
        "EditTextPreference" -> EditTextPreferenceWidget(
            title = title,
            subtitle = subtitle ?: "%s",
            icon = null,
            value = pref.currentString ?: pref.defaultString.orEmpty(),
            onConfirm = {
                onChange(SuwayomiSourcePreferenceChange(position = positioned.index, editTextState = it))
                true
            },
        )
        "ListPreference" -> {
            val entries = pref.entriesByValue()
            ListPreferenceWidget(
                value = pref.currentString ?: pref.defaultString.orEmpty(),
                title = title,
                subtitle = subtitle,
                icon = null,
                entries = entries,
                onValueChange = {
                    onChange(SuwayomiSourcePreferenceChange(position = positioned.index, listState = it))
                },
            )
        }
        "MultiSelectListPreference" -> MultiSelectPreferenceWidget(
            title = title,
            subtitle = subtitle,
            entries = pref.entriesByValue(),
            values = (pref.currentStringList ?: pref.defaultStringList.orEmpty()).toSet(),
            onValueChange = {
                onChange(SuwayomiSourcePreferenceChange(position = positioned.index, multiSelectState = it.toList()))
            },
        )
        else -> TextPreferenceWidget(
            title = title,
            subtitle = subtitle ?: "Unsupported preference type: ${pref.type}",
        )
    }
}

@Composable
private fun MultiSelectPreferenceWidget(
    title: String,
    subtitle: String?,
    entries: Map<String, String>,
    values: Set<String>,
    onValueChange: (Set<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    TextPreferenceWidget(
        title = title,
        subtitle = subtitle,
        onPreferenceClick = { showDialog = true },
    )
    if (showDialog) {
        val selected = remember(values) { mutableStateListOf(*values.toTypedArray()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    entries.forEach { (value, entry) ->
                        item(key = value) {
                            ListItem(
                                headlineContent = { Text(entry) },
                                leadingContent = {
                                    Checkbox(
                                        checked = value in selected,
                                        onCheckedChange = null,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (value in selected) {
                                        selected.remove(value)
                                    } else {
                                        selected.add(value)
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(selected.toSet())
                        showDialog = false
                    },
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private data class PositionedSourcePreference(
    val index: Int,
    val preference: SuwayomiSourcePreferenceDto,
)

private fun List<SuwayomiSourcePreferenceDto>.visiblePreferences(): List<PositionedSourcePreference> {
    return mapIndexed(::PositionedSourcePreference).filter { it.preference.visible }
}

private fun SuwayomiSourcePreferenceDto.entriesByValue(): Map<String, String> {
    return entryValues.mapIndexed { index, value ->
        value to (entries.getOrNull(index) ?: value)
    }.toMap()
}

private fun SuwayomiSourcePreferenceDto.summaryForCurrentValue(): String? {
    val displayValue = when (type) {
        "ListPreference" -> entriesByValue()[currentString ?: defaultString.orEmpty()]
        "MultiSelectListPreference" -> {
            val entries = entriesByValue()
            (currentStringList ?: defaultStringList.orEmpty())
                .mapNotNull { entries[it] }
                .joinToString()
                .ifBlank { null }
        }
        "EditTextPreference" -> currentString ?: defaultString
        else -> null
    }
    val summaryText = summary
    return when {
        summaryText.isNullOrBlank() -> displayValue
        displayValue != null && summaryText.contains("%s") -> {
            runCatching { summaryText.format(displayValue) }.getOrDefault(summaryText)
        }
        else -> summaryText
    }
}
