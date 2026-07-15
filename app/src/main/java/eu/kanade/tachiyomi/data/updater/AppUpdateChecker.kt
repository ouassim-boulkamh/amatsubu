package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.domain.release.interactor.GetApplicationRelease
import eu.kanade.domain.release.model.Release
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.di.appDependencies
import eu.kanade.tachiyomi.util.system.isFossBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.core.common.util.lang.withIOContext

class AppUpdateChecker(private val context: Context) {

    suspend fun checkForUpdate(forceCheck: Boolean = false): GetApplicationRelease.Result = withIOContext {
        DebugControlledAppUpdateRelease.result(context, forceCheck)?.let { controlledResult ->
            if (controlledResult is GetApplicationRelease.Result.NewUpdate) {
                AppUpdateNotifier(context).promptUpdate(controlledResult.release)
            }
            return@withIOContext controlledResult
        }

        context.appDependencies.getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = isFossBuildType,
                isPreview = isPreviewBuildType,
                commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                versionName = BuildConfig.VERSION_NAME,
                repository = GITHUB_REPOSITORY,
                forceCheck = forceCheck,
            ),
        ).also { result ->
            if (result is GetApplicationRelease.Result.NewUpdate) {
                AppUpdateNotifier(
                    context,
                ).promptUpdate(result.release)
            }
        }
    }
}

const val GITHUB_REPOSITORY = "ouassim-boulkamh/amatsubu"
const val RELEASE_URL = "https://github.com/$GITHUB_REPOSITORY/releases"

private object DebugControlledAppUpdateRelease {
    private const val PREFERENCES_NAME = "debug_app_update_release"
    private const val KEY_MODE = "mode"
    private const val KEY_VERSION = "version"
    private const val KEY_CHANGELOG = "changelog"
    private const val KEY_RELEASE_LINK = "release_link"
    private const val KEY_DOWNLOAD_LINK = "download_link"
    private const val KEY_DELAY_MS = "delay_ms"
    private const val KEY_LAST_RESULT = "last_result"
    private const val MODE_NEW_UPDATE = "new_update"
    private const val MODE_NO_UPDATE = "no_update"

    fun result(context: Context, forceCheck: Boolean): GetApplicationRelease.Result? {
        if (!BuildConfig.DEBUG || !forceCheck) return null

        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val delayMs = preferences.getLong(KEY_DELAY_MS, 0L)
        if (delayMs > 0L) Thread.sleep(delayMs)

        return when (preferences.getString(KEY_MODE, null)) {
            MODE_NEW_UPDATE -> {
                preferences.edit().putString(KEY_LAST_RESULT, MODE_NEW_UPDATE).apply()
                GetApplicationRelease.Result.NewUpdate(
                    Release(
                        version = preferences.getString(KEY_VERSION, null) ?: "v0.1.1",
                        info = preferences.getString(KEY_CHANGELOG, null).orEmpty(),
                        releaseLink = preferences.getString(KEY_RELEASE_LINK, null)
                            ?: "https://example.com/releases/v0.1.1",
                        downloadLink = preferences.getString(KEY_DOWNLOAD_LINK, null)
                            ?: "https://example.com/amatsubu-arm64-v8a-modern-android-v0.1.1.apk",
                    ),
                )
            }
            MODE_NO_UPDATE -> {
                preferences.edit().putString(KEY_LAST_RESULT, MODE_NO_UPDATE).apply()
                GetApplicationRelease.Result.NoNewUpdate
            }
            else -> null
        }
    }
}
