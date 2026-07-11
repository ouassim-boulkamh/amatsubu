package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.data.ServerCreateBackupScreen
import eu.kanade.presentation.more.settings.screen.data.ServerRestoreBackupScreen
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.data.backup.restore.ServerBackupRestoreJob
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_PORT_MAX
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_PORT_MIN
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_TIMEOUT_MAX_SECONDS
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_TIMEOUT_MIN_SECONDS
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_BASIC
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_NONE
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_TOKEN
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiServerAboutDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiServerSettingsDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiWebUiAboutDto
import eu.kanade.tachiyomi.data.suwayomi.isValidSuwayomiServerPort
import eu.kanade.tachiyomi.data.suwayomi.serverSettingsAffectedEntities
import eu.kanade.tachiyomi.data.suwayomi.successMessage
import eu.kanade.tachiyomi.data.suwayomi.userMessage
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.DateFormat
import java.util.Date

object SettingsServerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_server

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val provider = remember(context) { context.appDependencies.suwayomiClientProvider }
        val preferences = provider.preferences
        val client = provider.graphQlClient

        val serverUrl by preferences.serverUrl.collectAsState()
        val useServerPort by preferences.useServerPort.collectAsState()
        val serverPort by preferences.serverPort.collectAsState()
        val authType by preferences.authType.collectAsState()
        val username by preferences.username.collectAsState()
        val password by preferences.password.collectAsState()
        val timeoutSeconds by preferences.timeoutSeconds.collectAsState()
        val testSuccessTitle = stringResource(MR.strings.pref_server_test_connection_success)
        val testFailureTitle = stringResource(MR.strings.pref_server_test_connection_failed)
        val logoutSuccessTitle = stringResource(MR.strings.logout_success)
        val settingsFailureTitle = stringResource(MR.strings.internal_error)

        val connectionKey = RemoteSettingsConnectionKey(
            serverUrl = serverUrl,
            useServerPort = useServerPort,
            serverPort = serverPort,
            authType = authType,
            username = username,
            password = password,
        )
        var reloadKey by remember { mutableIntStateOf(0) }
        var remoteSettings by remember { mutableStateOf<SuwayomiServerSettingsDto?>(null) }
        var remoteSettingsConnectionKey by remember { mutableStateOf<RemoteSettingsConnectionKey?>(null) }
        var remoteSettingsError by remember { mutableStateOf<String?>(null) }
        var remoteSettingsLoading by remember { mutableStateOf(false) }
        var serverAbout by remember { mutableStateOf<SuwayomiServerAboutDto?>(null) }
        var webUiAbout by remember { mutableStateOf<SuwayomiWebUiAboutDto?>(null) }
        var aboutConnectionKey by remember { mutableStateOf<RemoteSettingsConnectionKey?>(null) }
        var aboutError by remember { mutableStateOf<String?>(null) }
        var aboutLoading by remember { mutableStateOf(false) }
        var remoteMutationInFlight by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<TestConnectionDialog?>(null) }
        val chooseServerBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, context.stringResource(MR.strings.file_select_backup))
                }
            },
        ) {
            if (it == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }

            navigator.push(ServerRestoreBackupScreen(it.toString()))
        }

        LaunchedEffect(Unit) {
            ServerStateSync.invalidations.collectLatest { invalidation ->
                if (invalidation.affectsAny(ServerStateEntity.ServerSettings)) {
                    reloadKey++
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reloadKey++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(connectionKey, reloadKey) {
            remoteSettings = null
            remoteSettingsConnectionKey = null
            remoteSettingsError = null
            serverAbout = null
            webUiAbout = null
            aboutConnectionKey = null
            aboutError = null
            remoteSettingsLoading = true
            aboutLoading = true
            runCatching { withIOContext { client.serverSettings() } }
                .onSuccess {
                    remoteSettings = it
                    remoteSettingsConnectionKey = connectionKey
                    remoteSettingsError = null
                }
                .onFailure {
                    remoteSettings = null
                    remoteSettingsConnectionKey = null
                    remoteSettingsError = it.userMessage()
                }
            remoteSettingsLoading = false
            runCatching {
                withIOContext {
                    client.serverAbout() to client.webUiAbout()
                }
            }.onSuccess { (server, webUi) ->
                serverAbout = server
                webUiAbout = webUi
                aboutConnectionKey = connectionKey
                aboutError = null
            }.onFailure {
                serverAbout = null
                webUiAbout = null
                aboutConnectionKey = null
                aboutError = it.userMessage()
            }
            aboutLoading = false
        }

        dialog?.let { current ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(current.title) },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
            )
        }

        suspend fun updateServerSettings(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Boolean {
            if (remoteMutationInFlight) return false
            remoteMutationInFlight = true
            return runCatching {
                val updatedSettings = withIOContext { client.setSettings(buildJsonObject(block)) }
                remoteSettings = updatedSettings
                remoteSettingsConnectionKey = connectionKey
                ServerStateSync.requestRefresh(*serverSettingsAffectedEntities().toTypedArray())
                true
            }.onFailure {
                dialog = TestConnectionDialog(
                    title = settingsFailureTitle,
                    message = it.userMessage(),
                )
            }.getOrDefault(false)
                .also { remoteMutationInFlight = false }
        }

        return buildList {
            add(
                Preference.PreferenceGroup(
                    title = stringResource(MR.strings.pref_server_client_section),
                    preferenceItems = listOf(
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.serverUrl,
                            title = stringResource(MR.strings.pref_server_url),
                            subtitle = "%s",
                        ),
                        Preference.PreferenceItem.SwitchPreference(
                            preference = preferences.useServerPort,
                            title = stringResource(MR.strings.pref_server_port_enabled),
                        ),
                        Preference.PreferenceItem.EditTextPreference(
                            preference = preferences.serverPort,
                            title = stringResource(MR.strings.pref_server_port),
                            subtitle = "%s",
                            enabled = useServerPort,
                            onValueChanged = { isValidSuwayomiServerPort(it) },
                        ),
                        Preference.PreferenceItem.SliderPreference(
                            value = timeoutSeconds,
                            valueRange = SUWAYOMI_TIMEOUT_MIN_SECONDS..SUWAYOMI_TIMEOUT_MAX_SECONDS,
                            title = stringResource(MR.strings.pref_server_timeout),
                            valueString = "${timeoutSeconds}s",
                            onValueChanged = { preferences.timeoutSeconds.set(it) },
                        ),
                        Preference.PreferenceItem.TextPreference(
                            title = stringResource(MR.strings.pref_server_test_connection),
                            subtitle = "${preferences.baseUrl()}/api/graphql",
                            onClick = {
                                scope.launch {
                                    dialog = runCatching {
                                        withIOContext { client.testConnection() }
                                    }.fold(
                                        onSuccess = {
                                            TestConnectionDialog(
                                                title = testSuccessTitle,
                                                message = it.successMessage(),
                                            )
                                        },
                                        onFailure = {
                                            TestConnectionDialog(
                                                title = testFailureTitle,
                                                message = it.userMessage(),
                                            )
                                        },
                                    )
                                }
                            },
                        ),
                    ),
                ),
            )
            add(
                Preference.PreferenceGroup(
                    title = stringResource(MR.strings.pref_server_authentication_section),
                    preferenceItems = buildList {
                        add(
                            Preference.PreferenceItem.ListPreference(
                                preference = preferences.authType,
                                entries = mapOf(
                                    AUTH_NONE to stringResource(MR.strings.pref_server_auth_none),
                                    AUTH_BASIC to stringResource(MR.strings.pref_server_auth_basic),
                                    AUTH_TOKEN to "Token",
                                ),
                                title = stringResource(MR.strings.pref_server_auth_type),
                            ),
                        )
                        if (authType != AUTH_NONE) {
                            add(
                                Preference.PreferenceItem.EditTextPreference(
                                    preference = preferences.username,
                                    title = stringResource(MR.strings.username),
                                    subtitle = username.ifBlank { stringResource(MR.strings.none) },
                                ),
                            )
                            add(
                                Preference.PreferenceItem.EditTextPreference(
                                    preference = preferences.password,
                                    title = stringResource(MR.strings.password),
                                    subtitle = if (password.isBlank()) {
                                        stringResource(MR.strings.none)
                                    } else {
                                        stringResource(MR.strings.pref_server_password_set)
                                    },
                                ),
                            )
                            if (authType == AUTH_TOKEN) {
                                add(
                                    Preference.PreferenceItem.TextPreference(
                                        title = stringResource(MR.strings.pref_server_test_connection),
                                        subtitle = "Sign in and verify the token connection",
                                        onClick = {
                                            scope.launch {
                                                dialog = runCatching {
                                                    withIOContext {
                                                        provider.tokenAuth.login(username, password)
                                                        preferences.clearTokenLoginPassword()
                                                        client.testConnection()
                                                    }
                                                }.fold(
                                                    onSuccess = {
                                                        TestConnectionDialog(testSuccessTitle, it.successMessage())
                                                    },
                                                    onFailure = {
                                                        TestConnectionDialog(testFailureTitle, it.userMessage())
                                                    },
                                                )
                                            }
                                        },
                                    ),
                                )
                                add(
                                    Preference.PreferenceItem.TextPreference(
                                        title = stringResource(MR.strings.logout),
                                        subtitle = "Remove this server's saved token",
                                        onClick = {
                                            provider.tokenAuth.logout()
                                            preferences.clearTokenLoginPassword()
                                            dialog = TestConnectionDialog(
                                                title = logoutSuccessTitle,
                                                message = "The saved token was removed.",
                                            )
                                        },
                                    ),
                                )
                            }
                        }
                    },
                ),
            )
            add(
                Preference.PreferenceGroup(
                    title = stringResource(MR.strings.pref_server_server_section),
                    preferenceItems = buildList {
                        add(
                            Preference.PreferenceItem.TextPreference(
                                title = stringResource(MR.strings.pref_server_web_ui),
                                subtitle = preferences.baseUrl(),
                                onClick = { uriHandler.openUri(preferences.baseUrl()) },
                            ),
                        )
                        remoteSettingsError?.let {
                            add(Preference.PreferenceItem.InfoPreference(it))
                        }
                        if (remoteSettingsLoading) {
                            add(Preference.PreferenceItem.InfoPreference("Loading server settings..."))
                        } else if (remoteSettings != null && remoteSettingsConnectionKey != connectionKey) {
                            add(Preference.PreferenceItem.InfoPreference("Server settings changed; reloading..."))
                        }
                    },
                ),
            )
            add(
                Preference.PreferenceGroup(
                    title = "Manual server backups",
                    preferenceItems = listOf(
                        Preference.PreferenceItem.TextPreference(
                            title = "Create server backup",
                            subtitle = "Export Suwayomi server library data to a backup file.",
                            onClick = { navigator.push(ServerCreateBackupScreen()) },
                        ),
                        Preference.PreferenceItem.TextPreference(
                            title = "Restore server backup",
                            subtitle = "Import a backup into the configured Suwayomi server.",
                            onClick = {
                                if (!ServerBackupRestoreJob.isRunning(context)) {
                                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                        context.toast(MR.strings.restore_miui_warning)
                                    }

                                    chooseServerBackup.launch("*/*")
                                } else {
                                    context.toast(MR.strings.restore_in_progress)
                                }
                            },
                        ),
                        Preference.PreferenceItem.InfoPreference(
                            "Server backups restore Suwayomi-owned library, chapter, category, " +
                                "history, and tracking data. " +
                                "They do not restore Android client settings.",
                        ),
                    ),
                ),
            )
            addAboutGroup(
                server = serverAbout.takeIf { aboutConnectionKey == connectionKey },
                webUi = webUiAbout.takeIf { aboutConnectionKey == connectionKey },
                loading = aboutLoading,
                error = aboutError,
            )

            remoteSettings
                ?.takeIf { remoteSettingsConnectionKey == connectionKey }
                ?.let { settings ->
                    addServerBindingGroup(settings, ::updateServerSettings)
                    addServerAuthenticationGroup(settings, ::updateServerSettings)
                    addLibraryUpdatesGroup(settings, ::updateServerSettings)
                    addDownloadBehaviorGroup(settings, ::updateServerSettings)
                    addBackupGroup(settings, ::updateServerSettings)
                    addSocksProxyGroup(settings, ::updateServerSettings)
                    addFlareSolverrGroup(settings, ::updateServerSettings)
                    addWebUiGroup(settings, ::updateServerSettings)
                    addMiscGroup(settings, ::updateServerSettings)
                }
        }
    }

    private fun MutableList<Preference>.addAboutGroup(
        server: SuwayomiServerAboutDto?,
        webUi: SuwayomiWebUiAboutDto?,
        loading: Boolean,
        error: String?,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "About",
                preferenceItems = buildList {
                    if (loading) {
                        add(Preference.PreferenceItem.InfoPreference("Loading server about info..."))
                    }
                    error?.let {
                        add(Preference.PreferenceItem.InfoPreference("Could not load server about info: $it"))
                    }
                    server?.let {
                        add(Preference.PreferenceItem.TextPreference("Server", it.name))
                        add(Preference.PreferenceItem.TextPreference("Server version", it.version))
                        if (it.buildType.isNotBlank()) {
                            add(Preference.PreferenceItem.TextPreference("Server build type", it.buildType))
                        }
                        add(
                            Preference.PreferenceItem.TextPreference(
                                "Server build time",
                                formatTimestamp(it.buildTime),
                            ),
                        )
                    }
                    webUi?.let {
                        add(Preference.PreferenceItem.TextPreference("WebUI version", it.tag))
                        add(Preference.PreferenceItem.TextPreference("WebUI channel", it.channel))
                        add(
                            Preference.PreferenceItem.TextPreference(
                                "WebUI updated",
                                formatTimestamp(it.updateTimestamp),
                            ),
                        )
                    }
                },
            ),
        )
    }

    private fun MutableList<Preference>.addServerBindingGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Server bindings",
                preferenceItems = listOf(
                    textFieldPreference(
                        title = "IP",
                        value = settings.ip.orEmpty(),
                        onConfirm = {
                            update { put("ip", it) }
                            true
                        },
                    ),
                    textFieldPreference(
                        title = "Server port",
                        value = settings.port.toString(),
                        onConfirm = {
                            val port = it.toIntOrNull() ?: return@textFieldPreference false
                            if (port !in 0..65535) return@textFieldPreference false
                            update { put("port", port) }
                            true
                        },
                    ),
                ),
            ),
        )
    }

    private fun MutableList<Preference>.addServerAuthenticationGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Server authentication",
                preferenceItems = buildList {
                    add(
                        listPreference(
                            title = "Authentication mode",
                            value = settings.authMode,
                            entries = mapOf(
                                "NONE" to "None",
                                "BASIC_AUTH" to "Basic auth",
                                "SIMPLE_LOGIN" to "Simple login",
                                "UI_LOGIN" to "UI login",
                            ),
                            onChanged = { update { put("authMode", it) } },
                        ),
                    )
                    if (settings.authMode != "NONE") {
                        add(
                            textFieldPreference("Server username", settings.authUsername) {
                                update { put("authUsername", it) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("Server password", settings.authPassword) {
                                update { put("authPassword", it) }
                                true
                            },
                        )
                    }
                },
            ),
        )
    }

    private fun MutableList<Preference>.addLibraryUpdatesGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Library updates",
                preferenceItems = listOf(
                    switchPreference(
                        title = "Update manga metadata",
                        checked = settings.updateMangas,
                        onChanged = { update { put("updateMangas", it) } },
                    ),
                    textFieldPreference(
                        title = "Global update interval",
                        value = settings.globalUpdateInterval.toString(),
                        onConfirm = {
                            val interval = it.toDoubleOrNull() ?: return@textFieldPreference false
                            if (interval != 0.0 && interval < 6.0) return@textFieldPreference false
                            update { put("globalUpdateInterval", interval) }
                            true
                        },
                    ),
                    switchPreference(
                        title = "Exclude unread chapters",
                        checked = settings.excludeUnreadChapters,
                        onChanged = { update { put("excludeUnreadChapters", it) } },
                    ),
                    switchPreference(
                        title = "Exclude not started",
                        checked = settings.excludeNotStarted,
                        onChanged = { update { put("excludeNotStarted", it) } },
                    ),
                    switchPreference(
                        title = "Exclude completed",
                        checked = settings.excludeCompleted,
                        onChanged = { update { put("excludeCompleted", it) } },
                    ),
                    textFieldPreference(
                        title = "Max sources in parallel",
                        value = settings.maxSourcesInParallel.toString(),
                        onConfirm = {
                            val count = it.toIntOrNull() ?: return@textFieldPreference false
                            if (count !in 1..20) return@textFieldPreference false
                            update { put("maxSourcesInParallel", count) }
                            true
                        },
                    ),
                ),
            ),
        )
    }

    private fun MutableList<Preference>.addDownloadBehaviorGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Download behavior",
                preferenceItems = listOf(
                    textFieldPreference("Downloads path", settings.downloadsPath) {
                        update { put("downloadsPath", it) }
                        true
                    },
                    switchPreference(
                        title = "Download as CBZ",
                        checked = settings.downloadAsCbz,
                        onChanged = { update { put("downloadAsCbz", it) } },
                    ),
                    switchPreference(
                        title = "Auto-download new chapters",
                        checked = settings.autoDownloadNewChapters,
                        onChanged = { update { put("autoDownloadNewChapters", it) } },
                    ),
                    switchPreference(
                        title = "Exclude entries with unread chapters",
                        checked = settings.excludeEntryWithUnreadChapters,
                        onChanged = { update { put("excludeEntryWithUnreadChapters", it) } },
                    ),
                    textFieldPreference(
                        title = "Auto-download limit",
                        value = settings.autoDownloadNewChaptersLimit.toString(),
                        onConfirm = {
                            val limit = it.toIntOrNull() ?: return@textFieldPreference false
                            if (limit < 0) return@textFieldPreference false
                            update { put("autoDownloadNewChaptersLimit", limit) }
                            true
                        },
                    ),
                    switchPreference(
                        title = "Ignore chapter reuploads",
                        checked = settings.autoDownloadIgnoreReUploads,
                        onChanged = { update { put("autoDownloadIgnoreReUploads", it) } },
                    ),
                ),
            ),
        )
    }

    private fun MutableList<Preference>.addBackupGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Automatic backups",
                preferenceItems = listOf(
                    textFieldPreference("Backup path", settings.backupPath) {
                        update { put("backupPath", it) }
                        true
                    },
                    textFieldPreference("Backup time", settings.backupTime) {
                        if (!it.matches(TIME_PATTERN)) return@textFieldPreference false
                        update { put("backupTime", it) }
                        true
                    },
                    textFieldPreference(
                        title = "Backup interval",
                        value = settings.backupInterval.toString(),
                        onConfirm = {
                            val interval = it.toIntOrNull() ?: return@textFieldPreference false
                            if (interval < 0) return@textFieldPreference false
                            update { put("backupInterval", interval) }
                            true
                        },
                    ),
                    textFieldPreference(
                        title = "Backup TTL",
                        value = settings.backupTTL.toString(),
                        onConfirm = {
                            val ttl = it.toIntOrNull() ?: return@textFieldPreference false
                            if (ttl < 0) return@textFieldPreference false
                            update { put("backupTTL", ttl) }
                            true
                        },
                    ),
                    switchPreference(
                        title = "Include library entries",
                        checked = settings.autoBackupIncludeManga,
                        onChanged = { update { put("autoBackupIncludeManga", it) } },
                    ),
                    switchPreference(
                        title = "Include categories",
                        checked = settings.autoBackupIncludeCategories,
                        onChanged = { update { put("autoBackupIncludeCategories", it) } },
                    ),
                    switchPreference(
                        title = "Include chapters",
                        checked = settings.autoBackupIncludeChapters,
                        onChanged = { update { put("autoBackupIncludeChapters", it) } },
                    ),
                    switchPreference(
                        title = "Include tracking",
                        checked = settings.autoBackupIncludeTracking,
                        onChanged = { update { put("autoBackupIncludeTracking", it) } },
                    ),
                    switchPreference(
                        title = "Include history",
                        checked = settings.autoBackupIncludeHistory,
                        onChanged = { update { put("autoBackupIncludeHistory", it) } },
                    ),
                    switchPreference(
                        title = "Include client data",
                        checked = settings.autoBackupIncludeClientData,
                        onChanged = { update { put("autoBackupIncludeClientData", it) } },
                    ),
                    switchPreference(
                        title = "Include server settings",
                        checked = settings.autoBackupIncludeServerSettings,
                        onChanged = { update { put("autoBackupIncludeServerSettings", it) } },
                    ),
                ),
            ),
        )
    }

    private fun MutableList<Preference>.addSocksProxyGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "SOCKS proxy",
                preferenceItems = buildList {
                    add(
                        switchPreference(
                            title = "Enable SOCKS proxy",
                            checked = settings.socksProxyEnabled,
                            onChanged = { update { put("socksProxyEnabled", it) } },
                        ),
                    )
                    if (settings.socksProxyEnabled) {
                        add(
                            textFieldPreference("SOCKS version", settings.socksProxyVersion.toString()) {
                                val version = it.toIntOrNull() ?: return@textFieldPreference false
                                if (version !in 4..5) return@textFieldPreference false
                                update { put("socksProxyVersion", version) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("SOCKS host", settings.socksProxyHost) {
                                update { put("socksProxyHost", it) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("SOCKS port", settings.socksProxyPort) {
                                if (it.isBlank()) {
                                    update { put("socksProxyPort", "") }
                                    return@textFieldPreference true
                                }
                                val port = it.toIntOrNull() ?: return@textFieldPreference false
                                if (port !in SUWAYOMI_PORT_MIN..SUWAYOMI_PORT_MAX) return@textFieldPreference false
                                update { put("socksProxyPort", it) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("SOCKS username", settings.socksProxyUsername) {
                                update { put("socksProxyUsername", it) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("SOCKS password", settings.socksProxyPassword) {
                                update { put("socksProxyPassword", it) }
                                true
                            },
                        )
                    }
                },
            ),
        )
    }

    private fun MutableList<Preference>.addFlareSolverrGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "FlareSolverr",
                preferenceItems = buildList {
                    add(
                        switchPreference(
                            title = "Enable FlareSolverr",
                            checked = settings.flareSolverrEnabled,
                            onChanged = { update { put("flareSolverrEnabled", it) } },
                        ),
                    )
                    if (settings.flareSolverrEnabled) {
                        add(
                            textFieldPreference("FlareSolverr server URL", settings.flareSolverrUrl) {
                                update { put("flareSolverrUrl", it) }
                                true
                            },
                        )
                        add(
                            switchPreference(
                                title = "Use FlareSolverr as response fallback",
                                checked = settings.flareSolverrAsResponseFallback,
                                onChanged = { update { put("flareSolverrAsResponseFallback", it) } },
                            ),
                        )
                        add(
                            textFieldPreference("Request timeout", settings.flareSolverrTimeout.toString()) {
                                val timeout = it.toIntOrNull() ?: return@textFieldPreference false
                                if (timeout < 0) return@textFieldPreference false
                                update { put("flareSolverrTimeout", timeout) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("Session name", settings.flareSolverrSessionName) {
                                update { put("flareSolverrSessionName", it) }
                                true
                            },
                        )
                        add(
                            textFieldPreference("Session TTL", settings.flareSolverrSessionTtl.toString()) {
                                val ttl = it.toIntOrNull() ?: return@textFieldPreference false
                                if (ttl < 0) return@textFieldPreference false
                                update { put("flareSolverrSessionTtl", ttl) }
                                true
                            },
                        )
                    }
                },
            ),
        )
    }

    private fun MutableList<Preference>.addWebUiGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Web UI",
                preferenceItems = listOf(
                    textFieldPreference("WebUI channel", settings.webUIChannel) {
                        val value = it.trim().uppercase()
                        if (value !in setOf("BUNDLED", "STABLE", "PREVIEW")) return@textFieldPreference false
                        update { put("webUIChannel", value) }
                        true
                    },
                    textFieldPreference("WebUI flavor", settings.webUIFlavor) {
                        val value = it.trim().uppercase()
                        if (value !in setOf("WEBUI", "VUI", "CUSTOM")) return@textFieldPreference false
                        update { put("webUIFlavor", value) }
                        true
                    },
                    textFieldPreference("WebUI interface", settings.webUIInterface) {
                        val value = it.trim().uppercase()
                        if (value !in setOf("BROWSER", "ELECTRON")) return@textFieldPreference false
                        update { put("webUIInterface", value) }
                        true
                    },
                    textFieldPreference(
                        title = "WebUI update check interval",
                        value = settings.webUIUpdateCheckInterval.toString(),
                        onConfirm = {
                            val interval = it.toDoubleOrNull() ?: return@textFieldPreference false
                            if (interval < 0.0 || interval > 23.0) return@textFieldPreference false
                            update { put("webUIUpdateCheckInterval", interval) }
                            true
                        },
                    ),
                ),
            ),
        )
    }

    private fun MutableList<Preference>.addMiscGroup(
        settings: SuwayomiServerSettingsDto,
        update: suspend (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Boolean,
    ) {
        add(
            Preference.PreferenceGroup(
                title = "Misc",
                preferenceItems = listOf(
                    switchPreference(
                        title = "Debug logs",
                        checked = settings.debugLogsEnabled,
                        onChanged = { update { put("debugLogsEnabled", it) } },
                    ),
                    switchPreference(
                        title = "System tray icon",
                        checked = settings.systemTrayEnabled,
                        onChanged = { update { put("systemTrayEnabled", it) } },
                    ),
                    switchPreference(
                        title = "Open initial requests in browser",
                        checked = settings.initialOpenInBrowserEnabled,
                        onChanged = { update { put("initialOpenInBrowserEnabled", it) } },
                    ),
                    textFieldPreference("Local source path", settings.localSourcePath) {
                        update { put("localSourcePath", it) }
                        true
                    },
                    textFieldPreference("Electron path", settings.electronPath) {
                        update { put("electronPath", it) }
                        true
                    },
                    textFieldPreference("Max log file size", settings.maxLogFileSize) {
                        if (!it.matches(LOG_SIZE_PATTERN)) return@textFieldPreference false
                        update { put("maxLogFileSize", it) }
                        true
                    },
                    textFieldPreference("Max log folder size", settings.maxLogFolderSize) {
                        if (!it.matches(LOG_SIZE_PATTERN)) return@textFieldPreference false
                        update { put("maxLogFolderSize", it) }
                        true
                    },
                    textFieldPreference(
                        title = "Max log files",
                        value = settings.maxLogFiles.toString(),
                        onConfirm = {
                            val count = it.toIntOrNull() ?: return@textFieldPreference false
                            if (count < 0) return@textFieldPreference false
                            update { put("maxLogFiles", count) }
                            true
                        },
                    ),
                ),
            ),
        )
    }
}

private fun textFieldPreference(
    title: String,
    value: String,
    onConfirm: suspend (String) -> Boolean,
): Preference.PreferenceItem.CustomPreference {
    return Preference.PreferenceItem.CustomPreference(title) {
        EditTextPreferenceWidget(
            title = title,
            subtitle = "%s",
            icon = null,
            value = value,
            onConfirm = onConfirm,
        )
    }
}

private fun switchPreference(
    title: String,
    checked: Boolean,
    onChanged: suspend (Boolean) -> Unit,
): Preference.PreferenceItem.CustomPreference {
    return Preference.PreferenceItem.CustomPreference(title) {
        val scope = rememberCoroutineScope()
        SwitchPreferenceWidget(
            title = title,
            checked = checked,
            onCheckedChanged = { value ->
                scope.launch { onChanged(value) }
            },
        )
    }
}

private fun <T> listPreference(
    title: String,
    value: T,
    entries: Map<T, String>,
    onChanged: suspend (T) -> Unit,
): Preference.PreferenceItem.CustomPreference {
    return Preference.PreferenceItem.CustomPreference(title) {
        val scope = rememberCoroutineScope()
        ListPreferenceWidget(
            title = title,
            subtitle = entries[value],
            icon = null,
            value = value,
            entries = entries,
            onValueChange = { selected ->
                scope.launch { onChanged(selected) }
            },
        )
    }
}

private data class TestConnectionDialog(
    val title: String,
    val message: String,
)

private data class RemoteSettingsConnectionKey(
    val serverUrl: String,
    val useServerPort: Boolean,
    val serverPort: String,
    val authType: String,
    val username: String,
    val password: String,
)

private fun formatTimestamp(timestamp: String): String {
    val value = timestamp.toLongOrNull() ?: return timestamp.ifBlank { "Unknown" }
    if (value <= 0L) return "Unknown"
    val milliseconds = if (value < 100_000_000_000L) value * 1000 else value
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(milliseconds))
}

private val TIME_PATTERN = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex()
private val LOG_SIZE_PATTERN = "^[0-9]+(|kb|KB|mb|MB|gb|GB)$".toRegex()
