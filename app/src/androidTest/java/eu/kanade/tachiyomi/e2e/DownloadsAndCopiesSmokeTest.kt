package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsAndCopiesSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun downloadQueueScreenRendersWithoutMutatingServerState() {
        app.launchApp()

        app.openHomeTab(Selectors.Tags.HOME_MORE, Selectors.Text.MORE)
        app.scrollToTag(Selectors.Tags.DOWNLOAD_QUEUE)
        app.tapTag(Selectors.Tags.DOWNLOAD_QUEUE)

        app.assertAnyText("Download queue", "No downloads", "Server unreachable")
        app.assertAnyTag(Selectors.Tags.DOWNLOAD_QUEUE_EMPTY, Selectors.Tags.DOWNLOAD_QUEUE_LIST)
    }
}
