package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQlResponse<T>(
    val data: T? = null,
    val errors: List<GraphQlError> = emptyList(),
)

data class GraphQlPartialResponse<T>(
    val data: T?,
    val errors: List<GraphQlError>,
) {
    val errorMessages: List<String> = errors.map { it.message }
}

@Serializable
data class GraphQlError(
    val message: String,
)

data class SuwayomiConnectionCheck(
    val endpoint: String,
    val sourceCount: Int,
    val serverPort: Int,
)

@Serializable
data class SourceListData(
    val sources: SourceNodeList,
)

@Serializable
data class SourceNodeList(
    val nodes: List<SuwayomiSourceDto>,
)

@Serializable
data class TrackersData(
    val trackers: TrackerNodeList,
)

@Serializable
data class TrackerNodeList(
    val nodes: List<SuwayomiTrackerDto>,
)

@Serializable
data class SuwayomiTrackerDto(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val isLoggedIn: Boolean = false,
    val authUrl: String? = null,
    val supportsTrackDeletion: Boolean = false,
    val supportsReadingDates: Boolean = false,
    val supportsPrivateTracking: Boolean = false,
    val statuses: List<SuwayomiTrackStatusDto> = emptyList(),
    val scores: List<String> = emptyList(),
)

@Serializable
data class SuwayomiTrackStatusDto(
    val value: Int,
    val name: String,
)

@Serializable
data class TrackerLoginData(
    val loginTrackerCredentials: TrackerLoginPayload? = null,
    val loginTrackerOAuth: TrackerLoginPayload? = null,
)

@Serializable
data class TrackerLoginPayload(
    val isLoggedIn: Boolean,
    val tracker: SuwayomiTrackerDto,
)

@Serializable
data class TrackerLogoutData(
    val logoutTracker: TrackerLoginPayload,
)

@Serializable
data class TrackRecordsData(
    val trackRecords: TrackRecordNodeList,
)

@Serializable
data class TrackRecordNodeList(
    val nodes: List<SuwayomiTrackRecordDto>,
)

@Serializable
data class SuwayomiTrackRecordDto(
    val id: Int,
    val mangaId: Int,
    val trackerId: Int,
    val remoteId: String,
    val libraryId: String? = null,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Int,
    val status: Int,
    val score: Double,
    val displayScore: String? = null,
    val remoteUrl: String,
    val startDate: String = "0",
    val finishDate: String = "0",
    val private: Boolean = false,
    val tracker: SuwayomiTrackerDto? = null,
)

@Serializable
data class SearchTrackerData(
    val searchTracker: SearchTrackerPayload,
)

@Serializable
data class SearchTrackerPayload(
    val trackSearches: List<SuwayomiTrackSearchDto>,
)

@Serializable
data class SuwayomiTrackSearchDto(
    val id: Int,
    val trackerId: Int,
    val remoteId: String,
    val libraryId: String? = null,
    val title: String,
    val lastChapterRead: Double = 0.0,
    val totalChapters: Int = 0,
    val trackingUrl: String,
    val coverUrl: String? = null,
    val summary: String? = null,
    val publishingStatus: String? = null,
    val publishingType: String? = null,
    val startDate: String? = null,
    val status: Int = 0,
    val score: Double = 0.0,
    val startedReadingDate: String = "0",
    val finishedReadingDate: String = "0",
    val private: Boolean = false,
)

@Serializable
data class BindTrackData(
    val bindTrack: TrackRecordPayload,
)

@Serializable
data class BindTrackRecordData(
    val bindTrackRecord: TrackRecordPayload,
)

@Serializable
data class FetchTrackData(
    val fetchTrack: TrackRecordPayload,
)

@Serializable
data class UpdateTrackData(
    val updateTrack: NullableTrackRecordPayload,
)

@Serializable
data class UnbindTrackData(
    val unbindTrack: NullableTrackRecordPayload,
)

@Serializable
data class TrackRecordPayload(
    val trackRecord: SuwayomiTrackRecordDto,
)

@Serializable
data class NullableTrackRecordPayload(
    val trackRecord: SuwayomiTrackRecordDto? = null,
)

