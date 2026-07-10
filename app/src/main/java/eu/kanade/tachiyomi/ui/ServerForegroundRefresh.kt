package eu.kanade.tachiyomi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun ServerForegroundRefreshEffect(
    enabled: Boolean = true,
    interval: Duration = SERVER_FOREGROUND_REFRESH_INTERVAL,
    onRefresh: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(lifecycleOwner, enabled, interval) {
        if (!enabled) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(interval)
                currentRefresh()
            }
        }
    }
}

internal val SERVER_FOREGROUND_REFRESH_INTERVAL = 30.seconds
