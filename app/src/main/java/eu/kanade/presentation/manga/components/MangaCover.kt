package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.delay

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
    ) {
        val placeholderPainter = remember { ColorPainter(CoverPlaceholderColor) }
        val errorPainter = rememberResourceBitmapPainter(id = R.drawable.cover_error)
        var retryAttempt by remember(data) { mutableIntStateOf(0) }
        var failedAttempt by remember(data) { mutableIntStateOf(-1) }

        LaunchedEffect(data, failedAttempt) {
            val nextRetry = failedAttempt + 1
            if (failedAttempt >= 0 && failedAttempt == retryAttempt && nextRetry <= CoverRetryDelays.size) {
                delay(CoverRetryDelays[failedAttempt])
                retryAttempt = nextRetry
            }
        }

        key(data, retryAttempt) {
            AsyncImage(
                model = data,
                placeholder = placeholderPainter,
                error = if (retryAttempt < CoverRetryDelays.size) placeholderPainter else errorPainter,
                contentDescription = contentDescription,
                modifier = modifier
                    .aspectRatio(ratio)
                    .clip(shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = onClick,
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.Crop,
                onError = {
                    failedAttempt = retryAttempt
                },
            )
        }
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
private val CoverRetryDelays = longArrayOf(250, 750, 1500)
