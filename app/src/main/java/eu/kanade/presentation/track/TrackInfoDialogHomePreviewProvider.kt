package eu.kanade.presentation.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val tracker = ServerTrackerPresentation(
        id = 1L,
        name = "Example Tracker",
        logoRes = R.drawable.brand_anilist,
        supportsReadingDates = false,
        supportsPrivateTracking = true,
        supportsTrackDeletion = false,
        statuses = emptyList(),
        scores = (0..10).map(Int::toString),
    )
    private val aTrack = ServerTrackRecord(
        id = 1L,
        title = "Manage Name On Tracker Site",
        lastChapterRead = 2.0,
        totalChapters = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
    private val privateTrack = aTrack.copy(private = true)
    private val trackItemWithoutTrack = ServerTrackItem(
        track = null,
        tracker = tracker,
    )
    private val trackItemWithTrack = ServerTrackItem(
        track = aTrack,
        tracker = tracker,
    )
    private val trackItemWithPrivateTrack = ServerTrackItem(
        track = privateTrack,
        tracker = tracker,
    )

    private val trackersWithAndWithoutTrack = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val noTrackers = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val trackerWithPrivateTracking = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    override val values: Sequence<@Composable () -> Unit>
        get() = sequenceOf(
            trackersWithAndWithoutTrack,
            noTrackers,
            trackerWithPrivateTracking,
        )
}
