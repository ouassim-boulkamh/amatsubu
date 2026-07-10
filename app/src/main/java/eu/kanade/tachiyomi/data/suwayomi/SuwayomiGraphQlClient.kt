package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.data.suwayomi.generated.LoginTrackerCredentialsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LoginMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LoginTrackerOAuthMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LogoutTrackerMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.BindTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.BindTrackRecordMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.AllCategoriesQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.CreateCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchExtensionListMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaCategoriesQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ExtensionStoresQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.AddExtensionStoreMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.RemoveExtensionStoreMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.RefreshTokenMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateExtensionMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateExtensionsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SearchTrackerQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SourceListQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SourceDetailsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchSourceMangaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateSourcePreferenceMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackProgressMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackRecordsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackerListQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UnbindTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryOrderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangaCategoriesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetNamedCategoryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetCategoryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryMangasQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetLibraryTrackingDataQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetRecentChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetReadingHistoryQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetReadingHistoryIdsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchMangaAndChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetMangaTrackSummaryQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetCachedChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchChapterPagesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateReaderChapterMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateReaderChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetServerStatsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangasCategoriesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateMangaLibraryMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateLibraryMangasMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryMangasMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LibraryUpdateStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.StopLibraryUpdateMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LastSyncStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.StartSyncMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetGlobalMetaQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SetGlobalMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteGlobalMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.ClearCachedImagesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SetMangaMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.ClearDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteDownloadedChaptersMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DequeueChapterDownloadMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DequeueChapterDownloadsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.EnqueueChapterDownloadsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetDownloadStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ReorderChapterDownloadMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StartDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StopDownloaderMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.ServerAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ServerSettingsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.TestConnectionSettingsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SetSettingsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.WebUiAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuTrackRecord
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuTrackSearch
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtension
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtensionSource
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtensionStore
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuCategory
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuLibraryChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuLibraryManga
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuManga
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuReaderChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuDownload
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuDownloadStatus
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourceFilter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourceFilterChild
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourcePreference
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuServerSettings
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginTrackerCredentialsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginTrackerOAuthInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LogoutTrackerInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.BindTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.BindTrackRecordInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SearchTrackerInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.TrackProgressInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UnbindTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.AddExtensionStoreInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.CreateCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.IncludeOrExclude
import eu.kanade.tachiyomi.data.suwayomi.generated.type.RemoveExtensionStoreInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.RefreshTokenInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryOrderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaCategoriesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaCategoriesPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchMangaAndChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchChapterPagesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChapterInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChapterPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchSourceMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchSourceMangaType as GeneratedFetchSourceMangaType
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FilterChangeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SortSelectionInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SourcePreferenceChangeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.TriState
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateSourcePreferenceInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangasCategoriesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateMangaPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateLibraryMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateStopInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StartSyncInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.GlobalMetaTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SetGlobalMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteGlobalMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ClearCachedImagesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SetMangaMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.MangaMetaTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ClearDownloaderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteDownloadedChaptersInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DequeueChapterDownloadInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DequeueChapterDownloadsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.EnqueueChapterDownloadsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ReorderChapterDownloadInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StartDownloaderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StopDownloaderInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.AuthMode
import eu.kanade.tachiyomi.data.suwayomi.generated.type.PartialSettingsTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIChannel
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIFlavor
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIInterface
import com.apollographql.apollo.api.Optional
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

private fun AmatsubuExtension.toSuwayomiDto() = SuwayomiExtensionDto(
    apkName = apkName,
    apkUrl = apkUrl,
    contentWarning = contentWarning.rawValue,
    extensionLib = extensionLib,
    extensionStore = extensionStore?.amatsubuExtensionStore?.toSuwayomiDto(),
    hasUpdate = hasUpdate,
    iconUrl = iconUrl,
    isInstalled = isInstalled,
    isNsfw = isNsfw,
    isObsolete = isObsolete,
    lang = lang,
    name = name,
    pkgName = pkgName,
    repo = repo,
    source = SourceNodeList(source.nodes.map { it.amatsubuExtensionSource.toSuwayomiDto() }),
    storeIndexUrl = storeIndexUrl,
    versionCode = versionCode.toLong(),
    versionCodeLong = versionCodeLong.toString(),
    versionName = versionName,
)

private fun AmatsubuExtensionStore.toSuwayomiDto() = SuwayomiExtensionStoreDto(
    name = name,
    badgeLabel = badgeLabel,
    signingKey = signingKey,
    contactWebsite = contactWebsite,
    contactDiscord = contactDiscord,
    indexUrl = indexUrl,
    isLegacy = isLegacy,
    extensionListUrl = extensionListUrl,
)

private fun AmatsubuCategory.toSuwayomiDto() = SuwayomiCategoryDto(
    id = id,
    name = name,
    order = order,
    includeInDownload = SuwayomiCategoryFlag.valueOf(includeInDownload.rawValue),
    includeInUpdate = SuwayomiCategoryFlag.valueOf(includeInUpdate.rawValue),
)

private fun AmatsubuExtensionSource.toSuwayomiDto() = SuwayomiSourceDto(
    baseUrl = baseUrl,
    contentWarning = contentWarning.rawValue,
    displayName = displayName,
    homeUrl = homeUrl,
    iconUrl = iconUrl,
    id = id.toString(),
    isConfigurable = isConfigurable,
    isNsfw = isNsfw,
    lang = lang,
    name = name,
    supportsLatest = supportsLatest,
)

private fun Any?.asLongOrNull() = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

private fun AmatsubuLibraryManga.toSuwayomiDto() = SuwayomiMangaDto(
    artist = artist,
    author = author,
    description = description,
    downloadCount = downloadCount,
    genre = genre,
    id = id,
    inLibrary = inLibrary,
    inLibraryAt = inLibraryAt.asLongOrNull(),
    initialized = initialized,
    chaptersLastFetchedAt = chaptersLastFetchedAt.asLongOrNull(),
    lastFetchedAt = lastFetchedAt.asLongOrNull(),
    latestFetchedChapter = latestFetchedChapter?.let { SuwayomiLatestFetchedChapterDto(it.fetchedAt.asLongOrNull() ?: 0L) },
    latestReadChapter = latestReadChapter?.let { SuwayomiLatestReadChapterDto(it.lastReadAt.asLongOrNull() ?: 0L) },
    latestUploadedChapter = latestUploadedChapter?.let { SuwayomiLatestUploadedChapterDto(it.uploadDate.asLongOrNull() ?: 0L) },
    meta = meta.map { SuwayomiMangaMetaDto(it.key, it.mangaId, it.value) },
    realUrl = realUrl,
    sourceId = sourceId.toString(),
    status = MangaStatus.valueOf(status.rawValue),
    thumbnailUrl = thumbnailUrl,
    title = title,
    unreadCount = unreadCount.toLong(),
    updateStrategy = UpdateStrategy.valueOf(updateStrategy.rawValue),
    url = url,
)

private fun AmatsubuManga.toSuwayomiDto() = SuwayomiMangaDto(
    artist = artist,
    author = author,
    description = description,
    downloadCount = downloadCount,
    genre = genre,
    id = id,
    inLibrary = inLibrary,
    inLibraryAt = inLibraryAt.asLongOrNull(),
    initialized = initialized,
    chaptersLastFetchedAt = chaptersLastFetchedAt.asLongOrNull(),
    lastFetchedAt = lastFetchedAt.asLongOrNull(),
    latestFetchedChapter = latestFetchedChapter?.let { SuwayomiLatestFetchedChapterDto(it.fetchedAt.asLongOrNull() ?: 0L) },
    latestReadChapter = latestReadChapter?.let { SuwayomiLatestReadChapterDto(it.lastReadAt.asLongOrNull() ?: 0L) },
    latestUploadedChapter = latestUploadedChapter?.let { SuwayomiLatestUploadedChapterDto(it.uploadDate.asLongOrNull() ?: 0L) },
    meta = meta.map { SuwayomiMangaMetaDto(it.key, it.mangaId, it.value) },
    realUrl = realUrl,
    sourceId = sourceId.toString(),
    status = MangaStatus.valueOf(status.rawValue),
    thumbnailUrl = thumbnailUrl,
    title = title,
    unreadCount = unreadCount.toLong(),
    updateStrategy = UpdateStrategy.valueOf(updateStrategy.rawValue),
    url = url,
)

private fun AmatsubuLibraryChapter.toSuwayomiDto() = SuwayomiChapterDto(
    chapterNumber = chapterNumber.toFloat(),
    fetchedAt = fetchedAt.toString(),
    id = id,
    isDownloaded = isDownloaded,
    isBookmarked = isBookmarked,
    isRead = isRead,
    lastPageRead = lastPageRead,
    lastReadAt = lastReadAt.asLongOrNull() ?: 0L,
    mangaId = mangaId,
    name = name,
    realUrl = realUrl,
    scanlator = scanlator,
    sourceOrder = sourceOrder,
    uploadDate = uploadDate.asLongOrNull() ?: 0L,
    url = url,
)

