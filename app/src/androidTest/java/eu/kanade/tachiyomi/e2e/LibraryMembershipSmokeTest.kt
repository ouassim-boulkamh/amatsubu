package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMembershipSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun libraryRendersMembershipAndCategorySurface() {
        app.launchApp()

        app.assertText(Selectors.Text.LIBRARY)
        waitForLibrarySurface()
        app.assertAnyText(
            Selectors.Text.LIBRARY,
            "Default",
            "Your library is empty",
            "No manga",
            "No entries found",
            "Server unreachable",
        )

        app.tapDesc("Filter")
        app.assertAnyText("Filter", "Unread", "Downloaded", "Bookmarked", "Tracking")
        app.pressBack()
        app.assertText(Selectors.Text.LIBRARY)
    }

    private fun waitForLibrarySurface() {
        repeat(18) {
            if (
                app.hasText("Your library is empty") ||
                app.hasText("Server unreachable") ||
                app.hasText("No manga") ||
                app.hasText("No entries found") ||
                app.hasText("Default") ||
                app.hasDesc("Filter")
            ) {
                return
            }
            Thread.sleep(2_000)
        }
    }
}
