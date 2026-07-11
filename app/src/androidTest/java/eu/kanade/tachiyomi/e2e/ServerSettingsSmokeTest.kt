package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerSettingsSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun serverSettingsScreenRenders() {
        app.launchSettings()
        app.scrollToTag(Selectors.Tags.SERVER_SETTINGS)
        app.tapTag(Selectors.Tags.SERVER_SETTINGS)

        app.assertAnyText(
            "Server bindings",
            "Server port",
            "Server authentication",
            "WebUI",
            "Server unreachable",
            Selectors.Text.SERVER,
        )
    }
}
