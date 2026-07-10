package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_TIMEOUT_MAX_SECONDS
import eu.kanade.tachiyomi.data.suwayomi.SUWAYOMI_TIMEOUT_MIN_SECONDS
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_BASIC
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_NONE
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiPreferences.Companion.AUTH_TOKEN
import eu.kanade.tachiyomi.data.suwayomi.isValidSuwayomiConnectionSettings
import eu.kanade.tachiyomi.data.suwayomi.isValidSuwayomiServerPort
import eu.kanade.tachiyomi.data.suwayomi.isValidSuwayomiServerUrl
import eu.kanade.tachiyomi.data.suwayomi.successMessage
import eu.kanade.tachiyomi.data.suwayomi.userMessage
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha

internal class ServerStep(
    private val provider: SuwayomiClientProvider,
) : OnboardingStep {

    private val preferences = provider.preferences
    private val client = provider.graphQlClient

    private var _isComplete by mutableStateOf(false)
    private var testState by mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle)

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()

        val serverUrl by preferences.serverUrl.collectAsState()
        val useServerPort by preferences.useServerPort.collectAsState()
        val serverPort by preferences.serverPort.collectAsState()
        val authType by preferences.authType.collectAsState()
        val username by preferences.username.collectAsState()
        val password by preferences.password.collectAsState()
        val timeoutSeconds by preferences.timeoutSeconds.collectAsState()

        val settingsValid = isValidSuwayomiConnectionSettings(
            serverUrl = serverUrl,
            useServerPort = useServerPort,
            serverPort = serverPort,
            authType = authType,
            timeoutSeconds = timeoutSeconds,
        )

        LaunchedEffect(serverUrl, useServerPort, serverPort, authType, username, password, timeoutSeconds) {
            _isComplete = settingsValid
            testState = ConnectionTestState.Idle
        }

        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_server_info),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .secondaryItemAlpha(),
            )

            EditTextPreferenceWidget(
                title = stringResource(MR.strings.pref_server_url),
                subtitle = "%s",
                icon = null,
                value = serverUrl,
                onConfirm = {
                    if (!isValidSuwayomiServerUrl(it)) return@EditTextPreferenceWidget false
                    preferences.serverUrl.set(it.trim().trimEnd('/'))
                    true
                },
            )

            SwitchPreferenceWidget(
                title = stringResource(MR.strings.pref_server_port_enabled),
                subtitle = stringResource(MR.strings.onboarding_server_port_description),
                checked = useServerPort,
                onCheckedChanged = { preferences.useServerPort.set(it) },
            )

            if (useServerPort) {
                EditTextPreferenceWidget(
                    title = stringResource(MR.strings.pref_server_port),
                    subtitle = "%s",
                    icon = null,
                    value = serverPort,
                    onConfirm = {
                        if (!isValidSuwayomiServerPort(it)) return@EditTextPreferenceWidget false
                        preferences.serverPort.set(it.trim())
                        true
                    },
                )
            }

            BaseSliderItem(
                value = timeoutSeconds,
                valueRange = SUWAYOMI_TIMEOUT_MIN_SECONDS..SUWAYOMI_TIMEOUT_MAX_SECONDS,
                title = stringResource(MR.strings.pref_server_timeout),
                valueString = stringResource(MR.strings.onboarding_server_timeout_value, timeoutSeconds),
                onChange = { preferences.timeoutSeconds.set(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
                titleStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = eu.kanade.presentation.more.settings.widget.TitleFontSize,
                ),
            )

            ListPreferenceWidget(
                value = authType,
                title = stringResource(MR.strings.pref_server_auth_type),
                subtitle = authTypeLabel(authType),
                icon = null,
                entries = mapOf(
                    AUTH_NONE to stringResource(MR.strings.pref_server_auth_none),
                    AUTH_BASIC to stringResource(MR.strings.pref_server_auth_basic),
                    AUTH_TOKEN to "Token",
                ),
                onValueChange = { preferences.authType.set(it) },
            )

            if (authType == AUTH_BASIC || authType == AUTH_TOKEN) {
                EditTextPreferenceWidget(
                    title = stringResource(MR.strings.username),
                    subtitle = "%s",
                    icon = null,
                    value = username,
                    onConfirm = {
                        preferences.username.set(it)
                        true
                    },
                )
                EditTextPreferenceWidget(
                    title = stringResource(MR.strings.password),
                    subtitle = if (password.isBlank()) {
                        stringResource(MR.strings.none)
                    } else {
                        stringResource(MR.strings.pref_server_password_set)
                    },
                    icon = null,
                    value = password,
                    onConfirm = {
                        preferences.password.set(it)
                        true
                    },
                )
            }

            Text(
                text = statusText(settingsValid),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .secondaryItemAlpha(),
            )

            Button(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                enabled = settingsValid && testState != ConnectionTestState.Running,
                onClick = {
                    scope.launch {
                        testState = ConnectionTestState.Running
                        testState = runCatching {
                            if (authType == AUTH_TOKEN) {
                                provider.tokenAuth.login(username, password)
                                preferences.clearTokenLoginPassword()
                            }
                            client.testConnection()
                        }
                            .fold(
                                onSuccess = { ConnectionTestState.Success(it.successMessage()) },
                                onFailure = { ConnectionTestState.Failure(it.userMessage()) },
                            )
                    }
                },
            ) {
                Text(stringResource(MR.strings.pref_server_test_connection))
            }
        }
    }

    @Composable
    private fun authTypeLabel(authType: String): String {
        return when (authType) {
            AUTH_BASIC -> stringResource(MR.strings.pref_server_auth_basic)
            AUTH_TOKEN -> "Token"
            else -> stringResource(MR.strings.pref_server_auth_none)
        }
    }

    @Composable
    private fun statusText(settingsValid: Boolean): String {
        if (!settingsValid) {
            return stringResource(MR.strings.onboarding_server_invalid_settings)
        }
        return when (val state = testState) {
            ConnectionTestState.Idle -> stringResource(MR.strings.onboarding_server_test_recommended)
            ConnectionTestState.Running -> stringResource(MR.strings.onboarding_server_test_running)
            is ConnectionTestState.Success -> state.message
            is ConnectionTestState.Failure -> stringResource(MR.strings.onboarding_server_test_failed, state.message)
        }
    }

    private sealed interface ConnectionTestState {
        data object Idle : ConnectionTestState
        data object Running : ConnectionTestState
        data class Success(val message: String) : ConnectionTestState
        data class Failure(val message: String) : ConnectionTestState
    }
}