private fun AmatsubuLibraryChapter.toSuwayomiDto(manga: AmatsubuLibraryManga) = SuwayomiChapterWithMangaDto(
    chapterNumber = chapterNumber.toFloat(),
    fetchedAt = fetchedAt.toString(),
    id = id,
    isDownloaded = isDownloaded,
    isBookmarked = isBookmarked,
    isRead = isRead,
    lastPageRead = lastPageRead,
    lastReadAt = lastReadAt.asLongOrNull() ?: 0L,
    manga = manga.toSuwayomiDto(),
    mangaId = mangaId,
    name = name,
    scanlator = scanlator,
    sourceOrder = sourceOrder,
    uploadDate = uploadDate.asLongOrNull() ?: 0L,
    url = url,
)

private fun GetRecentChaptersQuery.Node.toSuwayomiDto() =
    amatsubuLibraryChapter.toSuwayomiDto(manga.amatsubuLibraryManga)

private fun GetReadingHistoryQuery.Node.toSuwayomiDto() =
    amatsubuLibraryChapter.toSuwayomiDto(manga.amatsubuLibraryManga)

private fun AmatsubuChapter.toSuwayomiDto() = SuwayomiChapterDto(
    chapterNumber = chapterNumber.toFloat(),
    fetchedAt = fetchedAt.toString(),
    id = id,
    isDownloaded = isDownloaded,
    isBookmarked = isBookmarked,
    isRead = isRead,
    lastPageRead = lastPageRead,
    lastReadAt = lastReadAt.asLongOrNull() ?: 0L,
    mangaId = mangaId,
    name = name,
    realUrl = realUrl,
    scanlator = scanlator,
    sourceOrder = sourceOrder,
    uploadDate = uploadDate.asLongOrNull() ?: 0L,
    url = url,
)

private fun AmatsubuDownloadStatus.toSuwayomiDto() = SuwayomiDownloadStatusDto(
    state = state.rawValue,
    queue = queue.map { it.amatsubuDownload.toSuwayomiDto() },
)

private fun AmatsubuDownload.toSuwayomiDto() = SuwayomiDownloadDto(
    chapter = SuwayomiDownloadChapterDto(
        id = chapter.id,
        name = chapter.name,
        chapterNumber = chapter.chapterNumber.toFloat(),
        uploadDate = chapter.uploadDate.asLongOrNull() ?: 0L,
        sourceOrder = chapter.sourceOrder,
        isDownloaded = chapter.isDownloaded,
    ),
    manga = SuwayomiDownloadMangaDto(
        id = manga.id,
        title = manga.title,
        downloadCount = manga.downloadCount,
        thumbnailUrl = manga.thumbnailUrl,
    ),
    progress = progress,
    state = state.rawValue,
    tries = tries,
    position = position,
)

private fun AmatsubuReaderChapter.toSuwayomiDto() = SuwayomiChapterDto(
    chapterNumber = chapterNumber.toFloat(),
    fetchedAt = fetchedAt.toString(),
    id = id,
    isDownloaded = isDownloaded,
    isBookmarked = isBookmarked,
    isRead = isRead,
    lastPageRead = lastPageRead,
    lastReadAt = lastReadAt.asLongOrNull() ?: 0L,
    mangaId = mangaId,
    name = name,
    realUrl = realUrl,
    scanlator = scanlator,
    sourceOrder = sourceOrder,
    uploadDate = uploadDate.asLongOrNull() ?: 0L,
    url = url,
)

private fun AmatsubuSourceFilter.toSuwayomiDto() = SuwayomiSourceFilterDto(
    type = __typename,
    name = onCheckBoxFilter?.name ?: onHeaderFilter?.name ?: onSeparatorFilter?.name ?: onSelectFilter?.name
        ?: onSortFilter?.name ?: onTextFilter?.name ?: onTriStateFilter?.name ?: onGroupFilter?.name.orEmpty(),
    defaultBoolean = onCheckBoxFilter?.defaultBoolean,
    defaultInt = onSelectFilter?.defaultInt,
    defaultSort = onSortFilter?.defaultSort?.let { SuwayomiSortSelectionDto(it.index, it.ascending) },
    defaultString = onTextFilter?.defaultString,
    defaultTriState = onTriStateFilter?.defaultTriState?.toSuwayomiDto(),
    values = onSelectFilter?.values ?: onSortFilter?.values.orEmpty(),
    filters = onGroupFilter?.filters?.map { it.amatsubuSourceFilterChild.toSuwayomiDto() }.orEmpty(),
)

private fun AmatsubuSourceFilterChild.toSuwayomiDto() = SuwayomiSourceFilterDto(
    type = __typename,
    name = onCheckBoxFilter?.name ?: onHeaderFilter?.name ?: onSeparatorFilter?.name ?: onSelectFilter?.name
        ?: onSortFilter?.name ?: onTextFilter?.name ?: onTriStateFilter?.name.orEmpty(),
    defaultBoolean = onCheckBoxFilter?.defaultBoolean,
    defaultInt = onSelectFilter?.defaultInt,
    defaultSort = onSortFilter?.defaultSort?.let { SuwayomiSortSelectionDto(it.index, it.ascending) },
    defaultString = onTextFilter?.defaultString,
    defaultTriState = onTriStateFilter?.defaultTriState?.toSuwayomiDto(),
    values = onSelectFilter?.values ?: onSortFilter?.values.orEmpty(),
)

private fun AmatsubuSourcePreference.toSuwayomiDto(): SuwayomiSourcePreferenceDto {
    fun base(key: String?, title: String?, summary: String?, enabled: Boolean, visible: Boolean) =
        SuwayomiSourcePreferenceDto(type = __typename, key = key, title = title, summary = summary, enabled = enabled, visible = visible)
    return onCheckBoxPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentBoolean = preference.currentBoolean, defaultBoolean = preference.defaultBoolean,
        )
    } ?: onSwitchPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentBoolean = preference.currentBoolean, defaultBoolean = preference.defaultBoolean,
        )
    } ?: onEditTextPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentString = preference.currentString, defaultString = preference.defaultString, text = preference.text,
            dialogTitle = preference.dialogTitle, dialogMessage = preference.dialogMessage,
        )
    } ?: onListPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentString = preference.currentString, defaultString = preference.defaultString,
            entries = preference.entries, entryValues = preference.entryValues,
        )
    } ?: onMultiSelectListPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentStringList = preference.currentStringList, defaultStringList = preference.defaultStringList,
            entries = preference.entries, entryValues = preference.entryValues,
            dialogTitle = preference.dialogTitle, dialogMessage = preference.dialogMessage,
        )
    } ?: base(null, null, null, true, true)
}

private fun TriState.toSuwayomiDto() = SuwayomiTriState.valueOf(rawValue)

private fun SuwayomiSourceFilterChange.toGeneratedInput(): FilterChangeInput = FilterChangeInput(
    position = position,
    checkBoxState = Optional.presentIfNotNull(checkBoxState),
    groupChange = Optional.presentIfNotNull(groupChange?.toGeneratedInput()),
    selectState = Optional.presentIfNotNull(selectState),
    sortState = Optional.presentIfNotNull(sortState?.let { SortSelectionInput(ascending = it.ascending, index = it.index) }),
    textState = Optional.presentIfNotNull(textState),
    triState = Optional.presentIfNotNull(triState?.let { TriState.valueOf(it.name) }),
)

private fun SuwayomiSourcePreferenceChange.toGeneratedInput() = SourcePreferenceChangeInput(
    position = position,
    checkBoxState = Optional.presentIfNotNull(checkBoxState),
    editTextState = Optional.presentIfNotNull(editTextState),
    listState = Optional.presentIfNotNull(listState),
    multiSelectState = Optional.presentIfNotNull(multiSelectState),
    switchState = Optional.presentIfNotNull(switchState),
)