@Serializable
data class AllCategoriesData(
    val categories: CategoryNodeList,
)

@Serializable
data class CategoryNodeList(
    val nodes: List<SuwayomiCategoryDto>,
)

@Serializable
data class SuwayomiCategoryDto(
    val id: Int,
    val name: String,
    val order: Int = 0,
    val includeInDownload: SuwayomiCategoryFlag = SuwayomiCategoryFlag.UNSET,
    val includeInUpdate: SuwayomiCategoryFlag = SuwayomiCategoryFlag.UNSET,
)

@Serializable
enum class SuwayomiCategoryFlag {
    EXCLUDE,
    INCLUDE,
    UNSET,
}

@Serializable
data class GetCategoryMangasData(
    val category: CategoryMangasDto,
)

@Serializable
data class CategoryMangasDto(
    val id: Int,
    val mangas: MangaNodeList,
)

@Serializable
data class GetMangaCategoriesData(
    val manga: MangaCategoriesDto,
)

@Serializable
data class MangaCategoriesDto(
    val categories: CategoryNodeList,
)

@Serializable
data class UpdateMangaCategoriesData(
    val updateMangaCategories: UpdateMangaCategoriesPayload,
)

@Serializable
data class UpdateMangaCategoriesPayload(
    val manga: MangaCategoriesDto,
)

@Serializable
data class UpdateMangasCategoriesData(
    val updateMangasCategories: UpdateMangasCategoriesPayload,
)

@Serializable
data class UpdateMangasCategoriesPayload(
    val mangas: List<SuwayomiMangaDto>,
)

@Serializable
data class CreateCategoryData(
    val createCategory: CreateCategoryPayload? = null,
)

@Serializable
data class CreateCategoryPayload(
    val category: SuwayomiCategoryDto,
)

@Serializable
data class UpdateCategoryData(
    val updateCategory: UpdateCategoryPayload? = null,
)

@Serializable
data class UpdateCategoryPayload(
    val category: SuwayomiCategoryDto,
)

@Serializable
data class UpdateCategoryOrderData(
    val updateCategoryOrder: UpdateCategoryOrderPayload? = null,
)

@Serializable
data class UpdateCategoryOrderPayload(
    val categories: List<SuwayomiCategoryDto>,
)

@Serializable
data class DeleteCategoryData(
    val deleteCategory: DeleteCategoryPayload? = null,
)

@Serializable
data class DeleteCategoryPayload(
    val category: SuwayomiCategoryDto? = null,
)

@Serializable
data class MangaNodeList(
    val nodes: List<SuwayomiMangaDto>,
)

@Serializable
data class LibraryMangaListData(
    val mangas: MangaNodeList,
)

@Serializable
data class LibraryTrackingData(
    val libraryTrackingMangas: LibraryTrackingMangaNodeList,
    val trackers: TrackerNodeList,
)

@Serializable
data class LibraryTrackingMangaNodeList(
    val nodes: List<SuwayomiLibraryTrackingMangaDto>,
)

@Serializable
data class SuwayomiLibraryTrackingMangaDto(
    val id: Int,
    val trackRecords: StatsTrackRecordNodeList = StatsTrackRecordNodeList(emptyList()),
)

@Serializable
data class ServerStatsData(
    val statisticsMangas: ServerStatsMangaNodeList,
    val totalChapters: CountNodeList,
    val readChapters: CountNodeList,
    val downloadedChapters: CountNodeList,
    val settings: SuwayomiServerSettingsDto,
    val trackers: TrackerNodeList,
)

@Serializable
data class CountNodeList(
    val totalCount: Int,
)

@Serializable
data class ServerStatsMangaNodeList(
    val totalCount: Int,
    val nodes: List<SuwayomiStatsMangaDto>,
)

@Serializable
data class SuwayomiStatsMangaDto(
    val id: Int,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val unreadCount: Long = 0,
    val downloadCount: Int = 0,
    val initialized: Boolean = false,
    val latestReadChapter: SuwayomiStatsLatestReadChapterDto? = null,
    val sourceId: String,
    val updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    val categories: StatsCategoryNodeList = StatsCategoryNodeList(emptyList()),
    val trackRecords: StatsTrackRecordNodeList = StatsTrackRecordNodeList(emptyList()),
)

