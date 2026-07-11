@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.data.suwayomi.generated.LoginMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.RefreshTokenMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.TestConnectionSettingsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.RefreshTokenInput
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

internal class SuwayomiGraphQlClient(
    private val client: OkHttpClient,
    private val endpoint: () -> String,
    private val snapshotCache: SuwayomiSnapshotCache? = null,
) : SuwayomiTokenOperations {

    private val apolloClientFactory = SuwayomiApolloClientFactory(client, endpoint)
    private val sources = SuwayomiSourceGraphQlDomain(apolloClientFactory)
    private val trackers = SuwayomiTrackerGraphQlDomain(apolloClientFactory)
    private val extensions = SuwayomiExtensionGraphQlDomain(apolloClientFactory)
    private val library = SuwayomiLibraryGraphQlDomain(apolloClientFactory, snapshotCache, ::serverKey)
    private val server = SuwayomiServerGraphQlDomain(apolloClientFactory)
    private val chapters = SuwayomiChapterGraphQlDomain(apolloClientFactory, snapshotCache, ::serverKey)
    private val downloads = SuwayomiDownloadGraphQlDomain(apolloClientFactory, snapshotCache, ::serverKey)

    suspend fun testConnection(): SuwayomiConnectionCheck {
        val graphQlEndpoint = endpoint()
        val serverPort = runCatching {
            apolloClientFactory.create().query(TestConnectionSettingsQuery()).execute().data?.settings?.port
                ?: error("Suwayomi server returned no settings")
        }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Could not load Suwayomi settings from $graphQlEndpoint",
                    error,
                )
            }
        val sources = runCatching { sourceList() }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Connected to $graphQlEndpoint, but could not load the source list",
                    error,
                )
            }
        check(sources.isNotEmpty()) { "Connected to $graphQlEndpoint, but the server returned no sources" }
        return SuwayomiConnectionCheck(
            endpoint = graphQlEndpoint,
            sourceCount = sources.size,
            serverPort = serverPort,
        )
    }

    override suspend fun login(username: String, password: String): SuwayomiTokens {
        val response = apolloClientFactory.create().mutation(
            LoginMutation(LoginInput(username = username, password = password)),
        ).execute()
        val payload = response.data?.login
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi login failed")
        return SuwayomiTokens(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
    }

    override suspend fun refreshToken(refreshToken: String): String {
        val response = apolloClientFactory.create().mutation(
            RefreshTokenMutation(RefreshTokenInput(refreshToken = refreshToken)),
        ).execute()
        return response.data?.refreshToken?.accessToken
            ?: error(
                response.errors?.firstOrNull()?.message ?: response.exception?.message
                    ?: "Suwayomi token refresh failed",
            )
    }

    suspend fun sourceList(): List<SuwayomiSourceDto> = sources.sourceList()
    suspend fun fetchSourceManga(
        sourceId: String,
        type: FetchSourceMangaType,
        page: Int,
        queryText: String? = null,
        filters: List<SuwayomiSourceFilterChange> = emptyList(),
    ): SourceMangaPageDto = sources.fetchSourceManga(sourceId, type, page, queryText, filters)
    suspend fun sourceDetails(sourceId: String): SuwayomiSourceDetailsDto = sources.sourceDetails(sourceId)
    suspend fun sourceFilters(sourceId: String): List<SuwayomiSourceFilterDto> = sources.sourceFilters(sourceId)
    suspend fun sourcePreferences(
        sourceId: String,
    ): List<SuwayomiSourcePreferenceDto> = sources.sourcePreferences(sourceId)
    suspend fun updateSourcePreference(
        sourceId: String,
        change: SuwayomiSourcePreferenceChange,
    ): List<SuwayomiSourcePreferenceDto> = sources.updateSourcePreference(sourceId, change)

    suspend fun trackerList(): List<SuwayomiTrackerDto> = trackers.trackerList()
    suspend fun loginTrackerCredentials(
        trackerId: Int,
        username: String,
        password: String,
    ): SuwayomiTrackerDto = trackers.loginTrackerCredentials(trackerId, username, password)
    suspend fun loginTrackerOAuth(
        trackerId: Int,
        callbackUrl: String,
    ): SuwayomiTrackerDto = trackers.loginTrackerOAuth(trackerId, callbackUrl)
    suspend fun logoutTracker(trackerId: Int): SuwayomiTrackerDto = trackers.logoutTracker(trackerId)
    suspend fun trackProgress(mangaId: Int) = trackers.trackProgress(mangaId)
    suspend fun getTrackRecords(mangaId: Int): List<SuwayomiTrackRecordDto> = trackers.getTrackRecords(mangaId)
    suspend fun searchTracker(
        trackerId: Int,
        queryText: String,
    ): List<SuwayomiTrackSearchDto> = trackers.searchTracker(trackerId, queryText)
    suspend fun bindTrack(
        mangaId: Int,
        trackerId: Int,
        remoteId: Long,
        private: Boolean = false,
    ): SuwayomiTrackRecordDto = trackers.bindTrack(mangaId, trackerId, remoteId, private)
    suspend fun bindTrackRecord(
        mangaId: Int,
        trackRecordId: Int,
    ): SuwayomiTrackRecordDto = trackers.bindTrackRecord(mangaId, trackRecordId)
    suspend fun fetchTrack(recordId: Int): SuwayomiTrackRecordDto = trackers.fetchTrack(recordId)
    suspend fun updateTrack(
        recordId: Int,
        status: Int? = null,
        lastChapterRead: Double? = null,
        scoreString: String? = null,
        startDate: Long? = null,
        finishDate: Long? = null,
        private: Boolean? = null,
    ): SuwayomiTrackRecordDto? = trackers.updateTrack(
        recordId,
        status,
        lastChapterRead,
        scoreString,
        startDate,
        finishDate,
        private,
    )
    suspend fun unbindTrack(
        recordId: Int,
        deleteRemoteTrack: Boolean = false,
    ): SuwayomiTrackRecordDto? = trackers.unbindTrack(recordId, deleteRemoteTrack)

    suspend fun extensionList(): List<SuwayomiExtensionDto> = extensions.extensionList()
    suspend fun extensionStores(): List<SuwayomiExtensionStoreDto> = extensions.extensionStores()
    suspend fun addExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto = extensions.addExtensionStore(indexUrl)
    suspend fun removeExtensionStore(
        indexUrl: String,
    ): SuwayomiExtensionStoreDto? = extensions.removeExtensionStore(indexUrl)
    suspend fun updateExtension(
        pkgName: String,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): SuwayomiExtensionDto? = extensions.updateExtension(pkgName, install, uninstall, update)
    suspend fun updateExtensions(
        pkgNames: List<String>,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): List<SuwayomiExtensionDto> = extensions.updateExtensions(pkgNames, install, uninstall, update)

    suspend fun getCategories(): List<SuwayomiCategoryDto> = library.getCategories()
    suspend fun createCategory(
        name: String,
        order: Int? = null,
    ): SuwayomiCategoryDto = library.createCategory(name, order)
    suspend fun updateCategoryName(
        categoryId: Int,
        name: String,
    ): SuwayomiCategoryDto = library.updateCategoryName(categoryId, name)
    suspend fun updateCategoryFlags(
        categoryId: Int,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ): SuwayomiCategoryDto = library.updateCategoryFlags(categoryId, includeInUpdate, includeInDownload)
    suspend fun updateCategoryOrder(
        categoryId: Int,
        position: Int,
    ): List<SuwayomiCategoryDto> = library.updateCategoryOrder(categoryId, position)
    suspend fun deleteCategory(categoryId: Int): SuwayomiCategoryDto? = library.deleteCategory(categoryId)
    suspend fun getCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> = library.getCategoryMangas(categoryId)
    suspend fun getCategoryMangasSnapshot(
        categoryId: Int,
    ): SuwayomiSnapshot<List<SuwayomiMangaDto>>? = library.getCategoryMangasSnapshot(categoryId)
    suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> = library.getMangaCategories(mangaId)
    suspend fun getLibraryMangas(): List<SuwayomiMangaDto> = library.getLibraryMangas()
    suspend fun getLibraryMangasSnapshot(): SuwayomiSnapshot<List<SuwayomiMangaDto>>? = library.getLibraryMangasSnapshot()
    suspend fun getLibraryChapters(): List<SuwayomiChapterDto> = library.getLibraryChapters()
    suspend fun getLibraryTrackingData(): LibraryTrackingData = library.getLibraryTrackingData()
    suspend fun getServerStats(): ServerStatsData = library.getServerStats()
    suspend fun updateMangaCategories(
        mangaId: Int,
        categoryIds: List<Int>,
    ): List<SuwayomiCategoryDto> = library.updateMangaCategories(mangaId, categoryIds)
    suspend fun updateMangasCategories(
        mangaIds: List<Int>,
        addCategoryIds: List<Int> = emptyList(),
        removeCategoryIds: List<Int> = emptyList(),
        clearCategories: Boolean = false,
    ): List<SuwayomiMangaDto> = library.updateMangasCategories(
        mangaIds,
        addCategoryIds,
        removeCategoryIds,
        clearCategories,
    )
    suspend fun getManga(mangaId: Int): SuwayomiMangaDto = library.getManga(mangaId)
    suspend fun getMangaSnapshot(mangaId: Int): SuwayomiSnapshot<SuwayomiMangaDto>? = library.getMangaSnapshot(mangaId)
    suspend fun fetchMangaAndChaptersPartial(
        mangaId: Int,
        fetchManga: Boolean,
        fetchChapters: Boolean,
    ): GraphQlPartialResponse<FetchMangaAndChaptersPayload> = library.fetchMangaAndChaptersPartial(
        mangaId,
        fetchManga,
        fetchChapters,
    )
    suspend fun getMangaTrackSummary(mangaId: Int): SuwayomiMangaTrackSummaryDto = library.getMangaTrackSummary(mangaId)
    suspend fun getRecentChapters(
        limit: Int = 500,
    ): List<SuwayomiChapterWithMangaDto> = library.getRecentChapters(limit)
    suspend fun getReadingHistory(
        limit: Int = 500,
    ): List<SuwayomiChapterWithMangaDto> = library.getReadingHistory(limit)
    suspend fun getReadingHistoryChapterIds(
        pageSize: Int = 500,
    ): List<Int> = library.getReadingHistoryChapterIds(pageSize)
    suspend fun updateMangaLibrary(
        mangaId: Int,
        inLibrary: Boolean,
    ): SuwayomiMangaDto = library.updateMangaLibrary(mangaId, inLibrary)

    suspend fun updateLibraryMangas(): Boolean = server.updateLibraryMangas()
    suspend fun updateCategoryMangas(categoryId: Int): Boolean = server.updateCategoryMangas(categoryId)
    suspend fun getLibraryUpdateStatus(): SuwayomiLibraryUpdateStatusDto = server.getLibraryUpdateStatus()
    suspend fun stopLibraryUpdate() = server.stopLibraryUpdate()
    suspend fun lastSyncStatus(): SuwayomiSyncStatusDto? = server.lastSyncStatus()
    suspend fun startSync(): StartSyncPayload = server.startSync()
    suspend fun getGlobalMeta(key: String): SuwayomiGlobalMetaDto? = server.getGlobalMeta(key)
    suspend fun setGlobalMeta(key: String, value: String): SuwayomiGlobalMetaDto = server.setGlobalMeta(key, value)
    suspend fun deleteGlobalMeta(key: String): SuwayomiGlobalMetaDto? = server.deleteGlobalMeta(key)
    suspend fun clearCachedImages(
        cachedPages: Boolean? = null,
        cachedThumbnails: Boolean? = null,
        downloadedThumbnails: Boolean? = null,
    ): SuwayomiClearCachedImagesDto = server.clearCachedImages(cachedPages, cachedThumbnails, downloadedThumbnails)
    suspend fun setMangaMeta(
        mangaId: Int,
        key: String,
        value: String,
    ): SuwayomiMangaMetaDto = server.setMangaMeta(mangaId, key, value)
    suspend fun serverAbout(): SuwayomiServerAboutDto = server.serverAbout()
    suspend fun webUiAbout(): SuwayomiWebUiAboutDto = server.webUiAbout()
    suspend fun serverSettings(): SuwayomiServerSettingsDto = server.serverSettings()
    suspend fun setSettings(settings: JsonObject): SuwayomiServerSettingsDto = server.setSettings(settings)
    suspend fun setExtensionRepos(
        extensionRepos: List<String>,
    ): SuwayomiServerSettingsDto = server.setExtensionRepos(extensionRepos)

    suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> = chapters.getChapters(mangaId)
    suspend fun getCachedChapters(mangaId: Int): List<SuwayomiChapterDto> = chapters.getCachedChapters(mangaId)
    suspend fun getChaptersSnapshot(
        mangaId: Int,
    ): SuwayomiSnapshot<List<SuwayomiChapterDto>>? = chapters.getChaptersSnapshot(mangaId)
    suspend fun getChapterPages(chapterId: Int): List<String> = chapters.getChapterPages(chapterId)
    suspend fun getChapterPageManifest(
        chapterId: Int,
    ): SuwayomiChapterPageManifest = chapters.getChapterPageManifest(chapterId)
    suspend fun updateChapterProgress(
        chapterId: Int,
        isRead: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto = chapters.updateChapterProgress(chapterId, isRead, lastPageRead)
    suspend fun updateChapterRead(
        chapterId: Int,
        isRead: Boolean,
    ): SuwayomiChapterDto = chapters.updateChapterRead(chapterId, isRead)
    suspend fun updateChapterBookmark(
        chapterId: Int,
        isBookmarked: Boolean,
    ): SuwayomiChapterDto = chapters.updateChapterBookmark(chapterId, isBookmarked)
    suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto = chapters.updateChapterMigrationState(chapterId, isRead, isBookmarked, lastPageRead)
    suspend fun updateChaptersRead(
        chapterIds: List<Int>,
        isRead: Boolean,
    ): List<SuwayomiChapterDto> = chapters.updateChaptersRead(chapterIds, isRead)
    suspend fun updateChaptersBookmark(
        chapterIds: List<Int>,
        isBookmarked: Boolean,
    ): List<SuwayomiChapterDto> = chapters.updateChaptersBookmark(chapterIds, isBookmarked)

    suspend fun getDownloadStatus(): SuwayomiDownloadStatusDto = downloads.getDownloadStatus()
    suspend fun getDownloadStatusSnapshot(): SuwayomiSnapshot<SuwayomiDownloadStatusDto>? = downloads.getDownloadStatusSnapshot()
    suspend fun enqueueChapterDownloads(chapterIds: List<Int>) = downloads.enqueueChapterDownloads(chapterIds)
    suspend fun dequeueChapterDownload(chapterId: Int) = downloads.dequeueChapterDownload(chapterId)
    suspend fun dequeueChapterDownloads(
        chapterIds: List<Int>,
    ): SuwayomiDownloadStatusDto = downloads.dequeueChapterDownloads(chapterIds)
    suspend fun startDownloader() = downloads.startDownloader()
    suspend fun stopDownloader() = downloads.stopDownloader()
    suspend fun clearDownloader() = downloads.clearDownloader()
    suspend fun reorderChapterDownload(
        chapterId: Int,
        to: Int,
    ): SuwayomiDownloadStatusDto = downloads.reorderChapterDownload(chapterId, to)
    suspend fun deleteDownloadedChapters(
        chapterIds: List<Int>,
    ): List<SuwayomiChapterDto> = downloads.deleteDownloadedChapters(chapterIds)

    private fun serverKey(): String = endpoint().trimEnd('/')
}

enum class FetchSourceMangaType {
    SEARCH,
    POPULAR,
    LATEST,
}