private fun JsonObject.toGeneratedInput() = PartialSettingsTypeInput(
    authMode = optionalString("authMode") { AuthMode.safeValueOf(it) },
    authPassword = optionalString("authPassword"),
    authUsername = optionalString("authUsername"),
    autoBackupIncludeCategories = optionalBoolean("autoBackupIncludeCategories"),
    autoBackupIncludeChapters = optionalBoolean("autoBackupIncludeChapters"),
    autoBackupIncludeClientData = optionalBoolean("autoBackupIncludeClientData"),
    autoBackupIncludeHistory = optionalBoolean("autoBackupIncludeHistory"),
    autoBackupIncludeManga = optionalBoolean("autoBackupIncludeManga"),
    autoBackupIncludeServerSettings = optionalBoolean("autoBackupIncludeServerSettings"),
    autoBackupIncludeTracking = optionalBoolean("autoBackupIncludeTracking"),
    autoDownloadIgnoreReUploads = optionalBoolean("autoDownloadIgnoreReUploads"),
    autoDownloadNewChapters = optionalBoolean("autoDownloadNewChapters"),
    autoDownloadNewChaptersLimit = optionalInt("autoDownloadNewChaptersLimit"),
    backupInterval = optionalInt("backupInterval"),
    backupPath = optionalString("backupPath"),
    backupTTL = optionalInt("backupTTL"),
    backupTime = optionalString("backupTime"),
    debugLogsEnabled = optionalBoolean("debugLogsEnabled"),
    downloadAsCbz = optionalBoolean("downloadAsCbz"),
    downloadsPath = optionalString("downloadsPath"),
    electronPath = optionalString("electronPath"),
    excludeCompleted = optionalBoolean("excludeCompleted"),
    excludeEntryWithUnreadChapters = optionalBoolean("excludeEntryWithUnreadChapters"),
    excludeNotStarted = optionalBoolean("excludeNotStarted"),
    excludeUnreadChapters = optionalBoolean("excludeUnreadChapters"),
    extensionRepos = this["extensionRepos"]?.jsonArray?.map { it.jsonPrimitive.content }?.let { Optional.present(it) } ?: Optional.Absent,
    flareSolverrAsResponseFallback = optionalBoolean("flareSolverrAsResponseFallback"),
    flareSolverrEnabled = optionalBoolean("flareSolverrEnabled"),
    flareSolverrSessionName = optionalString("flareSolverrSessionName"),
    flareSolverrSessionTtl = optionalInt("flareSolverrSessionTtl"),
    flareSolverrTimeout = optionalInt("flareSolverrTimeout"),
    flareSolverrUrl = optionalString("flareSolverrUrl"),
    globalUpdateInterval = optionalDouble("globalUpdateInterval"),
    initialOpenInBrowserEnabled = optionalBoolean("initialOpenInBrowserEnabled"),
    ip = optionalString("ip"),
    localSourcePath = optionalString("localSourcePath"),
    maxLogFileSize = optionalString("maxLogFileSize"),
    maxLogFiles = optionalInt("maxLogFiles"),
    maxLogFolderSize = optionalString("maxLogFolderSize"),
    maxSourcesInParallel = optionalInt("maxSourcesInParallel"),
    port = optionalInt("port"),
    socksProxyEnabled = optionalBoolean("socksProxyEnabled"),
    socksProxyHost = optionalString("socksProxyHost"),
    socksProxyPassword = optionalString("socksProxyPassword"),
    socksProxyPort = optionalString("socksProxyPort"),
    socksProxyUsername = optionalString("socksProxyUsername"),
    socksProxyVersion = optionalInt("socksProxyVersion"),
    systemTrayEnabled = optionalBoolean("systemTrayEnabled"),
    updateMangas = optionalBoolean("updateMangas"),
    webUIChannel = optionalString("webUIChannel") { WebUIChannel.safeValueOf(it) },
    webUIFlavor = optionalString("webUIFlavor") { WebUIFlavor.safeValueOf(it) },
    webUIInterface = optionalString("webUIInterface") { WebUIInterface.safeValueOf(it) },
    webUIUpdateCheckInterval = optionalDouble("webUIUpdateCheckInterval"),
)

private fun JsonObject.optionalBoolean(key: String): Optional<Boolean?> =
    this[key]?.jsonPrimitive?.boolean?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalInt(key: String): Optional<Int?> =
    this[key]?.jsonPrimitive?.int?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalDouble(key: String): Optional<Double?> =
    this[key]?.jsonPrimitive?.double?.let { Optional.present(it) } ?: Optional.Absent

private fun <T> JsonObject.optionalString(key: String, transform: (String) -> T): Optional<T?> =
    this[key]?.jsonPrimitive?.content?.let(transform)?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalString(key: String): Optional<String?> = optionalString(key) { it }

private fun GetServerStatsQuery.Data.toServerStatsData() = ServerStatsData(
    statisticsMangas = ServerStatsMangaNodeList(
        totalCount = statisticsMangas.totalCount,
        nodes = statisticsMangas.nodes.map { manga ->
            SuwayomiStatsMangaDto(
                id = manga.id,
                status = MangaStatus.valueOf(manga.status.rawValue),
                unreadCount = manga.unreadCount.toLong(),
                downloadCount = manga.downloadCount,
                initialized = manga.initialized,
                latestReadChapter = manga.latestReadChapter?.let { SuwayomiStatsLatestReadChapterDto(it.lastReadAt.toString()) },
                sourceId = manga.sourceId.toString(),
                updateStrategy = UpdateStrategy.valueOf(manga.updateStrategy.rawValue),
                categories = StatsCategoryNodeList(manga.categories.nodes.map {
                    SuwayomiStatsCategoryDto(it.id, SuwayomiCategoryFlag.valueOf(it.includeInUpdate.rawValue))
                }),
                trackRecords = StatsTrackRecordNodeList(manga.trackRecords.nodes.map { SuwayomiStatsTrackRecordDto(it.trackerId, it.score) }),
            )
        },
    ),
    totalChapters = CountNodeList(totalChapters.totalCount),
    readChapters = CountNodeList(readChapters.totalCount),
    downloadedChapters = CountNodeList(downloadedChapters.totalCount),
    settings = SuwayomiServerSettingsDto(
        excludeCompleted = settings.excludeCompleted,
        excludeEntryWithUnreadChapters = settings.excludeEntryWithUnreadChapters,
        excludeNotStarted = settings.excludeNotStarted,
        excludeUnreadChapters = settings.excludeUnreadChapters,
    ),
    trackers = TrackerNodeList(trackers.nodes.map { SuwayomiTrackerDto(id = it.id, name = it.name, isLoggedIn = it.isLoggedIn) }),
)

private fun LibraryUpdateStatusQuery.LibraryUpdateStatus.toSuwayomiDto() = SuwayomiLibraryUpdateStatusDto(
    categoryUpdates = categoryUpdates.map { SuwayomiCategoryUpdateDto(it.category.amatsubuCategory.toSuwayomiDto(), it.status.rawValue) },
    mangaUpdates = mangaUpdates.map { SuwayomiMangaUpdateDto(it.manga.amatsubuManga.toSuwayomiDto(), it.status.rawValue) },
    jobsInfo = SuwayomiUpdaterJobsInfoDto(
        isRunning = jobsInfo.isRunning,
        totalJobs = jobsInfo.totalJobs,
        finishedJobs = jobsInfo.finishedJobs,
        skippedCategoriesCount = jobsInfo.skippedCategoriesCount,
        skippedMangasCount = jobsInfo.skippedMangasCount,
    ),
)

private fun LastSyncStatusQuery.LastSyncStatus.toSuwayomiDto() = SuwayomiSyncStatusDto(
    state = state.rawValue,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    backupRestoreId = backupRestoreId,
    errorMessage = errorMessage,
)

private fun ServerAboutQuery.AboutServer.toSuwayomiDto() = SuwayomiServerAboutDto(
    name = name,
    version = version,
    revision = revision,
    buildType = buildType,
    buildTime = buildTime.toString(),
    github = github,
    discord = discord,
)

private fun WebUiAboutQuery.AboutWebUI.toSuwayomiDto() = SuwayomiWebUiAboutDto(
    channel = channel.rawValue,
    tag = tag,
    updateTimestamp = updateTimestamp.toString(),
)