@Serializable
data class SuwayomiStatsLatestReadChapterDto(
    val lastReadAt: String = "0",
)

@Serializable
data class StatsCategoryNodeList(
    val nodes: List<SuwayomiStatsCategoryDto>,
)

@Serializable
data class SuwayomiStatsCategoryDto(
    val id: Int,
    val includeInUpdate: SuwayomiCategoryFlag = SuwayomiCategoryFlag.UNSET,
)

@Serializable
data class StatsTrackRecordNodeList(
    val nodes: List<SuwayomiStatsTrackRecordDto>,
)

@Serializable
data class SuwayomiStatsTrackRecordDto(
    val trackerId: Int,
    val score: Double,
)

@Serializable
data class RecentChaptersData(
    val chapters: RecentChapterNodeList,
)

@Serializable
data class RecentChapterNodeList(
    val nodes: List<SuwayomiChapterWithMangaDto>,
)

@Serializable
data class ReadingHistoryIdsData(
    val chapters: ReadingHistoryIdNodeList,
)

@Serializable
data class ReadingHistoryIdNodeList(
    val nodes: List<ReadingHistoryIdDto>,
    val totalCount: Int = 0,
)

@Serializable
data class ReadingHistoryIdDto(
    val id: Int,
)

@Serializable
data class UpdateLibraryMangaData(
    val updateLibraryManga: UpdateLibraryMangaPayload,
)

@Serializable
data class UpdateLibraryMangaPayload(
    val updateStatus: SuwayomiUpdateStatusDto,
)

@Serializable
data class UpdateCategoryMangaData(
    val updateCategoryManga: UpdateCategoryMangaPayload,
)

@Serializable
data class UpdateCategoryMangaPayload(
    val updateStatus: SuwayomiUpdateStatusDto,
)

@Serializable
data class SuwayomiUpdateStatusDto(
    val isRunning: Boolean = false,
)

@Serializable
data class LibraryUpdateStatusData(
    val libraryUpdateStatus: SuwayomiLibraryUpdateStatusDto,
)

@Serializable
data class SuwayomiLibraryUpdateStatusDto(
    val categoryUpdates: List<SuwayomiCategoryUpdateDto> = emptyList(),
    val mangaUpdates: List<SuwayomiMangaUpdateDto> = emptyList(),
    val jobsInfo: SuwayomiUpdaterJobsInfoDto = SuwayomiUpdaterJobsInfoDto(),
)

@Serializable
data class SuwayomiCategoryUpdateDto(
    val category: SuwayomiCategoryDto,
    val status: String,
)

@Serializable
data class SuwayomiMangaUpdateDto(
    val manga: SuwayomiMangaDto,
    val status: String,
)

@Serializable
data class SuwayomiUpdaterJobsInfoDto(
    val isRunning: Boolean = false,
    val totalJobs: Int = 0,
    val finishedJobs: Int = 0,
    val skippedCategoriesCount: Int = 0,
    val skippedMangasCount: Int = 0,
)

@Serializable
data class LibraryUpdateStatusChangedData(
    val libraryUpdateStatusChanged: SuwayomiLibraryUpdateUpdatesDto,
)

@Serializable
data class SuwayomiLibraryUpdateUpdatesDto(
    val categoryUpdates: List<SuwayomiCategoryUpdateDto> = emptyList(),
    val mangaUpdates: List<SuwayomiMangaUpdateDto> = emptyList(),
    val initial: SuwayomiLibraryUpdateStatusDto? = null,
    val jobsInfo: SuwayomiUpdaterJobsInfoDto = SuwayomiUpdaterJobsInfoDto(),
    val omittedUpdates: Boolean = false,
)

@Serializable
data class SuwayomiSourceDto(
    val baseUrl: String? = null,
    val contentWarning: String? = null,
    val displayName: String,
    val homeUrl: String? = null,
    val iconUrl: String? = null,
    val id: String,
    val isConfigurable: Boolean = false,
    val isNsfw: Boolean = false,
    val lang: String,
    val name: String,
    val supportsLatest: Boolean = false,
)

