package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.GetReadingHistoryQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetRecentChaptersQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.GetServerStatsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.LastSyncStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.LibraryUpdateStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ServerAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.WebUiAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuCategory
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuDownload
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuDownloadStatus
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtension
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtensionSource
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuExtensionStore
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuLibraryChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuLibraryManga
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuManga
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuReaderChapter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuServerSettings
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourceFilter
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourceFilterChild
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuSourcePreference
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuTrackRecord
import eu.kanade.tachiyomi.data.suwayomi.generated.fragment.AmatsubuTrackSearch
import eu.kanade.tachiyomi.data.suwayomi.generated.type.TriState

internal fun AmatsubuExtension.toSuwayomiDto() = SuwayomiExtensionDto(
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

internal fun AmatsubuExtensionStore.toSuwayomiDto() = SuwayomiExtensionStoreDto(
    name = name,
    badgeLabel = badgeLabel,
    signingKey = signingKey,
    contactWebsite = contactWebsite,
    contactDiscord = contactDiscord,
    indexUrl = indexUrl,
    isLegacy = isLegacy,
    extensionListUrl = extensionListUrl,
)

internal fun AmatsubuCategory.toSuwayomiDto() = SuwayomiCategoryDto(
    id = id,
    name = name,
    order = order,
    includeInDownload = SuwayomiCategoryFlag.valueOf(includeInDownload.rawValue),
    includeInUpdate = SuwayomiCategoryFlag.valueOf(includeInUpdate.rawValue),
)

internal fun AmatsubuExtensionSource.toSuwayomiDto() = SuwayomiSourceDto(
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

internal fun Any?.asLongOrNull() = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}

internal fun AmatsubuLibraryManga.toSuwayomiDto() = SuwayomiMangaDto(
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
    latestFetchedChapter = latestFetchedChapter?.let {
        SuwayomiLatestFetchedChapterDto(it.fetchedAt.asLongOrNull() ?: 0L)
    },
    latestReadChapter = latestReadChapter?.let { SuwayomiLatestReadChapterDto(it.lastReadAt.asLongOrNull() ?: 0L) },
    latestUploadedChapter = latestUploadedChapter?.let {
        SuwayomiLatestUploadedChapterDto(it.uploadDate.asLongOrNull() ?: 0L)
    },
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

internal fun AmatsubuManga.toSuwayomiDto() = SuwayomiMangaDto(
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
    latestFetchedChapter = latestFetchedChapter?.let {
        SuwayomiLatestFetchedChapterDto(it.fetchedAt.asLongOrNull() ?: 0L)
    },
    latestReadChapter = latestReadChapter?.let { SuwayomiLatestReadChapterDto(it.lastReadAt.asLongOrNull() ?: 0L) },
    latestUploadedChapter = latestUploadedChapter?.let {
        SuwayomiLatestUploadedChapterDto(it.uploadDate.asLongOrNull() ?: 0L)
    },
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

internal fun AmatsubuLibraryChapter.toSuwayomiDto() = SuwayomiChapterDto(
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

internal fun AmatsubuLibraryChapter.toSuwayomiDto(manga: AmatsubuLibraryManga) = SuwayomiChapterWithMangaDto(
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

internal fun GetRecentChaptersQuery.Node.toSuwayomiDto() =
    amatsubuLibraryChapter.toSuwayomiDto(manga.amatsubuLibraryManga)

internal fun GetReadingHistoryQuery.Node.toSuwayomiDto() =
    amatsubuLibraryChapter.toSuwayomiDto(manga.amatsubuLibraryManga)

internal fun AmatsubuChapter.toSuwayomiDto() = SuwayomiChapterDto(
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

internal fun AmatsubuDownloadStatus.toSuwayomiDto() = SuwayomiDownloadStatusDto(
    state = state.rawValue,
    queue = queue.map { it.amatsubuDownload.toSuwayomiDto() },
)

internal fun AmatsubuDownload.toSuwayomiDto() = SuwayomiDownloadDto(
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

internal fun AmatsubuReaderChapter.toSuwayomiDto() = SuwayomiChapterDto(
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

internal fun AmatsubuSourceFilter.toSuwayomiDto() = SuwayomiSourceFilterDto(
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

internal fun AmatsubuSourceFilterChild.toSuwayomiDto() = SuwayomiSourceFilterDto(
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

internal fun AmatsubuSourcePreference.toSuwayomiDto(): SuwayomiSourcePreferenceDto {
    fun base(key: String?, title: String?, summary: String?, enabled: Boolean, visible: Boolean) =
        SuwayomiSourcePreferenceDto(
            type = __typename,
            key = key,
            title = title,
            summary = summary,
            enabled = enabled,
            visible = visible,
        )
    return onCheckBoxPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentBoolean = preference.currentBoolean,
            defaultBoolean = preference.defaultBoolean,
        )
    } ?: onSwitchPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentBoolean = preference.currentBoolean,
            defaultBoolean = preference.defaultBoolean,
        )
    } ?: onEditTextPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentString = preference.currentString,
            defaultString = preference.defaultString,
            text = preference.text,
            dialogTitle = preference.dialogTitle,
            dialogMessage = preference.dialogMessage,
        )
    } ?: onListPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentString = preference.currentString,
            defaultString = preference.defaultString,
            entries = preference.entries,
            entryValues = preference.entryValues,
        )
    } ?: onMultiSelectListPreference?.let { preference ->
        base(preference.key, preference.title, preference.summary, preference.enabled, preference.visible).copy(
            currentStringList = preference.currentStringList,
            defaultStringList = preference.defaultStringList,
            entries = preference.entries,
            entryValues = preference.entryValues,
            dialogTitle = preference.dialogTitle,
            dialogMessage = preference.dialogMessage,
        )
    } ?: base(null, null, null, true, true)
}

internal fun TriState.toSuwayomiDto() = SuwayomiTriState.valueOf(rawValue)

internal fun GetServerStatsQuery.Data.toServerStatsData() = ServerStatsData(
    statisticsMangas = ServerStatsMangaNodeList(
        totalCount = statisticsMangas.totalCount,
        nodes = statisticsMangas.nodes.map { manga ->
            SuwayomiStatsMangaDto(
                id = manga.id,
                status = MangaStatus.valueOf(manga.status.rawValue),
                unreadCount = manga.unreadCount.toLong(),
                downloadCount = manga.downloadCount,
                initialized = manga.initialized,
                latestReadChapter = manga.latestReadChapter?.let {
                    SuwayomiStatsLatestReadChapterDto(it.lastReadAt.toString())
                },
                sourceId = manga.sourceId.toString(),
                updateStrategy = UpdateStrategy.valueOf(manga.updateStrategy.rawValue),
                categories = StatsCategoryNodeList(
                    manga.categories.nodes.map {
                        SuwayomiStatsCategoryDto(it.id, SuwayomiCategoryFlag.valueOf(it.includeInUpdate.rawValue))
                    },
                ),
                trackRecords = StatsTrackRecordNodeList(
                    manga.trackRecords.nodes.map {
                        SuwayomiStatsTrackRecordDto(it.trackerId, it.score)
                    },
                ),
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
    trackers = TrackerNodeList(
        trackers.nodes.map {
            SuwayomiTrackerDto(id = it.id, name = it.name, isLoggedIn = it.isLoggedIn)
        },
    ),
)

internal fun LibraryUpdateStatusQuery.LibraryUpdateStatus.toSuwayomiDto() = SuwayomiLibraryUpdateStatusDto(
    categoryUpdates = categoryUpdates.map {
        SuwayomiCategoryUpdateDto(it.category.amatsubuCategory.toSuwayomiDto(), it.status.rawValue)
    },
    mangaUpdates = mangaUpdates.map {
        SuwayomiMangaUpdateDto(it.manga.amatsubuManga.toSuwayomiDto(), it.status.rawValue)
    },
    jobsInfo = SuwayomiUpdaterJobsInfoDto(
        isRunning = jobsInfo.isRunning,
        totalJobs = jobsInfo.totalJobs,
        finishedJobs = jobsInfo.finishedJobs,
        skippedCategoriesCount = jobsInfo.skippedCategoriesCount,
        skippedMangasCount = jobsInfo.skippedMangasCount,
    ),
)

internal fun LastSyncStatusQuery.LastSyncStatus.toSuwayomiDto() = SuwayomiSyncStatusDto(
    state = state.rawValue,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    backupRestoreId = backupRestoreId,
    errorMessage = errorMessage,
)

internal fun ServerAboutQuery.AboutServer.toSuwayomiDto() = SuwayomiServerAboutDto(
    name = name,
    version = version,
    revision = revision,
    buildType = buildType,
    buildTime = buildTime.toString(),
    github = github,
    discord = discord,
)

internal fun WebUiAboutQuery.AboutWebUI.toSuwayomiDto() = SuwayomiWebUiAboutDto(
    channel = channel.rawValue,
    tag = tag,
    updateTimestamp = updateTimestamp.toString(),
)

internal fun AmatsubuServerSettings.toSuwayomiDto() = SuwayomiServerSettingsDto(
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

internal fun AmatsubuTrackRecord.toSuwayomiDto(): SuwayomiTrackRecordDto {
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

internal fun AmatsubuTrackSearch.toSuwayomiDto() = SuwayomiTrackSearchDto(
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

internal fun <T> T?.optional(): Optional<T?> =
    if (this == null) Optional.Absent else Optional.present(this)
