package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseServerSourceSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun browseServerSourcesScreenRenders() {
        app.launchApp()

        app.openHomeTab(Selectors.Tags.HOME_BROWSE, Selectors.Text.BROWSE)

        app.assertAnyText(
            Selectors.Text.SOURCES,
            Selectors.Text.EXTENSIONS,
            "Server sources",
            "No sources found",
            "Server unreachable",
        )
    }

    @Test
    fun controlledServerSourceFiltersPreferencesAndMangaRender() {
        app.launchControlledSource()

        app.assertText(Selectors.ControlledSource.MANGA_TITLE)
        app.assertTag(Selectors.Tags.SOURCE_SEARCH_BUTTON)

        app.tapDesc(Selectors.Text.SETTINGS)
        app.assertAnyText("Settings", "Override base URL", "Base URL", "Preferences")
        app.pressBack()

        app.tapText(Selectors.ControlledSource.MANGA_TITLE)
        app.assertAnyText(
            Selectors.ControlledSource.MANGA_TITLE,
            "Chapters",
            Selectors.ControlledSource.BASELINE_CHAPTER,
        )
    }
}