fun SuwayomiSourceDto.isLocalFolderSource(): Boolean {
    return id.equals("local", ignoreCase = true) ||
        id == "0" ||
        lang.equals("localsourcelang", ignoreCase = true) ||
        name.equals("LocalSource", ignoreCase = true) ||
        name.equals("Local source", ignoreCase = true) ||
        displayName.equals("Local source", ignoreCase = true)
}

fun SuwayomiSourceDto.hasNsfwContent(): Boolean {
    return contentWarning.hasNsfwContent(fallback = isNsfw)
}

fun SuwayomiSourceDto.webUrl(): String? {
    return homeUrl.takeUnless(String?::isNullOrBlank)
        ?: baseUrl.takeUnless(String?::isNullOrBlank)
}

@Serializable
data class SourceDetailsData(
    val source: SuwayomiSourceDetailsDto,
)

@Serializable
data class SuwayomiSourceDetailsDto(
    val baseUrl: String? = null,
    val contentWarning: String? = null,
    val displayName: String,
    val homeUrl: String? = null,
    val iconUrl: String? = null,
    val id: String,
    val isConfigurable: Boolean = false,
    val isNsfw: Boolean = false,
    val lang: String,
    val name: String,
    val supportsLatest: Boolean = false,
    val filters: List<SuwayomiSourceFilterDto> = emptyList(),
    val preferences: List<SuwayomiSourcePreferenceDto> = emptyList(),
)

@Serializable
data class SuwayomiSourceFilterDto(
    @SerialName("__typename")
    val type: String,
    val name: String,
    val defaultBoolean: Boolean? = null,
    val defaultInt: Int? = null,
    val defaultSort: SuwayomiSortSelectionDto? = null,
    val defaultString: String? = null,
    val defaultTriState: SuwayomiTriState? = null,
    val values: List<String> = emptyList(),
    val filters: List<SuwayomiSourceFilterDto> = emptyList(),
)

@Serializable
data class SuwayomiSortSelectionDto(
    val index: Int,
    val ascending: Boolean,
)

@Serializable
enum class SuwayomiTriState {
    IGNORE,
    INCLUDE,
    EXCLUDE,
}

data class SuwayomiSourceFilterChange(
    val position: Int,
    val checkBoxState: Boolean? = null,
    val groupChange: SuwayomiSourceFilterChange? = null,
    val selectState: Int? = null,
    val sortState: SuwayomiSortSelectionDto? = null,
    val textState: String? = null,
    val triState: SuwayomiTriState? = null,
)

@Serializable
data class SuwayomiSourcePreferenceDto(
    @SerialName("__typename")
    val type: String,
    val key: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val currentBoolean: Boolean? = null,
    val currentString: String? = null,
    val currentStringList: List<String>? = null,
    val defaultBoolean: Boolean? = null,
    val defaultString: String? = null,
    val defaultStringList: List<String>? = null,
    val entries: List<String> = emptyList(),
    val entryValues: List<String> = emptyList(),
    val text: String? = null,
    val dialogTitle: String? = null,
    val dialogMessage: String? = null,
)

data class SuwayomiSourcePreferenceChange(
    val position: Int,
    val checkBoxState: Boolean? = null,
    val editTextState: String? = null,
    val listState: String? = null,
    val multiSelectState: List<String>? = null,
    val switchState: Boolean? = null,
)

@Serializable
data class UpdateSourcePreferenceData(
    val updateSourcePreference: UpdateSourcePreferencePayload,
)

@Serializable
data class UpdateSourcePreferencePayload(
    val preferences: List<SuwayomiSourcePreferenceDto>,
)

@Serializable
data class FetchSourceMangaData(
    val fetchSourceManga: SourceMangaPageDto? = null,
)

@Serializable
data class SourceMangaPageDto(
    val hasNextPage: Boolean,
    val mangas: List<SuwayomiMangaDto>,
)

@Serializable
data class FetchExtensionsData(
    val fetchExtensions: FetchExtensionsPayload,
)

