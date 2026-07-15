package eu.kanade.tachiyomi.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateAboutManualCheckTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = AmatsubuDevice()

    @After
    fun clearControlledRelease() {
        controlledReleasePreferences().edit().clear().commit()
    }

    @Test
    fun controlledManualCheckShowsLoadingUpdatePromptChangelogAndDismisses() {
        controlledReleasePreferences().edit()
            .putString(KEY_MODE, MODE_NEW_UPDATE)
            .putString(KEY_VERSION, "v0.1.1-controlled")
            .putString(
                KEY_CHANGELOG,
                """
                    ## Controlled About update

                    - Validates the real About check row
                    - Uses no live GitHub response
                """.trimIndent(),
            )
            .putString(KEY_RELEASE_LINK, "https://example.com/releases/v0.1.1-controlled")
            .putString(KEY_DOWNLOAD_LINK, "https://example.com/amatsubu-arm64-v8a-modern-android-v0.1.1-controlled.apk")
            .putLong(KEY_DELAY_MS, 1_500L)
            .commit()

        openAbout()

        app.tapText("Check for updates")
        app.assertDesc("Checking for updates")
        app.assertText("New version available!")
        app.assertText("v0.1.1-controlled")
        app.assertText("Controlled About update")
        app.assertTextContains("Validates the real About check row")
        app.scrollToText("Open on GitHub")
        app.assertText("Open on GitHub")
        app.assertText("Download")
        app.assertText("Not now")

        app.tapText("Not now")
        app.assertAnyText("About", "Version")
    }

    @Test
    fun controlledManualCheckReportsNoUpdate() {
        controlledReleasePreferences().edit()
            .putString(KEY_MODE, MODE_NO_UPDATE)
            .commit()

        openAbout()

        app.tapText("Check for updates")
        waitForLastResult(MODE_NO_UPDATE)
        app.assertText("About")
        check(!app.hasText("New version available!")) {
            "No-update manual check unexpectedly opened the update prompt"
        }
    }

    private fun openAbout() {
        app.launchSettings()
        app.scrollToText("About")
        app.tapText("About")
        app.assertText("Check for updates")
    }

    private fun controlledReleasePreferences() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun waitForLastResult(expected: String) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            if (controlledReleasePreferences().getString(KEY_LAST_RESULT, null) == expected) return
            Thread.sleep(250L)
        }
        error("Timed out waiting for controlled update result \"$expected\"")
    }

    private companion object {
        const val PREFERENCES_NAME = "debug_app_update_release"
        const val KEY_MODE = "mode"
        const val KEY_VERSION = "version"
        const val KEY_CHANGELOG = "changelog"
        const val KEY_RELEASE_LINK = "release_link"
        const val KEY_DOWNLOAD_LINK = "download_link"
        const val KEY_DELAY_MS = "delay_ms"
        const val KEY_LAST_RESULT = "last_result"
        const val MODE_NEW_UPDATE = "new_update"
        const val MODE_NO_UPDATE = "no_update"
    }
}
