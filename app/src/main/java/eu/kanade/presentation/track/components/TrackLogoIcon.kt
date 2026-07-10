package eu.kanade.presentation.track.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.presentation.core.util.clickableNoIndication

@Composable
fun TrackLogoIcon(
    @androidx.annotation.DrawableRes logoRes: Int,
    name: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) {
        Modifier.clickableNoIndication(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }

    Image(
        painter = painterResource(logoRes),
        contentDescription = name,
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium),
    )
}

@PreviewLightDark
@Composable
private fun TrackLogoIconPreviews(
    @PreviewParameter(TrackLogoIconPreviewProvider::class)
    logoRes: Int,
) {
    TachiyomiPreviewTheme {
        TrackLogoIcon(
            logoRes = logoRes,
            name = "Example tracker",
            onClick = null,
            onLongClick = null,
        )
    }
}
