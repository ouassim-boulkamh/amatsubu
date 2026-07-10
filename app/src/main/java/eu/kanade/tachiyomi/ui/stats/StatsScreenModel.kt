package eu.kanade.tachiyomi.ui.stats

import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.ServerStateInvalidation
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryFlag
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiServerSettingsDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiStatsMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiStatsTrackRecordDto
import eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy
import eu.kanade.tachiyomi.di.AppDependencies
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO

class StatsScreenModel private constructor(
    private val suwayomiProvider: SuwayomiClientProvider,
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    internal constructor(dependencies: AppDependencies) : this(dependencies.suwayomiClientProvider)

    private val suwayomiClient = suwayomiProvider.graphQlClient

    init {
        screenModelScope.launchIO {
            refreshStats()
        }

        screenModelScope.launchIO {
            ServerStateSync.invalidations
                .filter(ServerStateInvalidation::affectsStats)
                .collectLatest {
                    refreshStats()
                }
        }
    }

    private suspend fun refreshStats() {
        val serverStats = suwayomiClient.getServerStats()
        val libraryManga = serverStats.statisticsMangas.nodes
        val loggedInTrackerIds = serverStats.trackers.nodes
            .filter { it.isLoggedIn }
            .map { it.id }
            .toHashSet()

        val mangaTrackMap = getMangaTrackMap(libraryManga, loggedInTrackerIds)
        val scoredMangaTrackerMap = getScoredMangaTrackMap(mangaTrackMap)

        val overviewStatData = StatsData.Overview(
            libraryMangaCount = serverStats.statisticsMangas.totalCount,
            completedMangaCount = libraryManga.count {
                it.status == MangaStatus.COMPLETED && it.unreadCount == 0L
            },
            totalReadDuration = 0L,
        )

        val titlesStatData = StatsData.Titles(
            globalUpdateItemCount = getGlobalUpdateItemCount(libraryManga, serverStats.settings),
            startedMangaCount = libraryManga.count { it.hasStarted },
            localMangaCount = libraryManga.count { it.sourceId == LOCAL_SOURCE_ID },
        )

        val chaptersStatData = StatsData.Chapters(
            totalChapterCount = serverStats.totalChapters.totalCount,
            readChapterCount = serverStats.readChapters.totalCount,
            downloadCount = serverStats.downloadedChapters.totalCount,
        )

        val trackersStatData = StatsData.Trackers(
            trackedTitleCount = mangaTrackMap.count { it.value.isNotEmpty() },
            meanScore = getTrackMeanScore(scoredMangaTrackerMap),
            trackerCount = loggedInTrackerIds.size,
        )

        mutableState.update {
            StatsScreenState.Success(
                overview = overviewStatData,
                titles = titlesStatData,
                chapters = chaptersStatData,
                trackers = trackersStatData,
            )
        }
    }

    private fun getGlobalUpdateItemCount(
        libraryManga: List<SuwayomiStatsMangaDto>,
        settings: SuwayomiServerSettingsDto,
    ): Int {
        val hasIncludedCategories = libraryManga.any { manga ->
            manga.categories.nodes.any { it.includeInUpdate == SuwayomiCategoryFlag.INCLUDE }
        }

        return libraryManga.count { manga ->
            manga.updateStrategy == UpdateStrategy.ALWAYS_UPDATE &&
                (
                    !hasIncludedCategories ||
                        manga.categories.nodes.any { it.includeInUpdate == SuwayomiCategoryFlag.INCLUDE }
                    ) &&
                manga.categories.nodes.none { it.includeInUpdate == SuwayomiCategoryFlag.EXCLUDE } &&
                (!settings.excludeUnreadChapters || manga.unreadCount == 0L) &&
                (!settings.excludeNotStarted || !manga.initialized || manga.hasStarted) &&
                (!settings.excludeCompleted || manga.status != MangaStatus.COMPLETED) &&
                (!settings.excludeEntryWithUnreadChapters || manga.unreadCount == 0L)
        }
    }

    private fun getMangaTrackMap(
        libraryManga: List<SuwayomiStatsMangaDto>,
        loggedInTrackerIds: Set<Int>,
    ): Map<Int, List<SuwayomiStatsTrackRecordDto>> {
        return libraryManga.associate { manga ->
            val tracks = manga.trackRecords.nodes
                .fastFilter { it.trackerId in loggedInTrackerIds }

            manga.id to tracks
        }
    }

    private fun getScoredMangaTrackMap(
        mangaTrackMap: Map<Int, List<SuwayomiStatsTrackRecordDto>>,
    ): Map<Int, List<SuwayomiStatsTrackRecordDto>> {
        return mangaTrackMap.mapNotNull { (mangaId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            mangaId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredMangaTrackMap: Map<Int, List<SuwayomiStatsTrackRecordDto>>): Double {
        return scoredMangaTrackMap
            .map { (_, tracks) -> tracks.map { it.score }.average() }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private val SuwayomiStatsMangaDto.hasStarted: Boolean
        get() = latestReadChapter != null

    private companion object {
        private const val LOCAL_SOURCE_ID = "0"
    }
}

private fun ServerStateInvalidation.affectsStats(): Boolean {
    return affectsAny(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.Downloads,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
        ServerStateEntity.ServerSettings,
    ) || affected.any { entity ->
        entity is ServerStateEntity.Manga ||
            entity is ServerStateEntity.Chapters ||
            entity is ServerStateEntity.Trackers
    }
}
