package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

internal class SuwayomiDisplaySource(
    dto: SuwayomiSourceDto,
    fallbackId: Long,
) : Source {
    override val id: Long = dto.id.toLongOrNull() ?: fallbackId
    override val name: String = dto.name
    override val lang: String = dto.lang
    override val supportsLatest: Boolean = dto.supportsLatest

    override suspend fun getPopularManga(page: Int): MangasPage = unsupported()

    override suspend fun getLatestUpdates(page: Int): MangasPage = unsupported()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = unsupported()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = unsupported()

    override suspend fun getPageList(chapter: SChapter): List<Page> = unsupported()

    override fun toString(): String = if (lang.isEmpty()) name else "$name (${lang.uppercase()})"

    private fun unsupported(): Nothing {
        throw UnsupportedOperationException("Suwayomi display source is not a browse-source adapter")
    }
}
