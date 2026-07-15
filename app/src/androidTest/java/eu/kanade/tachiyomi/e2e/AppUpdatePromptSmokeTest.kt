package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdatePromptSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun controlledUpdatePromptShowsChangelogActionsAndDismisses() {
        app.launchControlledUpdatePrompt()

        app.assertText("New version available!")
        app.assertText("v0.1.1")
        app.assertText("Controlled update")
        app.assertTextContains("Restored updater prompt")
        app.assertText("Open on GitHub")
        app.assertText("Download")
        app.assertText("Not now")

        app.tapText("Not now")
        app.assertAnyText(
            Selectors.Text.LIBRARY,
            Selectors.Text.UPDATES,
            Selectors.Text.HISTORY,
            Selectors.Text.BROWSE,
            Selectors.Text.MORE,
        )
    }
}
