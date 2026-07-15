package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.release.interactor.GetApplicationRelease
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.updaterEnabled
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LinkIcon
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Github
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object AboutScreen : Screen() {

    private const val REPOSITORY_URL = "https://github.com/ouassim-boulkamh/amatsubu"

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var checkingUpdates by remember { mutableStateOf(false) }
        val dateFormat = UiPreferences.dateFormat(context.appDependencies.uiPreferences.dateFormat.get())

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_category_about),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                item {
                    LogoHeader(
                        iconPadding = PaddingValues(vertical = 56.dp),
                    )
                }

                if (updaterEnabled || BuildConfig.DEBUG) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.check_for_updates),
                            widget = {
                                if (checkingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = androidx.compose.ui.Modifier.semantics {
                                            contentDescription =
                                                context.stringResource(MR.strings.update_check_checking)
                                        },
                                        strokeWidth = 3.dp,
                                    )
                                }
                            },
                            onPreferenceClick = {
                                if (checkingUpdates) return@TextPreferenceWidget
                                scope.launch {
                                    checkingUpdates = true
                                    try {
                                        when (
                                            val result = AppUpdateChecker(
                                                context,
                                            ).checkForUpdate(forceCheck = true)
                                        ) {
                                            is GetApplicationRelease.Result.NewUpdate -> navigator.push(
                                                NewUpdateScreen(
                                                    result.release.version,
                                                    result.release.info,
                                                    result.release.releaseLink,
                                                    result.release.downloadLink,
                                                ),
                                            )
                                            GetApplicationRelease.Result.NoNewUpdate -> context.toast(
                                                MR.strings.update_check_no_new_updates,
                                            )
                                            GetApplicationRelease.Result.OsTooOld -> context.toast(
                                                MR.strings.update_check_eol,
                                            )
                                        }
                                    } catch (error: Exception) {
                                        logcat(LogPriority.ERROR, error) { "Failed to check for app update" }
                                        context.toast(error.message ?: "Failed to check for updates")
                                    } finally {
                                        checkingUpdates = false
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.version),
                        subtitle = getVersionName(withBuildDate = true, dateFormat = dateFormat),
                        onPreferenceClick = {
                            val deviceInfo = CrashLogUtil(context).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                        },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = "Mihon upstream base",
                        subtitle = "${BuildConfig.MIHON_BASE_VERSION} (${BuildConfig.MIHON_BASE_COMMIT})",
                    )
                }

                if (!BuildConfig.DEBUG) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.whats_new),
                            onPreferenceClick = { uriHandler.openUri("$REPOSITORY_URL/releases") },
                        )
                    }
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.licenses),
                        onPreferenceClick = { navigator.push(OpenSourceLicensesScreen()) },
                    )
                }

                item {
                    LinkIcon(
                        label = "Source",
                        icon = CustomIcons.Github,
                        url = REPOSITORY_URL,
                    )
                }
            }
        }
    }

    fun getVersionName(
        withBuildDate: Boolean,
        dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
    ): String {
        return when {
            BuildConfig.DEBUG -> {
                "${BuildConfig.VERSION_NAME} debug ${BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime(dateFormat)})"
                    } else {
                        it
                    }
                }
            }
            isPreviewBuildType -> {
                "${BuildConfig.VERSION_NAME} preview r${BuildConfig.COMMIT_COUNT}".let {
                    if (withBuildDate) {
                        "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime(dateFormat)})"
                    } else {
                        "$it (${BuildConfig.COMMIT_SHA})"
                    }
                }
            }
            else -> {
                BuildConfig.VERSION_NAME.let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime(dateFormat)})"
                    } else {
                        it
                    }
                }
            }
        }
    }

    internal fun getFormattedBuildTime(
        dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
    ): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(BuildConfig.BUILD_TIME),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(dateFormat)
        } catch (e: Exception) {
            BuildConfig.BUILD_TIME
        }
    }
}
