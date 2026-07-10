package eu.kanade.presentation.more.settings.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.export.LibraryExporter
import eu.kanade.tachiyomi.data.export.LibraryExporter.ExportOptions
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.download.ClientDeviceCopiesScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

object SettingsDataScreen : SearchableSettings {

    val restorePreferenceKeyString = MR.strings.label_data_storage
    const val HELP_URL = "https://github.com/Suwayomi/Suwayomi-Server/wiki"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri(HELP_URL) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val storagePreferences = remember(context) { context.appDependencies.storagePreferences }
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            getStorageLocationPref(storagePreferences = storagePreferences),
            Preference.PreferenceItem.InfoPreference(
                "Used for local source files and any remaining client-local downloads. " +
                    "Suwayomi server manga/download storage is configured in Server settings.",
            ),

            getBackupAndRestoreGroup(),
            getDeviceCopiesGroup(
                onOpenDeviceCopies = { navigator.push(ClientDeviceCopiesScreen) },
            ),
            getDataGroup(),
            getExportGroup(),
        )
    }

    @Composable
    fun storageLocationPicker(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): ManagedActivityResultLauncher<Uri?, Uri?> {
        val context = LocalContext.current

        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                // For some reason InkBook devices do not implement the SAF properly. Persistable URI grants do not
                // work. However, simply retrieving the URI and using it works fine for these devices. Access is not
                // revoked after the app is closed or the device is restarted.
                // This also holds for some Samsung devices. Thus, we simply execute inside of a try-catch block and
                // ignore the exception if it is thrown.
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    logcat(LogPriority.ERROR, e)
                    context.toast(MR.strings.file_picker_uri_permission_unsupported)
                }

                UniFile.fromUri(context, uri)?.let {
                    storageDirPref.set(it.uri.toString())
                }
            }
        }
    }

    @Composable
    fun storageLocationText(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): String {
        val context = LocalContext.current
        val storageDir by storageDirPref.collectAsState()

        if (storageDir == storageDirPref.defaultValue()) {
            return stringResource(MR.strings.no_location_set)
        }

        return remember(storageDir) {
            val file = UniFile.fromUri(context, storageDir.toUri())
            file?.displayablePath
        } ?: stringResource(MR.strings.invalid_location, storageDir)
    }

    @Composable
    private fun getStorageLocationPref(
        storagePreferences: StoragePreferences,
    ): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val pickStorageLocation = storageLocationPicker(storagePreferences.baseStorageDirectory)

        return Preference.PreferenceItem.TextPreference(
            title = "Client storage location",
            subtitle = storageLocationText(storagePreferences.baseStorageDirectory),
            onClick = {
                try {
                    pickStorageLocation.launch(null)
                } catch (e: ActivityNotFoundException) {
                    context.toast(MR.strings.file_picker_error)
                }
            },
        )
    }

    @Composable
    private fun getBackupAndRestoreGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        var showCreateOptionsDialog by remember { mutableStateOf(false) }
        var showRestoreOptionsDialog by remember { mutableStateOf(false) }
        var pendingCreateOptions by remember { mutableStateOf(BackupOptions()) }
        var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
        val chooseBackupFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                BackupCreateJob.startNow(context, uri, pendingCreateOptions)
            }
        }
        val chooseRestoreFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                pendingRestoreUri = uri
                showRestoreOptionsDialog = true
            }
        }

        if (showCreateOptionsDialog) {
            ClientCreateBackupOptionsDialog(
                onDismissRequest = { showCreateOptionsDialog = false },
                onConfirm = { options ->
                    pendingCreateOptions = options.withValidPrivateSelection()
                    showCreateOptionsDialog = false
                    try {
                        chooseBackupFile.launch(BackupCreator.getFilename())
                    } catch (e: ActivityNotFoundException) {
                        context.toast(MR.strings.file_picker_error)
                    }
                },
            )
        }

        if (showRestoreOptionsDialog) {
            ClientRestoreBackupOptionsDialog(
                onDismissRequest = { showRestoreOptionsDialog = false },
                onConfirm = { options ->
                    val uri = pendingRestoreUri ?: return@ClientRestoreBackupOptionsDialog
                    showRestoreOptionsDialog = false
                    BackupRestoreJob.start(context, uri, options.withValidPrivateSelection())
                },
            )
        }

        return Preference.PreferenceGroup(
            title = "Client backups",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Create client backup",
                    subtitle = "Save Android client backup data to a local .tachibk file.",
                    onClick = {
                        if (!BackupCreateJob.isManualJobRunning(context)) {
                            showCreateOptionsDialog = true
                        } else {
                            context.toast(MR.strings.backup_in_progress)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Restore client backup",
                    subtitle = "Restore Android client settings from a .tachibk file.",
                    onClick = {
                        if (!BackupRestoreJob.isRunning(context)) {
                            try {
                                chooseRestoreFile.launch(arrayOf("application/*", "application/octet-stream"))
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.file_picker_error)
                            }
                        } else {
                            context.toast(MR.strings.restore_in_progress)
                        }
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    "Client backups include Android app, reader, display, source, privacy, storage, " +
                        "network, and Suwayomi connection settings. Library, chapters, history, " +
                        "downloads, and tracking records are not restored here.",
                ),
            ),
        )
    }

    @Composable
    private fun ClientCreateBackupOptionsDialog(
        onDismissRequest: () -> Unit,
        onConfirm: (BackupOptions) -> Unit,
    ) {
        var options by remember { mutableStateOf(BackupOptions()) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "Create client backup") },
            text = {
                Column {
                    BackupOptions.settingsOptions.forEach { option ->
                        LabeledCheckbox(
                            label = stringResource(option.label),
                            checked = option.getter(options),
                            onCheckedChange = { checked ->
                                options = option.setter(options, checked).withValidPrivateSelection()
                            },
                            enabled = option.enabled(options),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(options) },
                    enabled = options.canCreate(),
                ) {
                    Text(text = stringResource(MR.strings.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun ClientRestoreBackupOptionsDialog(
        onDismissRequest: () -> Unit,
        onConfirm: (RestoreOptions) -> Unit,
    ) {
        var options by remember { mutableStateOf(RestoreOptions(appSettings = true, sourceSettings = true)) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "Restore client backup") },
            text = {
                Column {
                    RestoreOptions.options.forEach { option ->
                        LabeledCheckbox(
                            label = stringResource(option.label),
                            checked = option.getter(options),
                            onCheckedChange = { checked ->
                                options = option.setter(options, checked).withValidPrivateSelection()
                            },
                            enabled = option.enabled(options),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(options) },
                    enabled = options.canRestore(),
                ) {
                    Text(text = stringResource(MR.strings.action_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    private fun getDeviceCopiesGroup(
        onOpenDeviceCopies: () -> Unit,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "Device copies",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Manage device copies",
                    subtitle = "Review orphaned chapter copies stored on this Android device.",
                    onClick = onOpenDeviceCopies,
                ),
                Preference.PreferenceItem.InfoPreference(
                    "Removing a device copy deletes only Amatsubu local files. Suwayomi server downloads stay unchanged.",
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dependencies = remember(context) { context.appDependencies }
        val libraryPreferences = dependencies.libraryPreferences
        val chapterCache = dependencies.chapterCache
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }

        return Preference.PreferenceGroup(
            title = "Client cache",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_chapter_cache),
                    subtitle = stringResource(MR.strings.used_cache, cacheReadableSize),
                    onClick = {
                        scope.launchNonCancellable {
                            try {
                                val deletedFiles = chapterCache.clear()
                                withUIContext {
                                    context.toast(context.stringResource(MR.strings.cache_deleted, deletedFiles))
                                    cacheReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext { context.toast(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoClearChapterCache,
                    title = stringResource(MR.strings.pref_auto_clear_chapter_cache),
                ),
            ),
        )
    }

    @Composable
    private fun getExportGroup(): Preference.PreferenceGroup {
        var showDialog by remember { mutableStateOf(false) }
        var exportOptions by remember {
            mutableStateOf(
                ExportOptions(
                    includeTitle = true,
                    includeAuthor = true,
                    includeArtist = true,
                ),
            )
        }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val suwayomiClient = remember(context) { context.appDependencies.suwayomiClientProvider.graphQlClient }
        var favorites by remember { mutableStateOf<List<Manga>>(emptyList()) }
        LaunchedEffect(Unit) {
            favorites = runCatching {
                withIOContext {
                    suwayomiClient.getLibraryMangas()
                        .map(SuwayomiMangaDto::toExportManga)
                }
            }.getOrDefault(emptyList())
        }

        val saveFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    LibraryExporter.exportToCsv(
                        context = context,
                        uri = it,
                        favorites = favorites,
                        options = exportOptions,
                        onExportComplete = {
                            scope.launch(Dispatchers.Main) {
                                context.toast(MR.strings.library_exported)
                            }
                        },
                    )
                }
            }
        }

        if (showDialog) {
            ColumnSelectionDialog(
                options = exportOptions,
                onConfirm = { options ->
                    exportOptions = options
                    saveFileLauncher.launch("amatsubu_library.csv")
                },
                onDismissRequest = { showDialog = false },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.export),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.library_list),
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun ColumnSelectionDialog(
        options: ExportOptions,
        onConfirm: (ExportOptions) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        var titleSelected by remember { mutableStateOf(options.includeTitle) }
        var authorSelected by remember { mutableStateOf(options.includeAuthor) }
        var artistSelected by remember { mutableStateOf(options.includeArtist) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = titleSelected,
                            onCheckedChange = { checked ->
                                titleSelected = checked
                                if (!checked) {
                                    authorSelected = false
                                    artistSelected = false
                                }
                            },
                        )
                        Text(text = stringResource(MR.strings.title))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = authorSelected,
                            onCheckedChange = { authorSelected = it },
                            enabled = titleSelected,
                        )
                        Text(text = stringResource(MR.strings.author))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = artistSelected,
                            onCheckedChange = { artistSelected = it },
                            enabled = titleSelected,
                        )
                        Text(text = stringResource(MR.strings.artist))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            ExportOptions(
                                includeTitle = titleSelected,
                                includeAuthor = authorSelected,
                                includeArtist = artistSelected,
                            ),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private fun SuwayomiMangaDto.toExportManga(): Manga {
    return Manga.create().copy(
        id = id.toLong(),
        source = sourceId.toLongOrNull() ?: 0L,
        favorite = inLibrary,
        dateAdded = inLibraryAt ?: 0L,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = normalizedGenre(),
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
    )
}

private fun BackupOptions.withValidPrivateSelection(): BackupOptions {
    return if (appSettings || sourceSettings) {
        this
    } else {
        copy(privateSettings = false)
    }
}

private fun RestoreOptions.withValidPrivateSelection(): RestoreOptions {
    return if (appSettings || sourceSettings) {
        this
    } else {
        copy(privateSettings = false)
    }
}
