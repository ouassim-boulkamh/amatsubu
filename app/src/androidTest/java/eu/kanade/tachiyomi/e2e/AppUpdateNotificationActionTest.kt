package eu.kanade.tachiyomi.e2e

import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateNotificationActionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun downloadActionCarriesUpdaterUrlAndTitle() {
        val intent = NotificationReceiver.downloadAppUpdateIntent(
            context = context,
            url = "https://example.com/app-arm64-v8a-release.apk",
            title = "v0.2.0",
        )

        assertEquals(
            "https://example.com/app-arm64-v8a-release.apk",
            intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_URL),
        )
        assertEquals("v0.2.0", intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_TITLE))
    }

    @Test
    fun downloadActionAllowsMissingTitleButRequiresUrl() {
        val intent = NotificationReceiver.downloadAppUpdateIntent(
            context = context,
            url = "https://example.com/app-arm64-v8a-release.apk",
        )

        assertEquals(
            "https://example.com/app-arm64-v8a-release.apk",
            intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_URL),
        )
        assertNull(intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_TITLE))
    }

    @Test
    fun cancelActionDoesNotCarryDownloadInputs() {
        val intent = NotificationReceiver.cancelDownloadAppUpdateIntent(context)

        assertNull(intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_URL))
        assertNull(intent.getStringExtra(AppUpdateDownloadJob.EXTRA_DOWNLOAD_TITLE))
    }
}
