package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaReaderSmokeTest {
    private val app = AmatsubuDevice()

    @Test
    fun controlledSourceMangaChapterOpensReader() {
        app.launchControlledSource()

        app.tapText(Selectors.ControlledSource.MANGA_TITLE)
        app.scrollToText(Selectors.ControlledSource.BASELINE_CHAPTER)
        app.tapText(Selectors.ControlledSource.BASELINE_CHAPTER)

        app.assertTag(Selectors.Tags.READER_SURFACE)
    }
}
