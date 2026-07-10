package eu.kanade.presentation.track

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.random.Random

internal class TrackerSearchPreviewProvider : PreviewParameterProvider<@Composable () -> Unit> {
    private val fullPageWithSecondSelected = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithoutSelected = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = Result.success(someTrackSearches().take(30).toList()),
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val loading = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = null,
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithPrivateTracking = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = true,
        )
    }
    override val values: Sequence<@Composable () -> Unit> = sequenceOf(
        fullPageWithSecondSelected,
        fullPageWithoutSelected,
        loading,
        fullPageWithPrivateTracking,
    )

    private fun someTrackSearches(): Sequence<ServerTrackSearchResult> = sequence {
        while (true) {
            yield(randTrackSearch())
        }
    }

    private val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun randTrackSearch() = ServerTrackSearchResult(
        id = Random.nextLong(),
        remoteId = Random.nextLong(),
        title = lorem((1..10).random()).joinToString(),
        coverUrl = "https://example.com/cover.png",
        startDate = formatter.format(Date.from(Instant.now().minus((1L..365).random(), ChronoUnit.DAYS))),
        summary = lorem((0..40).random()).joinToString(),
        publishingStatus = if (Random.nextBoolean()) "Finished" else "",
        publishingType = if (Random.nextBoolean()) "Oneshot" else "",
        score = (0..10).random().toDouble(),
        artists = randomNames(),
        authors = randomNames(),
        trackingUrl = "https://example.com/tracker-example",
    )

    private fun randomNames(): List<String> = (0..(0..3).random()).map { lorem((3..5).random()).joinToString() }

    private fun lorem(words: Int): Sequence<String> =
        LoremIpsum(words).values
}