@Serializable
data class FetchExtensionsPayload(
    val extensions: List<SuwayomiExtensionDto>,
    val extensionStores: List<SuwayomiExtensionStoreDto> = emptyList(),
)

@Serializable
data class ExtensionStoresData(
    val extensionStores: ExtensionStoreNodeList,
)

@Serializable
data class AddExtensionStoreData(
    val addExtensionStore: ExtensionStorePayload,
)

@Serializable
data class RemoveExtensionStoreData(
    val removeExtensionStore: NullableExtensionStorePayload,
)

@Serializable
data class ExtensionStorePayload(
    val extensionStore: SuwayomiExtensionStoreDto,
)

@Serializable
data class NullableExtensionStorePayload(
    val extensionStore: SuwayomiExtensionStoreDto? = null,
)

@Serializable
data class UpdateExtensionData(
    val updateExtension: UpdateExtensionPayload,
)

@Serializable
data class UpdateExtensionPayload(
    val extension: SuwayomiExtensionDto? = null,
)

@Serializable
data class UpdateExtensionsData(
    val updateExtensions: UpdateExtensionsPayload,
)

@Serializable
data class UpdateExtensionsPayload(
    val extensions: List<SuwayomiExtensionDto>,
)

@Serializable
data class ExtensionNodeList(
    val nodes: List<SuwayomiExtensionDto>,
)

@Serializable
data class SuwayomiExtensionDto(
    val apkName: String? = null,
    val apkUrl: String? = null,
    val contentWarning: String? = null,
    val extensionLib: String? = null,
    val extensionStore: SuwayomiExtensionStoreDto? = null,
    val hasUpdate: Boolean = false,
    val iconUrl: String? = null,
    val isInstalled: Boolean = false,
    val isNsfw: Boolean = false,
    val isObsolete: Boolean = false,
    val lang: String,
    val name: String,
    val pkgName: String,
    val repo: String? = null,
    val source: SourceNodeList? = SourceNodeList(emptyList()),
    val storeIndexUrl: String? = null,
    val versionCode: Long = 0,
    val versionCodeLong: String? = null,
    val versionName: String = "",
)

fun SuwayomiExtensionDto.currentVersionCode(): Long {
    return versionCodeLong?.toLongOrNull() ?: versionCode
}

fun SuwayomiExtensionDto.currentLibVersion(): Double {
    return extensionLib?.toDoubleOrNull() ?: 0.0
}

fun SuwayomiExtensionDto.hasNsfwContent(): Boolean {
    return contentWarning.hasNsfwContent(fallback = isNsfw)
}

fun SuwayomiExtensionDto.sourceNodes(): List<SuwayomiSourceDto> {
    return source?.nodes.orEmpty()
}

private fun String?.hasNsfwContent(fallback: Boolean): Boolean {
    return when (this?.uppercase()) {
        "SAFE" -> false
        "MIXED", "NSFW" -> true
        "UNSPECIFIED", "CONTENT_WARNING_UNSPECIFIED", null -> fallback
        else -> true
    }
}

@Serializable
data class ExtensionStoreNodeList(
    val nodes: List<SuwayomiExtensionStoreDto>,
)

@Serializable
data class SuwayomiExtensionStoreDto(
    val name: String,
    val badgeLabel: String = "",
    val signingKey: String = "",
    val contactWebsite: String = "",
    val contactDiscord: String? = null,
    val indexUrl: String,
    val isLegacy: Boolean = false,
    val extensionListUrl: String? = null,
    val extensions: ExtensionNodeList = ExtensionNodeList(emptyList()),
)

@Serializable
data class LastSyncStatusData(
    val lastSyncStatus: SuwayomiSyncStatusDto? = null,
)

@Serializable
data class StartSyncData(
    val startSync: StartSyncPayload,
)

@Serializable
data class StartSyncPayload(
    val result: String,
)

@Serializable
data class SyncStatusChangedData(
    val syncStatusChanged: SuwayomiSyncStatusDto,
)

