package eu.kanade.presentation.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.migration.model.MigrationFlag
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.suwayomi.ServerMigrateMangaUseCase
import eu.kanade.tachiyomi.di.appDependencies
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val context = LocalContext.current
    val dependencies = context.appDependencies
    val scope = rememberCoroutineScope()

    val screenModel = rememberScreenModel {
        MigrateDialogScreenModel(
            sourcePreference = dependencies.sourcePreferences,
            serverMigrateManga = ServerMigrateMangaUseCase(
                sourcePreferences = dependencies.sourcePreferences,
                provider = dependencies.suwayomiClientProvider,
            ),
        )
    }
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { screenModel.toggleSelection(flag) },
                    )
                }
                state.migrationError?.let { error ->
                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                    Text(
                        text = with(context) { error.formattedMessage },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launchIO {
                            if (screenModel.migrateManga(replace = false)) {
                                withUIContext { onComplete() }
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        scope.launchIO {
                            if (screenModel.migrateManga(replace = true)) {
                                withUIContext { onComplete() }
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

private class MigrateDialogScreenModel(
    private val sourcePreference: SourcePreferences,
    private val serverMigrateManga: ServerMigrateMangaUseCase,
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    suspend fun init(current: Manga, target: Manga) {
        val isServerMigration = serverMigrateManga.isServerManga(current.id) &&
            serverMigrateManga.isServerManga(target.id)
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> isServerMigration
                    MigrationFlag.CATEGORY -> isServerMigration
                    MigrationFlag.NOTES -> isServerMigration && current.notes.isNotBlank()
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags.get()
            .filterTo(mutableSetOf()) { it in applicableFlags }
        mutableState.update {
            State(
                current = current,
                target = target,
                isServerMigration = isServerMigration,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags, migrationError = null)
        }
    }

    suspend fun migrateManga(replace: Boolean): Boolean {
        val state = state.value
        val current = state.current ?: return false
        val target = state.target ?: return false
        mutableState.update { it.copy(isMigrating = true, migrationError = null) }
        try {
            sourcePreference.migrationFlags.set(state.selectedFlags)
            if (state.isServerMigration) {
                serverMigrateManga(current, target, replace)
            }
            mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to migrate Suwayomi manga" }
            mutableState.update {
                it.copy(
                    isMigrating = false,
                    isMigrated = false,
                    migrationError = e,
                )
            }
            return false
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val isServerMigration: Boolean = false,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
        val migrationError: Throwable? = null,
    )
}
