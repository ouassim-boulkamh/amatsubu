package eu.kanade.presentation.track

import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource

/**
 * Presentation-only projection of tracker state returned by Suwayomi.
 *
 * Tracker records remain server-owned; this model deliberately has no local
 * persistence or mutation methods.
 */
data class ServerTrackRecord(
    val id: Long,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
    val private: Boolean,
)

data class ServerTrackerPresentation(
    val id: Long,
    val name: String,
    @DrawableRes val logoRes: Int,
    val supportsReadingDates: Boolean,
    val supportsPrivateTracking: Boolean,
    val supportsTrackDeletion: Boolean,
    val statuses: List<Status>,
    val scores: List<String>,
    private val displayScores: Map<Long, String> = emptyMap(),
) {
    data class Status(val value: Long, val label: StringResource?)

    fun statusLabel(status: Long): StringResource? = statuses.firstOrNull { it.value == status }?.label

    fun displayScore(track: ServerTrackRecord): String =
        displayScores[track.id]?.takeIf { it.isNotBlank() } ?: track.score.toString()
}

data class ServerTrackItem(
    val track: ServerTrackRecord?,
    val tracker: ServerTrackerPresentation,
)

data class ServerTrackSearchResult(
    val id: Long,
    val remoteId: Long,
    val title: String,
    val coverUrl: String,
    val summary: String,
    val publishingStatus: String,
    val publishingType: String,
    val startDate: String,
    val score: Double,
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val trackingUrl: String = "",
)
