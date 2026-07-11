package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.BindTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.BindTrackRecordMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LoginTrackerCredentialsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LoginTrackerOAuthMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.LogoutTrackerMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SearchTrackerQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackProgressMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackRecordsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.TrackerListQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UnbindTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateTrackMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.BindTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.BindTrackRecordInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginTrackerCredentialsInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LoginTrackerOAuthInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LogoutTrackerInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SearchTrackerInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.TrackProgressInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UnbindTrackInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateTrackInput

internal class SuwayomiTrackerGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
) {

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
}
