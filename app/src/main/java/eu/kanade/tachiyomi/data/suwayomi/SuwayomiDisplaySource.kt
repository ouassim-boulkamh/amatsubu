package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

internal class SuwayomiDisplaySource(
    sourceId: Long,
    override val name: String,
    override val lang: String,
    override val supportsLatest: Boolean,
) : Source {
    constructor(
        dto: SuwayomiSourceDto,
        fallbackId: Long,
    ) : this(
        sourceId = dto.id.toLongOrNull() ?: fallbackId,
        name = dto.displayName.ifBlank { dto.name },
        lang = dto.lang,
        supportsLatest = dto.supportsLatest,
    )

    override val id: Long = sourceId

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
