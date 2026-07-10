package eu.kanade.tachiyomi.data.suwayomi

internal enum class ServerMangaRefreshMode {
    Metadata,
    MetadataAndCover,
}

internal suspend fun SuwayomiGraphQlClient.refreshServerMangaFromSource(
    mangaId: Int,
    mode: ServerMangaRefreshMode = ServerMangaRefreshMode.Metadata,
): GraphQlPartialResponse<FetchMangaAndChaptersPayload> {
    if (mode == ServerMangaRefreshMode.MetadataAndCover) {
        clearCachedImages(
            cachedThumbnails = true,
            downloadedThumbnails = true,
        )
    }
    return fetchMangaAndChaptersPartial(
        mangaId = mangaId,
        fetchManga = true,
        fetchChapters = true,
    )
}