private fun AmatsubuServerSettings.toSuwayomiDto() = SuwayomiServerSettingsDto(
    authMode = authMode.rawValue,
    authPassword = authPassword,
    authUsername = authUsername,
    autoDownloadIgnoreReUploads = autoDownloadIgnoreReUploads,
    autoDownloadNewChapters = autoDownloadNewChapters,
    autoDownloadNewChaptersLimit = autoDownloadNewChaptersLimit,
    autoBackupIncludeCategories = autoBackupIncludeCategories,
    autoBackupIncludeChapters = autoBackupIncludeChapters,
    autoBackupIncludeClientData = autoBackupIncludeClientData,
    autoBackupIncludeHistory = autoBackupIncludeHistory,
    autoBackupIncludeManga = autoBackupIncludeManga,
    autoBackupIncludeServerSettings = autoBackupIncludeServerSettings,
    autoBackupIncludeTracking = autoBackupIncludeTracking,
    backupInterval = backupInterval,
    backupPath = backupPath,
    backupTTL = backupTTL,
    backupTime = backupTime,
    basicAuthEnabled = basicAuthEnabled,
    basicAuthPassword = basicAuthPassword,
    basicAuthUsername = basicAuthUsername,
    ip = ip,
    port = port,
    downloadAsCbz = downloadAsCbz,
    downloadsPath = downloadsPath,
    electronPath = electronPath,
    excludeCompleted = excludeCompleted,
    excludeEntryWithUnreadChapters = excludeEntryWithUnreadChapters,
    excludeNotStarted = excludeNotStarted,
    excludeUnreadChapters = excludeUnreadChapters,
    extensionRepos = extensionRepos,
    flareSolverrAsResponseFallback = flareSolverrAsResponseFallback,
    globalUpdateInterval = globalUpdateInterval,
    initialOpenInBrowserEnabled = initialOpenInBrowserEnabled,
    localSourcePath = localSourcePath,
    maxLogFileSize = maxLogFileSize,
    maxLogFiles = maxLogFiles,
    maxLogFolderSize = maxLogFolderSize,
    maxSourcesInParallel = maxSourcesInParallel,
    socksProxyEnabled = socksProxyEnabled,
    socksProxyHost = socksProxyHost,
    socksProxyPassword = socksProxyPassword,
    socksProxyPort = socksProxyPort,
    socksProxyUsername = socksProxyUsername,
    socksProxyVersion = socksProxyVersion,
    flareSolverrEnabled = flareSolverrEnabled,
    flareSolverrSessionName = flareSolverrSessionName,
    flareSolverrSessionTtl = flareSolverrSessionTtl,
    flareSolverrTimeout = flareSolverrTimeout,
    flareSolverrUrl = flareSolverrUrl,
    debugLogsEnabled = debugLogsEnabled,
    systemTrayEnabled = systemTrayEnabled,
    updateMangas = updateMangas,
    webUIChannel = webUIChannel.rawValue,
    webUIFlavor = webUIFlavor.rawValue,
    webUIInterface = webUIInterface.rawValue,
    webUIUpdateCheckInterval = webUIUpdateCheckInterval,
)

