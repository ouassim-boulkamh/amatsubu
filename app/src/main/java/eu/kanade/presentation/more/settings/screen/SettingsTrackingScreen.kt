package eu.kanade.presentation.more.settings.screen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTrackerDto
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.setting.track.SuwayomiTrackLoginActivity
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object SettingsTrackingScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri(TRACKING_HELP_URL) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        return listOf(
            Preference.PreferenceItem.CustomPreference(
                title = "Suwayomi trackers",
                content = { SuwayomiTrackingSettings() },
            ),
        )
    }

    @Composable
    private fun SuwayomiTrackingSettings() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val client = remember(context) { context.appDependencies.suwayomiClientProvider.graphQlClient }

        var trackers by remember { mutableStateOf<List<SuwayomiTrackerDto>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var credentialLogin by remember { mutableStateOf<SuwayomiTrackerDto?>(null) }

        fun reload() {
            scope.launchIO {
                loading = true
                error = null
                try {
                    trackers = client.trackerList()
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Failed to load Suwayomi trackers" }
                    error = e.message ?: "Failed to load Suwayomi trackers"
                } finally {
                    loading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            reload()
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reload()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        credentialLogin?.let { tracker ->
            CredentialsLoginDialog(
                tracker = tracker,
                onDismissRequest = { credentialLogin = null },
                onConfirm = { username, password ->
                    scope.launchIO {
                        try {
                            client.loginTrackerCredentials(tracker.id, username, password)
                            context.toast(MR.strings.login_success)
                            credentialLogin = null
                            reload()
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            logcat(LogPriority.ERROR, e) { "Failed to login Suwayomi tracker ${tracker.id}" }
                            context.toast(e.message ?: "Login failed")
                        }
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PrefsHorizontalPadding, 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(text = "Loading Suwayomi trackers")
                }
            }

            error?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PrefsHorizontalPadding, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = ::reload) {
                        Text(text = stringResource(MR.strings.action_retry))
                    }
                }
            }

            trackers.forEach { tracker ->
                SuwayomiTrackerRow(
                    tracker = tracker,
                    onLogin = {
                        if (tracker.authUrl != null) {
                            SuwayomiTrackLoginActivity.setPendingTrackerId(context, tracker.id)
                            context.openInBrowser(tracker.authUrl.withSuwayomiCallbackState())
                        } else {
                            credentialLogin = tracker
                        }
                    },
                    onLogout = {
                        scope.launch {
                            try {
                                client.logoutTracker(tracker.id)
                                context.toast(MR.strings.logout_success)
                                reload()
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                logcat(LogPriority.ERROR, e) { "Failed to logout Suwayomi tracker ${tracker.id}" }
                                context.toast(e.message ?: "Logout failed")
                            }
                        }
                    },
                )
            }

            Preference.PreferenceItem.InfoPreference(
                "These accounts are stored on the Suwayomi server. Manga-level tracking is configured from each manga's tracking action.",
            ).let {
                eu.kanade.presentation.more.settings.PreferenceItem(item = it, highlightKey = null)
            }
        }
    }

    @Composable
    private fun SuwayomiTrackerRow(
        tracker: SuwayomiTrackerDto,
        onLogin: () -> Unit,
        onLogout: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .clickable { if (tracker.isLoggedIn) onLogout() else onLogin() }
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tracker.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (tracker.isLoggedIn) "Logged in" else "Not logged in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tracker.isLoggedIn) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(MR.strings.login_success),
                    tint = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onLogout) {
                    Text(text = stringResource(MR.strings.logout))
                }
            } else {
                Button(onClick = onLogin) {
                    Text(text = stringResource(MR.strings.login))
                }
            }
        }
    }

    @Composable
    private fun CredentialsLoginDialog(
        tracker: SuwayomiTrackerDto,
        onDismissRequest: () -> Unit,
        onConfirm: (String, String) -> Unit,
    ) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MR.strings.login_title, tracker.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = {
                            Text(
                                text = stringResource(
                                    if (tracker.name ==
                                        "Kitsu"
                                    ) {
                                        MR.strings.email
                                    } else {
                                        MR.strings.username
                                    },
                                ),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(MR.strings.password)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = username.isNotBlank() && password.isNotBlank(),
                    onClick = { onConfirm(username, password) },
                ) {
                    Text(text = stringResource(MR.strings.login))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    private const val TRACKING_HELP_URL = "https://github.com/Suwayomi/Suwayomi-Server/wiki"
    private const val CALLBACK_URI = "amatsubu://tracker-oauth"

    private fun String.withSuwayomiCallbackState(): String {
        val callbackState = buildJsonObject {
            put("redirectUrl", CALLBACK_URI)
            put("clientName", "Amatsubu")
        }.toString()

        return Uri.parse(this)
            .buildUpon()
            .appendQueryParameter("state", callbackState)
            .build()
            .toString()
    }
}
