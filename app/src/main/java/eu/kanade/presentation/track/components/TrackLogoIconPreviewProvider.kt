package eu.kanade.presentation.track.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.R

internal class TrackLogoIconPreviewProvider : PreviewParameterProvider<Int> {

    override val values: Sequence<Int>
        get() = sequenceOf(
            R.drawable.brand_anilist,
        )
}
