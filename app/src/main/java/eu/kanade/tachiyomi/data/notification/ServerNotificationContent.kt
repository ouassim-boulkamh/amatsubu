package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto

internal object ServerNotificationContent {

    fun newChapterLines(
        chapters: List<SuwayomiChapterWithMangaDto>,
        hideContent: Boolean,
        maxLines: Int,
    ): List<String> {
        if (hideContent) return emptyList()

        return chapters
            .groupBy { it.manga.title }
            .entries
            .take(maxLines)
            .map { (mangaTitle, mangaChapters) ->
                "$mangaTitle - ${mangaChapters.take(3).joinToString { it.name }}"
            }
    }

    fun downloadDetail(
        mangaTitle: String,
        chapterName: String,
        hideContent: Boolean,
        redactedText: String,
    ): String {
        return if (hideContent) {
            redactedText
        } else {
            "$mangaTitle - $chapterName"
        }
    }

    fun extensionUpdateLines(
        extensions: List<SuwayomiExtensionDto>,
        hideContent: Boolean,
        maxLines: Int,
    ): List<String> {
        if (hideContent) return emptyList()

        return extensions
            .take(maxLines)
            .map { it.name }
    }
}