internal class SuwayomiGraphQlClient(
    private val client: OkHttpClient,
    private val endpoint: () -> String,
    private val snapshotCache: SuwayomiSnapshotCache? = null,
) : SuwayomiTokenOperations {

    private val apolloClientFactory = SuwayomiApolloClientFactory(client, endpoint)

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
        val response = apolloClientFactory.create().mutation(LoginMutation(LoginInput(username = username, password = password))).execute()
        val payload = response.data?.login
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi login failed")
        return SuwayomiTokens(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
    }

    override suspend fun refreshToken(refreshToken: String): String {
        val response = apolloClientFactory.create().mutation(RefreshTokenMutation(RefreshTokenInput(refreshToken = refreshToken))).execute()
        return response.data?.refreshToken?.accessToken
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi token refresh failed")
    }

    suspend fun sourceList(): List<SuwayomiSourceDto> {
        val response = apolloClientFactory.create().query(SourceListQuery()).execute()
        val sourceNodes = response.data?.sources?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no source list")
        return sourceNodes.map { source ->
            SuwayomiSourceDto(
                baseUrl = source.baseUrl,
                contentWarning = source.contentWarning.rawValue,
                displayName = source.displayName,
                homeUrl = source.homeUrl,
                iconUrl = source.iconUrl,
                id = source.id.toString(),
                isConfigurable = source.isConfigurable,
                isNsfw = source.isNsfw,
                lang = source.lang,
                name = source.name,
                supportsLatest = source.supportsLatest,
            )
        }
    }

    suspend fun trackerList(): List<SuwayomiTrackerDto> {
        val response = apolloClientFactory.create().query(TrackerListQuery()).execute()
        val trackerNodes = response.data?.trackers?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no tracker list")
        return trackerNodes.map { tracker ->
            SuwayomiTrackerDto(
                id = tracker.id,
                name = tracker.name,
                icon = tracker.icon,
                isLoggedIn = tracker.isLoggedIn,
                authUrl = tracker.authUrl,
                supportsTrackDeletion = tracker.supportsTrackDeletion,
                supportsReadingDates = tracker.supportsReadingDates,
                supportsPrivateTracking = tracker.supportsPrivateTracking,
                statuses = tracker.statuses.map { status ->
                    SuwayomiTrackStatusDto(
                        value = status.value,
                        name = status.name,
                    )
                },
                scores = tracker.scores,
            )
        }
    }

    suspend fun loginTrackerCredentials(
        trackerId: Int,
        username: String,
        password: String,
    ): SuwayomiTrackerDto {
        val response = apolloClientFactory.create().mutation(
            LoginTrackerCredentialsMutation(
                input = LoginTrackerCredentialsInput(
                    trackerId = trackerId,
                    username = username,
                    password = password,
                ),
            ),
        ).execute()
        val tracker = response.data?.loginTrackerCredentials?.tracker
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no tracker")
        return SuwayomiTrackerDto(
            id = tracker.id,
            name = tracker.name,
            icon = tracker.icon,
            isLoggedIn = tracker.isLoggedIn,
            authUrl = tracker.authUrl,
            supportsTrackDeletion = tracker.supportsTrackDeletion,
            supportsReadingDates = tracker.supportsReadingDates,
            supportsPrivateTracking = tracker.supportsPrivateTracking,
            statuses = tracker.statuses.map { status ->
                SuwayomiTrackStatusDto(
                    value = status.value,
                    name = status.name,
                )
            },
            scores = tracker.scores,
        )
    }

    suspend fun loginTrackerOAuth(
        trackerId: Int,
        callbackUrl: String,
    ): SuwayomiTrackerDto {
        val response = apolloClientFactory.create().mutation(
            LoginTrackerOAuthMutation(
                input = LoginTrackerOAuthInput(
                    trackerId = trackerId,
                    callbackUrl = callbackUrl,
                ),
            ),
        ).execute()
        val tracker = response.data?.loginTrackerOAuth?.tracker
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no tracker")
        return SuwayomiTrackerDto(
            id = tracker.id,
            name = tracker.name,
            icon = tracker.icon,
            isLoggedIn = tracker.isLoggedIn,
            authUrl = tracker.authUrl,
            supportsTrackDeletion = tracker.supportsTrackDeletion,
            supportsReadingDates = tracker.supportsReadingDates,
            supportsPrivateTracking = tracker.supportsPrivateTracking,
            statuses = tracker.statuses.map { status ->
                SuwayomiTrackStatusDto(
                    value = status.value,
                    name = status.name,
                )
            },
            scores = tracker.scores,
        )
    }

    suspend fun logoutTracker(trackerId: Int): SuwayomiTrackerDto {
        val response = apolloClientFactory.create().mutation(
            LogoutTrackerMutation(
                input = LogoutTrackerInput(trackerId = trackerId),
            ),
        ).execute()
        val tracker = response.data?.logoutTracker?.tracker
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no tracker")
        return SuwayomiTrackerDto(
            id = tracker.id,
            name = tracker.name,
            icon = tracker.icon,
            isLoggedIn = tracker.isLoggedIn,
            authUrl = tracker.authUrl,
            supportsTrackDeletion = tracker.supportsTrackDeletion,
            supportsReadingDates = tracker.supportsReadingDates,
            supportsPrivateTracking = tracker.supportsPrivateTracking,
            statuses = tracker.statuses.map { status ->
                SuwayomiTrackStatusDto(
                    value = status.value,
                    name = status.name,
                )
            },
            scores = tracker.scores,
        )
    }

    suspend fun extensionList(): List<SuwayomiExtensionDto> {
        val response = apolloClientFactory.create().mutation(FetchExtensionListMutation()).execute()
        val extensions = response.data?.fetchExtensions?.extensions
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension list")
        return extensions.map { it.amatsubuExtension.toSuwayomiDto() }
    }

    suspend fun extensionStores(): List<SuwayomiExtensionStoreDto> {
        val response = apolloClientFactory.create().query(ExtensionStoresQuery()).execute()
        val stores = response.data?.extensionStores?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension stores")
        return stores.map { it.amatsubuExtensionStore.toSuwayomiDto() }
    }

    suspend fun addExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto {
        val response = apolloClientFactory.create().mutation(
            AddExtensionStoreMutation(AddExtensionStoreInput(indexUrl = indexUrl)),
        ).execute()
        val store = response.data?.addExtensionStore?.extensionStore
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension store")
        return store.amatsubuExtensionStore.toSuwayomiDto()
    }

    suspend fun removeExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto? {
        val response = apolloClientFactory.create().mutation(
            RemoveExtensionStoreMutation(RemoveExtensionStoreInput(indexUrl = indexUrl)),
        ).execute()
        val payload = response.data?.removeExtensionStore
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension store result")
        return payload.extensionStore?.amatsubuExtensionStore?.toSuwayomiDto()
    }

    suspend fun updateExtension(
        pkgName: String,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): SuwayomiExtensionDto? {
        val response = apolloClientFactory.create().mutation(
            UpdateExtensionMutation(
                UpdateExtensionInput(
                    id = pkgName,
                    patch = UpdateExtensionPatchInput(
                        install = Optional.present(install),
                        uninstall = Optional.present(uninstall),
                        update = Optional.present(update),
                    ),
                ),
            ),
        ).execute()
        val payload = response.data?.updateExtension
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension update result")
        return payload.extension?.amatsubuExtension?.toSuwayomiDto()
    }

    suspend fun updateExtensions(
        pkgNames: List<String>,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): List<SuwayomiExtensionDto> {
        val ids = pkgNames.distinct()
        if (ids.isEmpty()) return emptyList()
        val response = apolloClientFactory.create().mutation(
            UpdateExtensionsMutation(
                UpdateExtensionsInput(
                    ids = ids,
                    patch = UpdateExtensionPatchInput(
                        install = Optional.present(install),
                        uninstall = Optional.present(uninstall),
                        update = Optional.present(update),
                    ),
                ),
            ),
        ).execute()
        val extensions = response.data?.updateExtensions?.extensions
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension update result")
        return extensions.map { it.amatsubuExtension.toSuwayomiDto() }
    }

    suspend fun getCategories(): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().query(AllCategoriesQuery()).execute()
        return response.data?.categories?.nodes?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no categories")
    }

    suspend fun createCategory(name: String, order: Int? = null): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            CreateCategoryMutation(CreateCategoryInput(name = name, order = Optional.presentIfNotNull(order))),
        ).execute()
        return response.data?.createCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryName(categoryId: Int, name: String): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMutation(UpdateCategoryInput(id = categoryId, patch = UpdateCategoryPatchInput(name = Optional.present(name)))),
        ).execute()
        return response.data?.updateCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryFlags(
        categoryId: Int,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ): SuwayomiCategoryDto {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMutation(
                UpdateCategoryInput(
                    id = categoryId,
                    patch = UpdateCategoryPatchInput(
                        includeInUpdate = Optional.present(IncludeOrExclude.valueOf(includeInUpdate.name)),
                        includeInDownload = Optional.present(IncludeOrExclude.valueOf(includeInDownload.name)),
                    ),
                ),
            ),
        ).execute()
        return response.data?.updateCategory?.category?.amatsubuCategory?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category")
    }

    suspend fun updateCategoryOrder(categoryId: Int, position: Int): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryOrderMutation(UpdateCategoryOrderInput(id = categoryId, position = position)),
        ).execute()
        return response.data?.updateCategoryOrder?.categories?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no categories")
    }

    suspend fun deleteCategory(categoryId: Int): SuwayomiCategoryDto? {
        val response = apolloClientFactory.create().mutation(DeleteCategoryMutation(DeleteCategoryInput(categoryId))).execute()
        return response.data?.deleteCategory?.category?.amatsubuCategory?.toSuwayomiDto()
    }

    suspend fun getCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> {
        val mangas = if (categoryId == 0) {
            getDefaultCategoryMangas()
        } else {
            getNamedCategoryMangas(categoryId)
        }.filterInLibraryMangas()
        snapshotCache?.storeCategoryMangas(
            serverKey = serverKey(),
            categoryId = categoryId,
            mangas = mangas,
            mirrorToLibrary = categoryId == 0,
        )
        return mangas
    }

    private suspend fun getNamedCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(
            GetNamedCategoryMangasQuery(categoryIds = listOf(categoryId), inLibrary = true),
        ).execute()
        return response.data?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category mangas")
    }

    private suspend fun getDefaultCategoryMangas(): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(GetCategoryMangasQuery(id = 0)).execute()
        return response.data?.category?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category mangas")
    }

    suspend fun getCategoryMangasSnapshot(categoryId: Int): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getCategoryMangas(serverKey(), categoryId)
    }

    suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> {
        val response = apolloClientFactory.create().query(GetMangaCategoriesQuery(mangaId)).execute()
        return response.data?.manga?.categories?.nodes?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga categories")
    }

    suspend fun getLibraryMangas(): List<SuwayomiMangaDto> {
        val response = apolloClientFactory.create().query(GetLibraryMangasQuery()).execute()
        val mangas = response.data?.mangas?.nodes?.map { it.amatsubuLibraryManga.toSuwayomiDto() }?.filterInLibraryMangas()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library mangas")
        snapshotCache?.storeLibraryMangas(serverKey(), mangas)
        return mangas
    }

    suspend fun getLibraryMangasSnapshot(): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getLibraryMangas(serverKey())
    }

    suspend fun getLibraryChapters(): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().query(GetLibraryChaptersQuery()).execute()
        val chapters = response.data?.chapters?.nodes?.map { it.amatsubuLibraryChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library chapters")
        snapshotCache?.storeLibraryChapters(serverKey(), chapters)
        return chapters
    }

    suspend fun getLibraryTrackingData(): LibraryTrackingData {
        val response = apolloClientFactory.create().query(GetLibraryTrackingDataQuery()).execute()
        val data = response.data
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library tracking data")
        return LibraryTrackingData(
            libraryTrackingMangas = LibraryTrackingMangaNodeList(
                data.libraryTrackingMangas.nodes.map { manga ->
                    SuwayomiLibraryTrackingMangaDto(
                        id = manga.id,
                        trackRecords = StatsTrackRecordNodeList(
                            manga.trackRecords.nodes.map { SuwayomiStatsTrackRecordDto(it.trackerId, it.score) },
                        ),
                    )
                },
            ),
            trackers = TrackerNodeList(
                data.trackers.nodes.map { SuwayomiTrackerDto(id = it.id, name = it.name, isLoggedIn = it.isLoggedIn) },
            ),
        )
    }

    suspend fun getServerStats(): ServerStatsData {
        val response = apolloClientFactory.create().query(GetServerStatsQuery()).execute()
        val data = response.data ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no server stats")
        return data.toServerStatsData()
    }

    suspend fun updateMangaCategories(
        mangaId: Int,
        categoryIds: List<Int>,
    ): List<SuwayomiCategoryDto> {
        val currentCategoryIds = getMangaCategories(mangaId).map { it.id }.toSet()
        val targetCategoryIds = categoryIds.distinct()
        val addCategoryIds = targetCategoryIds.filterNot { it in currentCategoryIds }
        val removeCategoryIds = currentCategoryIds.filterNot { it in targetCategoryIds.toSet() }

        if (addCategoryIds.isEmpty() && removeCategoryIds.isEmpty()) {
            return getMangaCategories(mangaId)
        }

        val patch = if (targetCategoryIds.isEmpty()) {
            UpdateMangaCategoriesPatchInput(clearCategories = Optional.present(true))
        } else {
            UpdateMangaCategoriesPatchInput(
                addToCategories = Optional.presentIfNotNull(addCategoryIds.takeIf { it.isNotEmpty() }),
                removeFromCategories = Optional.presentIfNotNull(removeCategoryIds.takeIf { it.isNotEmpty() }),
            )
        }
        val response = apolloClientFactory.create().mutation(
            UpdateMangaCategoriesMutation(UpdateMangaCategoriesInput(id = mangaId, patch = patch)),
        ).execute()
        return response.data?.updateMangaCategories?.manga?.categories?.nodes
            ?.map { it.amatsubuCategory.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga categories")
    }

    suspend fun updateMangasCategories(
        mangaIds: List<Int>,
        addCategoryIds: List<Int> = emptyList(),
        removeCategoryIds: List<Int> = emptyList(),
        clearCategories: Boolean = false,
    ): List<SuwayomiMangaDto> {
        val ids = mangaIds.distinct()
        val addIds = addCategoryIds.distinct()
        val removeIds = removeCategoryIds.distinct()
        if (ids.isEmpty()) return emptyList()
        if (!clearCategories && addIds.isEmpty() && removeIds.isEmpty()) return emptyList()

        val patch = if (clearCategories) {
            UpdateMangaCategoriesPatchInput(clearCategories = Optional.present(true))
        } else {
            UpdateMangaCategoriesPatchInput(
                addToCategories = Optional.presentIfNotNull(addIds.takeIf { it.isNotEmpty() }),
                removeFromCategories = Optional.presentIfNotNull(removeIds.takeIf { it.isNotEmpty() }),
            )
        }
        val response = apolloClientFactory.create().mutation(
            UpdateMangasCategoriesMutation(UpdateMangasCategoriesInput(ids = ids, patch = patch)),
        ).execute()
        return response.data?.updateMangasCategories?.mangas?.map { it.amatsubuManga.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated mangas")
    }

    suspend fun fetchSourceManga(
        sourceId: String,
        type: FetchSourceMangaType,
        page: Int,
        queryText: String? = null,
        filters: List<SuwayomiSourceFilterChange> = emptyList(),
    ): SourceMangaPageDto {
        val response = apolloClientFactory.create().mutation(
            FetchSourceMangaMutation(
                FetchSourceMangaInput(
                    source = sourceId,
                    type = GeneratedFetchSourceMangaType.valueOf(type.name),
                    page = page,
                    query = Optional.presentIfNotNull(queryText),
                    filters = Optional.presentIfNotNull(filters.takeIf { it.isNotEmpty() }?.map { it.toGeneratedInput() }),
                ),
            ),
        ).execute()
        val payload = response.data?.fetchSourceManga
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi server returned no source manga page")
        return SourceMangaPageDto(payload.hasNextPage, payload.mangas.map { it.amatsubuManga.toSuwayomiDto() })
    }

    suspend fun sourceDetails(sourceId: String): SuwayomiSourceDetailsDto {
        val response = apolloClientFactory.create().query(SourceDetailsQuery(sourceId)).execute()
        val source = response.data?.source
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi server returned no source")
        return SuwayomiSourceDetailsDto(
            baseUrl = source.baseUrl,
            contentWarning = source.contentWarning.rawValue,
            displayName = source.displayName,
            homeUrl = source.homeUrl,
            iconUrl = source.iconUrl,
            id = source.id.toString(),
            isConfigurable = source.isConfigurable,
            isNsfw = source.isNsfw,
            lang = source.lang,
            name = source.name,
            supportsLatest = source.supportsLatest,
            filters = source.filters.map { it.amatsubuSourceFilter.toSuwayomiDto() },
            preferences = source.preferences.map { it.amatsubuSourcePreference.toSuwayomiDto() },
        )
    }

    suspend fun sourceFilters(sourceId: String): List<SuwayomiSourceFilterDto> {
        return sourceDetails(sourceId).filters
    }

    suspend fun sourcePreferences(sourceId: String): List<SuwayomiSourcePreferenceDto> {
        return sourceDetails(sourceId).preferences
    }

    suspend fun updateSourcePreference(
        sourceId: String,
        change: SuwayomiSourcePreferenceChange,
    ): List<SuwayomiSourcePreferenceDto> {
        val response = apolloClientFactory.create().mutation(
            UpdateSourcePreferenceMutation(UpdateSourcePreferenceInput(source = sourceId, change = change.toGeneratedInput())),
        ).execute()
        return response.data?.updateSourcePreference?.preferences?.map { it.amatsubuSourcePreference.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: response.exception?.message ?: "Suwayomi server returned no source preferences")
    }

    suspend fun getManga(mangaId: Int): SuwayomiMangaDto {
        val response = apolloClientFactory.create().query(GetMangaQuery(mangaId)).execute()
        val manga = response.data?.manga?.amatsubuManga?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga")
        snapshotCache?.storeManga(serverKey(), manga)
        return manga
    }

    suspend fun getMangaSnapshot(mangaId: Int): SuwayomiSnapshot<SuwayomiMangaDto>? {
        return snapshotCache?.getManga(serverKey(), mangaId)
    }

    suspend fun fetchMangaAndChaptersPartial(
        mangaId: Int,
        fetchManga: Boolean,
        fetchChapters: Boolean,
    ): GraphQlPartialResponse<FetchMangaAndChaptersPayload> {
        val response = apolloClientFactory.create().mutation(
            FetchMangaAndChaptersMutation(
                FetchMangaAndChaptersInput(
                    id = mangaId,
                    fetchManga = fetchManga,
                    fetchChapters = fetchChapters,
                ),
            ),
        ).execute()
        val payload = response.data?.fetchMangaAndChapters
        val errors = response.errors.orEmpty()
        if (payload == null && errors.isNotEmpty()) {
            error(errors.joinToString("; ") { it.message })
        }
        return GraphQlPartialResponse(
            data = payload?.let {
                FetchMangaAndChaptersPayload(
                    manga = it.manga.amatsubuManga.toSuwayomiDto(),
                    chapters = it.chapters.map { chapter -> chapter.amatsubuChapter.toSuwayomiDto() },
                )
            } ?: error("Suwayomi server returned no manga or chapters"),
            errors = errors.map { GraphQlError(it.message) },
        )
    }

    suspend fun getMangaTrackSummary(mangaId: Int): SuwayomiMangaTrackSummaryDto {
        val response = apolloClientFactory.create().query(GetMangaTrackSummaryQuery(mangaId)).execute()
        val manga = response.data?.manga
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga track summary")
        return SuwayomiMangaTrackSummaryDto(
            description = manga.description,
            id = manga.id,
            status = MangaStatus.valueOf(manga.status.rawValue),
            thumbnailUrl = manga.thumbnailUrl,
            title = manga.title,
            chapters = SuwayomiTrackSummaryChapterCountDto(manga.chapters.totalCount.toLong()),
            latestReadChapter = manga.latestReadChapter?.let {
                SuwayomiTrackSummaryLatestReadChapterDto(it.chapterNumber.toDouble())
            },
            unreadCount = manga.unreadCount.toLong(),
        )
    }

    suspend fun getRecentChapters(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val response = apolloClientFactory.create().query(GetRecentChaptersQuery(limit)).execute()
        return response.data?.chapters?.nodes?.map { it.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no recent chapters")
    }

    suspend fun getReadingHistory(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val response = apolloClientFactory.create().query(GetReadingHistoryQuery(limit)).execute()
        return response.data?.chapters?.nodes?.map { it.toSuwayomiDto() }
            ?.filter { it.isRead || it.lastPageRead > 0 }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reading history")
    }

    suspend fun getReadingHistoryChapterIds(pageSize: Int = 500): List<Int> {
        val ids = mutableListOf<Int>()
        var offset = 0
        do {
            val response = apolloClientFactory.create().query(GetReadingHistoryIdsQuery(pageSize, offset)).execute()
            val page = response.data?.chapters
                ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reading history ids")

            ids += page.nodes.map { it.id }
            offset += page.nodes.size
        } while (page.nodes.isNotEmpty() && offset < page.totalCount)

        return ids
    }

    suspend fun updateMangaLibrary(
        mangaId: Int,
        inLibrary: Boolean,
    ): SuwayomiMangaDto {
        val response = apolloClientFactory.create().mutation(
            UpdateMangaLibraryMutation(UpdateMangaInput(id = mangaId, patch = UpdateMangaPatchInput(inLibrary = Optional.present(inLibrary)))),
        ).execute()
        return response.data?.updateManga?.manga?.amatsubuManga?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated manga")
    }

    suspend fun updateLibraryMangas(): Boolean {
        val response = apolloClientFactory.create().mutation(UpdateLibraryMangasMutation(UpdateLibraryMangaInput())).execute()
        return response.data?.updateLibraryManga?.updateStatus?.isRunning
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library update status")
    }

    suspend fun updateCategoryMangas(categoryId: Int): Boolean {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMangasMutation(UpdateCategoryMangaInput(categories = listOf(categoryId))),
        ).execute()
        return response.data?.updateCategoryManga?.updateStatus?.isRunning
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category update status")
    }

    suspend fun getLibraryUpdateStatus(): SuwayomiLibraryUpdateStatusDto {
        val response = apolloClientFactory.create().query(LibraryUpdateStatusQuery()).execute()
        return response.data?.libraryUpdateStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library update status")
    }

    suspend fun stopLibraryUpdate() {
        val response = apolloClientFactory.create().mutation(StopLibraryUpdateMutation(UpdateStopInput())).execute()
        if (response.data?.updateStop == null) error(response.errors?.firstOrNull()?.message ?: "Suwayomi server did not stop library update")
    }

    suspend fun lastSyncStatus(): SuwayomiSyncStatusDto? {
        val response = apolloClientFactory.create().query(LastSyncStatusQuery()).execute()
        if (response.data == null) error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no sync status")
        return response.data?.lastSyncStatus?.toSuwayomiDto()
    }

    suspend fun startSync(): StartSyncPayload {
        val response = apolloClientFactory.create().mutation(StartSyncMutation(StartSyncInput())).execute()
        return response.data?.startSync?.let { StartSyncPayload(it.result.rawValue) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no sync result")
    }

    suspend fun getGlobalMeta(key: String): SuwayomiGlobalMetaDto? {
        val response = apolloClientFactory.create().query(GetGlobalMetaQuery(key)).execute()
        if (response.errors?.isNotEmpty() == true) {
            error(response.errors!!.first().message)
        }
        return response.data?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
    }

    suspend fun setGlobalMeta(
        key: String,
        value: String,
    ): SuwayomiGlobalMetaDto {
        val response = apolloClientFactory.create().mutation(
            SetGlobalMetaMutation(SetGlobalMetaInput(meta = GlobalMetaTypeInput(key, value))),
        ).execute()
        return response.data?.setGlobalMeta?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no global metadata")
    }

    suspend fun deleteGlobalMeta(key: String): SuwayomiGlobalMetaDto? {
        val response = apolloClientFactory.create().mutation(
            DeleteGlobalMetaMutation(DeleteGlobalMetaInput(key = key)),
        ).execute()
        if (response.data?.deleteGlobalMeta == null && response.errors?.isNotEmpty() == true) {
            error(response.errors!!.first().message)
        }
        return response.data?.deleteGlobalMeta?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
    }

    suspend fun clearCachedImages(
        cachedPages: Boolean? = null,
        cachedThumbnails: Boolean? = null,
        downloadedThumbnails: Boolean? = null,
    ): SuwayomiClearCachedImagesDto {
        val response = apolloClientFactory.create().mutation(
            ClearCachedImagesMutation(
                ClearCachedImagesInput(
                    cachedPages = cachedPages.optional(),
                    cachedThumbnails = cachedThumbnails.optional(),
                    downloadedThumbnails = downloadedThumbnails.optional(),
                ),
            ),
        ).execute()
        return response.data?.clearCachedImages?.let {
            SuwayomiClearCachedImagesDto(
                cachedPages = it.cachedPages,
                cachedThumbnails = it.cachedThumbnails,
                downloadedThumbnails = it.downloadedThumbnails,
            )
        } ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no cache clear result")
    }

    suspend fun setMangaMeta(
        mangaId: Int,
        key: String,
        value: String,
    ): SuwayomiMangaMetaDto {
        val response = apolloClientFactory.create().mutation(
            SetMangaMetaMutation(SetMangaMetaInput(meta = MangaMetaTypeInput(key, mangaId, value))),
        ).execute()
        return response.data?.setMangaMeta?.meta?.let { SuwayomiMangaMetaDto(it.key, it.mangaId, it.value) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga metadata")
    }

    suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val chapters = runCatching {
            apolloClientFactory.create()
                .mutation(FetchChaptersMutation(FetchChaptersInput(mangaId = mangaId)))
                .execute()
                .data
                ?.fetchChapters
                ?.chapters
                ?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
                .orEmpty()
        }.getOrElse { error ->
            if (error.message?.contains("No chapters found", ignoreCase = true) == true) {
                emptyList()
            } else {
                throw error
            }
        }
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getCachedChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().query(GetCachedChaptersQuery(mangaId)).execute()
        val chapters = response.data?.chapters?.nodes?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no cached chapters")
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getChaptersSnapshot(mangaId: Int): SuwayomiSnapshot<List<SuwayomiChapterDto>>? {
        return snapshotCache?.getChapters(serverKey(), mangaId)
    }

    suspend fun getChapterPages(chapterId: Int): List<String> {
        val response = apolloClientFactory.create()
            .mutation(FetchChapterPagesMutation(FetchChapterPagesInput(chapterId = chapterId)))
            .execute()
        return response.data?.fetchChapterPages?.pages
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no chapter pages")
    }

    suspend fun getChapterPageManifest(chapterId: Int): SuwayomiChapterPageManifest {
        val response = apolloClientFactory.create()
            .mutation(FetchChapterPagesMutation(FetchChapterPagesInput(chapterId = chapterId)))
            .execute()
        return response.data?.fetchChapterPages
            ?.let { payload ->
                SuwayomiChapterPageManifest(
                    pages = payload.pages,
                    chapter = payload.chapter?.amatsubuReaderChapter?.toSuwayomiDto()
                        ?: error("Suwayomi server returned no manifest chapter"),
                )
            }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no chapter page manifest")
    }

    suspend fun updateChapterProgress(
        chapterId: Int,
        isRead: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(isRead = Optional.present(isRead), lastPageRead = Optional.present(lastPageRead)),
        )
    }

    suspend fun updateChapterRead(
        chapterId: Int,
        isRead: Boolean,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(
                isRead = Optional.present(isRead),
                lastPageRead = Optional.presentIfNotNull(0.takeIf { !isRead }),
            ),
        )
    }

    suspend fun updateChapterBookmark(
        chapterId: Int,
        isBookmarked: Boolean,
    ): SuwayomiChapterDto {
        return updateReaderChapter(chapterId, UpdateChapterPatchInput(isBookmarked = Optional.present(isBookmarked)))
    }

    suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        return updateReaderChapter(
            chapterId,
            UpdateChapterPatchInput(
                isRead = Optional.present(isRead),
                isBookmarked = Optional.present(isBookmarked),
                lastPageRead = Optional.present(lastPageRead),
            ),
        )
    }

    suspend fun updateChaptersRead(
        chapterIds: List<Int>,
        isRead: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isRead = isRead,
            lastPageRead = 0.takeIf { !isRead },
        )
    }

    suspend fun updateChaptersBookmark(
        chapterIds: List<Int>,
        isBookmarked: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isBookmarked = isBookmarked,
        )
    }

    private suspend fun updateChapters(
        chapterIds: List<Int>,
        isRead: Boolean? = null,
        isBookmarked: Boolean? = null,
        lastPageRead: Int? = null,
    ): List<SuwayomiChapterDto> {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return emptyList()

        val response = apolloClientFactory.create().mutation(
            UpdateReaderChaptersMutation(
                UpdateChaptersInput(
                    ids = ids,
                    patch = UpdateChapterPatchInput(
                        isRead = Optional.presentIfNotNull(isRead),
                        isBookmarked = Optional.presentIfNotNull(isBookmarked),
                        lastPageRead = Optional.presentIfNotNull(lastPageRead),
                    ),
                ),
            ),
        ).execute()
        return response.data?.updateChapters?.chapters?.map { it.amatsubuReaderChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated chapters")
    }

    private suspend fun updateReaderChapter(
        chapterId: Int,
        patch: UpdateChapterPatchInput,
    ): SuwayomiChapterDto {
        val response = apolloClientFactory.create().mutation(
            UpdateReaderChapterMutation(UpdateChapterInput(id = chapterId, patch = patch)),
        ).execute()
        return response.data?.updateChapter?.chapter?.amatsubuReaderChapter?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated chapter")
    }

    suspend fun trackProgress(mangaId: Int) {
        val response = apolloClientFactory.create().mutation(
            TrackProgressMutation(
                input = TrackProgressInput(mangaId = mangaId),
            ),
        ).execute()
        response.data?.trackProgress
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no track progress result")
    }

    suspend fun getTrackRecords(mangaId: Int): List<SuwayomiTrackRecordDto> {
        val response = apolloClientFactory.create().query(TrackRecordsQuery(mangaId)).execute()
        val records = response.data?.trackRecords?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no track records")
        return records.map { it.amatsubuTrackRecord.toSuwayomiDto() }
    }

    suspend fun searchTracker(
        trackerId: Int,
        queryText: String,
    ): List<SuwayomiTrackSearchDto> {
        val response = apolloClientFactory.create().query(
            SearchTrackerQuery(SearchTrackerInput(query = queryText, trackerId = trackerId)),
        ).execute()
        val results = response.data?.searchTracker?.trackSearches
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no tracker search results")
        return results.map { it.amatsubuTrackSearch.toSuwayomiDto() }
    }

    suspend fun bindTrack(
        mangaId: Int,
        trackerId: Int,
        remoteId: Long,
        private: Boolean = false,
    ): SuwayomiTrackRecordDto {
        val response = apolloClientFactory.create().mutation(
            BindTrackMutation(
                BindTrackInput(
                    mangaId = mangaId,
                    trackerId = trackerId,
                    remoteId = remoteId.toString(),
                    private = Optional.present(private),
                ),
            ),
        ).execute()
        val record = response.data?.bindTrack?.trackRecord
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no bound track")
        return record.amatsubuTrackRecord.toSuwayomiDto()
    }

    suspend fun bindTrackRecord(
        mangaId: Int,
        trackRecordId: Int,
    ): SuwayomiTrackRecordDto {
        val response = apolloClientFactory.create().mutation(
            BindTrackRecordMutation(BindTrackRecordInput(mangaId = mangaId, trackRecordId = trackRecordId)),
        ).execute()
        val record = response.data?.bindTrackRecord?.trackRecord
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no bound track")
        return record.amatsubuTrackRecord.toSuwayomiDto()
    }

    suspend fun fetchTrack(recordId: Int): SuwayomiTrackRecordDto {
        val response = apolloClientFactory.create().mutation(
            FetchTrackMutation(FetchTrackInput(recordId = recordId)),
        ).execute()
        val record = response.data?.fetchTrack?.trackRecord
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no fetched track")
        return record.amatsubuTrackRecord.toSuwayomiDto()
    }

    suspend fun updateTrack(
        recordId: Int,
        status: Int? = null,
        lastChapterRead: Double? = null,
        scoreString: String? = null,
        startDate: Long? = null,
        finishDate: Long? = null,
        private: Boolean? = null,
    ): SuwayomiTrackRecordDto? {
        val response = apolloClientFactory.create().mutation(
            UpdateTrackMutation(
                UpdateTrackInput(
                    recordId = recordId,
                    status = status.optional(),
                    lastChapterRead = lastChapterRead.optional(),
                    scoreString = scoreString.optional(),
                    startDate = startDate?.toString().optional(),
                    finishDate = finishDate?.toString().optional(),
                    private = private.optional(),
                ),
            ),
        ).execute()
        val payload = response.data?.updateTrack
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated track")
        return payload.trackRecord?.amatsubuTrackRecord?.toSuwayomiDto()
    }

    suspend fun unbindTrack(
        recordId: Int,
        deleteRemoteTrack: Boolean = false,
    ): SuwayomiTrackRecordDto? {
        val response = apolloClientFactory.create().mutation(
            UnbindTrackMutation(
                UnbindTrackInput(
                    recordId = recordId,
                    deleteRemoteTrack = Optional.present(deleteRemoteTrack),
                ),
            ),
        ).execute()
        val payload = response.data?.unbindTrack
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no unbound track")
        return payload.trackRecord?.amatsubuTrackRecord?.toSuwayomiDto()
    }

    suspend fun getDownloadStatus(): SuwayomiDownloadStatusDto {
        val response = apolloClientFactory.create().query(GetDownloadStatusQuery()).execute()
        val status = response.data?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no download status")
        snapshotCache?.storeDownloadStatus(serverKey(), status)
        return status
    }

    suspend fun getDownloadStatusSnapshot(): SuwayomiSnapshot<SuwayomiDownloadStatusDto>? {
        return snapshotCache?.getDownloadStatus(serverKey())
    }

    suspend fun enqueueChapterDownloads(chapterIds: List<Int>) {
        val response = apolloClientFactory.create().mutation(
            EnqueueChapterDownloadsMutation(EnqueueChapterDownloadsInput(ids = chapterIds)),
        ).execute()
        if (response.data?.enqueueChapterDownloads == null) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no enqueue result")
        }
    }

    suspend fun dequeueChapterDownload(chapterId: Int) {
        val response = apolloClientFactory.create().mutation(
            DequeueChapterDownloadMutation(DequeueChapterDownloadInput(id = chapterId)),
        ).execute()
        if (response.data?.dequeueChapterDownload == null) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no dequeue result")
        }
    }

    suspend fun dequeueChapterDownloads(chapterIds: List<Int>): SuwayomiDownloadStatusDto {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return getDownloadStatus()

        val response = apolloClientFactory.create().mutation(
            DequeueChapterDownloadsMutation(DequeueChapterDownloadsInput(ids = ids)),
        ).execute()
        return response.data?.dequeueChapterDownloads?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no dequeue status")
    }

    suspend fun startDownloader() {
        val response = apolloClientFactory.create().mutation(StartDownloaderMutation(StartDownloaderInput())).execute()
        if (response.data?.startDownloader == null) error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no start result")
    }

    suspend fun stopDownloader() {
        val response = apolloClientFactory.create().mutation(StopDownloaderMutation(StopDownloaderInput())).execute()
        if (response.data?.stopDownloader == null) error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no stop result")
    }

    suspend fun clearDownloader() {
        val response = apolloClientFactory.create().mutation(ClearDownloaderMutation(ClearDownloaderInput())).execute()
        if (response.data?.clearDownloader == null) error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no clear result")
    }

    suspend fun reorderChapterDownload(chapterId: Int, to: Int): SuwayomiDownloadStatusDto {
        val response = apolloClientFactory.create().mutation(
            ReorderChapterDownloadMutation(ReorderChapterDownloadInput(chapterId = chapterId, to = to)),
        ).execute()
        return response.data?.reorderChapterDownload?.downloadStatus?.amatsubuDownloadStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no reordered download status")
    }

    suspend fun deleteDownloadedChapters(chapterIds: List<Int>): List<SuwayomiChapterDto> {
        val response = apolloClientFactory.create().mutation(
            DeleteDownloadedChaptersMutation(DeleteDownloadedChaptersInput(ids = chapterIds)),
        ).execute()
        return response.data?.deleteDownloadedChapters?.chapters?.map { it.amatsubuChapter.toSuwayomiDto() }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no deleted chapters")
    }

    suspend fun serverAbout(): SuwayomiServerAboutDto {
        val response = apolloClientFactory.create().query(ServerAboutQuery()).execute()
        return response.data?.aboutServer?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no server about data")
    }

    suspend fun webUiAbout(): SuwayomiWebUiAboutDto {
        val response = apolloClientFactory.create().query(WebUiAboutQuery()).execute()
        return response.data?.aboutWebUI?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no Web UI about data")
    }

    suspend fun serverSettings(): SuwayomiServerSettingsDto {
        val response = apolloClientFactory.create().query(ServerSettingsQuery()).execute()
        return response.data?.settings?.amatsubuServerSettings?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no settings")
    }

    suspend fun setSettings(settings: JsonObject): SuwayomiServerSettingsDto {
        val response = apolloClientFactory.create().mutation(SetSettingsMutation(settings.toGeneratedInput())).execute()
        return response.data?.setSettings?.settings?.amatsubuServerSettings?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated settings")
    }

    suspend fun setExtensionRepos(extensionRepos: List<String>): SuwayomiServerSettingsDto {
        return setSettings(
            buildJsonObject {
                putJsonArray("extensionRepos") {
                    extensionRepos.forEach { add(it) }
                }
            },
        )
    }

    private fun AmatsubuTrackRecord.toSuwayomiDto(): SuwayomiTrackRecordDto {
        val tracker = tracker.amatsubuTracker
        return SuwayomiTrackRecordDto(
            id = id,
            mangaId = mangaId,
            trackerId = trackerId,
            remoteId = remoteId.toString(),
            libraryId = libraryId?.toString(),
            title = title,
            lastChapterRead = lastChapterRead,
            totalChapters = totalChapters,
            status = status,
            score = score,
            displayScore = displayScore,
            remoteUrl = remoteUrl,
            startDate = startDate.toString(),
            finishDate = finishDate.toString(),
            private = private,
            tracker = SuwayomiTrackerDto(
                id = tracker.id,
                name = tracker.name,
                icon = tracker.icon,
                isLoggedIn = tracker.isLoggedIn,
                authUrl = tracker.authUrl,
                supportsTrackDeletion = tracker.supportsTrackDeletion,
                supportsReadingDates = tracker.supportsReadingDates,
                supportsPrivateTracking = tracker.supportsPrivateTracking,
                statuses = tracker.statuses.map { SuwayomiTrackStatusDto(it.value, it.name) },
                scores = tracker.scores,
            ),
        )
    }

    private fun AmatsubuTrackSearch.toSuwayomiDto() = SuwayomiTrackSearchDto(
        id = id,
        trackerId = trackerId,
        remoteId = remoteId.toString(),
        libraryId = libraryId?.toString(),
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        trackingUrl = trackingUrl,
        coverUrl = coverUrl,
        summary = summary,
        publishingStatus = publishingStatus,
        publishingType = publishingType,
        startDate = startDate,
        status = status,
        score = score,
        startedReadingDate = startedReadingDate.toString(),
        finishedReadingDate = finishedReadingDate.toString(),
        private = private,
    )

    private fun <T> T?.optional(): Optional<T?> =
        if (this == null) Optional.Absent else Optional.present(this)

    private fun serverKey(): String = endpoint().trimEnd('/')

    private fun JsonObjectBuilder.putFilterChange(change: SuwayomiSourceFilterChange) {
        put("position", change.position)
        change.checkBoxState?.let { put("checkBoxState", it) }
        change.groupChange?.let { nestedChange ->
            putJsonObject("groupChange") {
                putFilterChange(nestedChange)
            }
        }
        change.selectState?.let { put("selectState", it) }
        change.sortState?.let { selection ->
            putJsonObject("sortState") {
                put("index", selection.index)
                put("ascending", selection.ascending)
            }
        }
        change.textState?.let { put("textState", it) }
        change.triState?.let { put("triState", it.name) }
    }

    private fun JsonObjectBuilder.putPreferenceChange(change: SuwayomiSourcePreferenceChange) {
        put("position", change.position)
        change.checkBoxState?.let { put("checkBoxState", it) }
        change.editTextState?.let { put("editTextState", it) }
        change.listState?.let { put("listState", it) }
        change.multiSelectState?.let { values ->
            putJsonArray("multiSelectState") {
                values.forEach { add(it) }
            }
        }
        change.switchState?.let { put("switchState", it) }
    }

}

enum class FetchSourceMangaType {
    SEARCH,
    POPULAR,
    LATEST,
}
