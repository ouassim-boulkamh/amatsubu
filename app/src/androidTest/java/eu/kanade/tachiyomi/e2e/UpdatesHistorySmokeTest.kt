package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdatesHistorySmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun updatesAndHistoryScreensRender() {
        app.launchApp()

        app.openHomeTab(Selectors.Tags.HOME_UPDATES, Selectors.Text.UPDATES)
        app.assertAnyText(Selectors.Text.UPDATES, "No recent updates", "Server unreachable")

        app.openHomeTab(Selectors.Tags.HOME_HISTORY, Selectors.Text.HISTORY)
        app.assertAnyText(Selectors.Text.HISTORY, "No history", "Server unreachable")
    }
}