@Serializable
data class SuwayomiSyncStatusDto(
    val state: String,
    val startDate: String,
    val endDate: String? = null,
    val backupRestoreId: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class GetMangaData(
    val manga: SuwayomiMangaDto,
)

@Serializable
data class GetMangaTrackSummaryData(
    val manga: SuwayomiMangaTrackSummaryDto,
)

@Serializable
data class SuwayomiMangaTrackSummaryDto(
    val description: String? = null,
    val id: Int,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val title: String,
    val chapters: SuwayomiTrackSummaryChapterCountDto,
    val latestReadChapter: SuwayomiTrackSummaryLatestReadChapterDto? = null,
    val unreadCount: Long = 0,
)

@Serializable
data class SuwayomiTrackSummaryChapterCountDto(
    val totalCount: Long = 0,
)

@Serializable
data class SuwayomiTrackSummaryLatestReadChapterDto(
    val chapterNumber: Double = 0.0,
)

@Serializable
data class UpdateMangaData(
    val updateManga: UpdateMangaPayload,
)

@Serializable
data class UpdateMangaPayload(
    val manga: SuwayomiMangaDto,
)

@Serializable
data class SetMangaMetaData(
    val setMangaMeta: SetMangaMetaPayload,
)

@Serializable
data class SetMangaMetaPayload(
    val meta: SuwayomiMangaMetaDto,
)

@Serializable
data class SuwayomiMangaMetaDto(
    val key: String,
    val mangaId: Int,
    val value: String,
)

@Serializable
data class FetchChaptersData(
    val fetchChapters: FetchChaptersPayload? = null,
)

@Serializable
data class FetchChaptersPayload(
    val chapters: List<SuwayomiChapterDto>,
)

@Serializable
data class FetchMangaAndChaptersData(
    val fetchMangaAndChapters: FetchMangaAndChaptersPayload? = null,
)

@Serializable
data class FetchMangaAndChaptersPayload(
    val manga: SuwayomiMangaDto,
    val chapters: List<SuwayomiChapterDto>,
)

@Serializable
data class ChaptersData(
    val chapters: ChapterNodeList,
)

@Serializable
data class ChapterNodeList(
    val nodes: List<SuwayomiChapterDto>,
)

@Serializable
data class FetchChapterPagesData(
    val fetchChapterPages: FetchChapterPagesPayload? = null,
)

@Serializable
data class FetchChapterPagesPayload(
    val pages: List<String>,
    val chapter: SuwayomiChapterDto? = null,
)

data class SuwayomiChapterPageManifest(
    val pages: List<String>,
    val chapter: SuwayomiChapterDto,
)

@Serializable
data class UpdateChapterData(
    val updateChapter: UpdateChapterPayload,
)

@Serializable
data class UpdateChapterPayload(
    val chapter: SuwayomiChapterDto,
)

@Serializable
data class UpdateChaptersData(
    val updateChapters: UpdateChaptersPayload,
)

@Serializable
data class UpdateChaptersPayload(
    val chapters: List<SuwayomiChapterDto>,
)

@Serializable
data class DownloadStatusData(
    val downloadStatus: SuwayomiDownloadStatusDto,
)

@Serializable
data class DownloadStatusChangedData(
    val downloadStatusChanged: SuwayomiDownloadUpdatesDto,
)

@Serializable
data class SuwayomiDownloadUpdatesDto(
    val state: String,
    val omittedUpdates: Boolean = false,
    val updates: List<SuwayomiDownloadUpdateDto> = emptyList(),
    val initial: List<SuwayomiDownloadDto>? = null,
)

@Serializable
data class SuwayomiDownloadUpdateDto(
    val type: SuwayomiDownloadUpdateType,
    val download: SuwayomiDownloadDto,
)

@Serializable
enum class SuwayomiDownloadUpdateType {
    QUEUED,
    DEQUEUED,
    PAUSED,
    STOPPED,
    PROGRESS,
    FINISHED,
    ERROR,
    POSITION,
}

@Serializable
data class ReorderChapterDownloadData(
    val reorderChapterDownload: ReorderChapterDownloadPayload,
)

@Serializable
data class ReorderChapterDownloadPayload(
    val downloadStatus: SuwayomiDownloadStatusDto,
)

@Serializable
data class DequeueChapterDownloadsData(
    val dequeueChapterDownloads: DequeueChapterDownloadsPayload,
)

@Serializable
data class DequeueChapterDownloadsPayload(
    val downloadStatus: SuwayomiDownloadStatusDto,
)

@Serializable
data class DeleteDownloadedChaptersData(
    val deleteDownloadedChapters: DeleteDownloadedChaptersPayload,
)

@Serializable
data class DeleteDownloadedChaptersPayload(
    val chapters: List<SuwayomiChapterDto>,
)

@Serializable
data class EmptyMutationData(
    val enqueueChapterDownloads: EmptyMutationPayload? = null,
    val dequeueChapterDownload: EmptyMutationPayload? = null,
    val startDownloader: EmptyMutationPayload? = null,
    val stopDownloader: EmptyMutationPayload? = null,
    val clearDownloader: EmptyMutationPayload? = null,
    val trackProgress: EmptyMutationPayload? = null,
    val updateStop: EmptyMutationPayload? = null,
)

@Serializable
data class EmptyMutationPayload(
    @SerialName("__typename")
    val typename: String? = null,
)

@Serializable
data class SuwayomiDownloadStatusDto(
    val state: String,
    val queue: List<SuwayomiDownloadDto> = emptyList(),
)

@Serializable
data class SuwayomiDownloadDto(
    val chapter: SuwayomiDownloadChapterDto,
    val manga: SuwayomiDownloadMangaDto,
    val progress: Double = 0.0,
    val state: String,
    val tries: Int = 0,
    val position: Int = 0,
)

@Serializable
data class SuwayomiDownloadChapterDto(
    val id: Int,
    val name: String,
    val chapterNumber: Float = -1f,
    val uploadDate: Long = 0L,
    val sourceOrder: Int = 0,
    val isDownloaded: Boolean = false,
)

@Serializable
data class SuwayomiDownloadMangaDto(
    val id: Int,
    val title: String,
    val downloadCount: Int = 0,
    val thumbnailUrl: String? = null,
)

@Serializable
data class ServerSettingsData(
    val settings: SuwayomiServerSettingsDto,
)

@Serializable
data class SetSettingsData(
    val setSettings: SetSettingsPayload,
)

@Serializable
data class SetSettingsPayload(
    val settings: SuwayomiServerSettingsDto,
)

@Serializable
data class ServerAboutData(
    val aboutServer: SuwayomiServerAboutDto,
)

@Serializable
data class WebUiAboutData(
    val aboutWebUI: SuwayomiWebUiAboutDto,
)

@Serializable
data class SuwayomiServerAboutDto(
    val name: String,
    val version: String,
    val revision: String = "",
    val buildType: String = "",
    val buildTime: String = "",
    val github: String = "",
    val discord: String = "",
)

@Serializable
data class SuwayomiWebUiAboutDto(
    val channel: String,
    val tag: String,
    val updateTimestamp: String = "",
)

@Serializable
data class SuwayomiServerSettingsDto(
    val authMode: String = "NONE",
    val authPassword: String = "",
    val authUsername: String = "",
    val autoDownloadIgnoreReUploads: Boolean = false,
    val autoDownloadNewChapters: Boolean = false,
    val autoDownloadNewChaptersLimit: Int = 0,
    val autoBackupIncludeCategories: Boolean = false,
    val autoBackupIncludeChapters: Boolean = false,
    val autoBackupIncludeClientData: Boolean = false,
    val autoBackupIncludeHistory: Boolean = false,
    val autoBackupIncludeManga: Boolean = false,
    val autoBackupIncludeServerSettings: Boolean = false,
    val autoBackupIncludeTracking: Boolean = false,
    val backupInterval: Int = 0,
    val backupPath: String = "",
    val backupTTL: Int = 0,
    val backupTime: String = "",
    val basicAuthEnabled: Boolean = false,
    val basicAuthPassword: String = "",
    val basicAuthUsername: String = "",
    val ip: String? = null,
    val port: Int = 4567,
    val downloadAsCbz: Boolean = false,
    val downloadsPath: String = "",
    val electronPath: String = "",
    val excludeCompleted: Boolean = false,
    val excludeEntryWithUnreadChapters: Boolean = false,
    val excludeNotStarted: Boolean = false,
    val excludeUnreadChapters: Boolean = false,
    val extensionRepos: List<String> = emptyList(),
    val flareSolverrAsResponseFallback: Boolean = false,
    val globalUpdateInterval: Double = 12.0,
    val initialOpenInBrowserEnabled: Boolean = false,
    val localSourcePath: String = "",
    val maxLogFileSize: String = "",
    val maxLogFiles: Int = 0,
    val maxLogFolderSize: String = "",
    val maxSourcesInParallel: Int = 0,
    val socksProxyEnabled: Boolean = false,
    val socksProxyHost: String = "",
    val socksProxyPassword: String = "",
    val socksProxyPort: String = "",
    val socksProxyUsername: String = "",
    val socksProxyVersion: Int = 5,
    val flareSolverrEnabled: Boolean = false,
    val flareSolverrSessionName: String = "",
    val flareSolverrSessionTtl: Int = 15,
    val flareSolverrTimeout: Int = 60,
    val flareSolverrUrl: String = "",
    val debugLogsEnabled: Boolean = false,
    val systemTrayEnabled: Boolean = false,
    val updateMangas: Boolean = false,
    val webUIChannel: String = "BUNDLED",
    val webUIFlavor: String = "WEBUI",
    val webUIInterface: String = "BROWSER",
    val webUIUpdateCheckInterval: Double = 0.0,
)

@Serializable
data class SuwayomiMangaDto(
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val downloadCount: Int = 0,
    val genre: List<String> = emptyList(),
    val id: Int,
    val inLibrary: Boolean = false,
    val inLibraryAt: Long? = null,
    val initialized: Boolean = false,
    val chaptersLastFetchedAt: Long? = null,
    val lastFetchedAt: Long? = null,
    val latestFetchedChapter: SuwayomiLatestFetchedChapterDto? = null,
    val latestReadChapter: SuwayomiLatestReadChapterDto? = null,
    val latestUploadedChapter: SuwayomiLatestUploadedChapterDto? = null,
    val meta: List<SuwayomiMangaMetaDto> = emptyList(),
    val realUrl: String? = null,
    val sourceId: String,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val title: String,
    val unreadCount: Long = 0,
    val updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    val url: String,
)

@Serializable
data class SuwayomiLatestFetchedChapterDto(
    val fetchedAt: Long = 0L,
)

@Serializable
data class SuwayomiLatestReadChapterDto(
    val lastReadAt: Long = 0L,
)

@Serializable
data class SuwayomiLatestUploadedChapterDto(
    val uploadDate: Long = 0L,
)

@Serializable
data class SuwayomiChapterDto(
    val chapterNumber: Float = -1f,
    val fetchedAt: String = "0",
    val id: Int,
    val isDownloaded: Boolean = false,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val lastPageRead: Int = 0,
    val lastReadAt: Long = 0L,
    val mangaId: Int,
    val name: String,
    val scanlator: String? = null,
    val sourceOrder: Int = 0,
    val uploadDate: Long = 0,
    val url: String,
    val realUrl: String? = null,
    val pageCount: Int = 0,
)

@Serializable
data class SuwayomiChapterWithMangaDto(
    val chapterNumber: Float = -1f,
    val fetchedAt: String = "0",
    val id: Int,
    val isDownloaded: Boolean = false,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val lastPageRead: Int = 0,
    val lastReadAt: Long = 0L,
    val manga: SuwayomiMangaDto,
    val mangaId: Int,
    val name: String,
    val scanlator: String? = null,
    val sourceOrder: Int = 0,
    val uploadDate: Long = 0,
    val url: String,
)

@Serializable
enum class MangaStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS,
}

@Serializable
enum class UpdateStrategy {
    @SerialName("ALWAYS_UPDATE")
    ALWAYS_UPDATE,

    @SerialName("ONLY_FETCH_ONCE")
    ONLY_FETCH_ONCE,
}
