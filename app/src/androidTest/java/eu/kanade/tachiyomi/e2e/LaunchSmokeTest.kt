package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun launchShowsHomeShellOrOnboarding() {
        app.launchApp()

        app.assertAnyText(
            Selectors.Text.LIBRARY,
            Selectors.Text.UPDATES,
            Selectors.Text.HISTORY,
            Selectors.Text.BROWSE,
            Selectors.Text.MORE,
            "Onboarding",
        )
    }
}
