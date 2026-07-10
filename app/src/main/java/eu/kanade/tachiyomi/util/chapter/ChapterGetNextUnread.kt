package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga, basePreferences: BasePreferences): Chapter? {
    return applyFilters(manga, basePreferences).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.chapter.read }
        } else {
            chapters.find { !it.chapter.read }
        }
    }?.chapter
}
