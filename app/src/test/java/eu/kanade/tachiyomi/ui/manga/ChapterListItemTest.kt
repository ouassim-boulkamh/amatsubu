package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterListItemTest {

    @Test
    fun `isDownloaded is true for server downloads`() {
        assertTrue(item(downloadState = Download.State.DOWNLOADED).isDownloaded)
    }

    @Test
    fun `isDownloaded is false for fresh local device copies without a server download`() {
        assertFalse(item(deviceCopyState = DeviceCopyState.FRESH).isDownloaded)
    }

    @Test
    fun `isLocallyDownloaded is true for fresh local device copies`() {
        assertTrue(item(deviceCopyState = DeviceCopyState.FRESH).isLocallyDownloaded)
    }

    @Test
    fun `isLocallyDownloaded is false for server downloads without a fresh local device copy`() {
        assertFalse(item(downloadState = Download.State.DOWNLOADED).isLocallyDownloaded)
    }

    @Test
    fun `download states are false when no server download or fresh local device copy exists`() {
        assertFalse(item().isDownloaded)
        assertFalse(item().isLocallyDownloaded)
    }

    @Test
    fun `isLocallyDownloaded is false for incomplete local device copies`() {
        assertFalse(item(deviceCopyState = DeviceCopyState.INCOMPLETE).isLocallyDownloaded)
    }

    private fun item(
        downloadState: Download.State = Download.State.NOT_DOWNLOADED,
        deviceCopyState: DeviceCopyState = DeviceCopyState.NONE,
    ): ChapterList.Item {
        return ChapterList.Item(
            chapter = Chapter(
                id = 1,
                mangaId = 1,
                read = false,
                bookmark = false,
                lastPageRead = 0,
                dateFetch = 0,
                sourceOrder = 1,
                url = "/chapters/1",
                name = "Chapter 1",
                dateUpload = 0,
                chapterNumber = 1.0,
                scanlator = null,
                lastModifiedAt = 0,
                version = 0,
                memo = JsonObject.EMPTY,
            ),
            downloadState = downloadState,
            downloadProgress = 0,
            deviceCopyState = deviceCopyState,
            deviceCopyProgress = 0,
        )
    }
}
