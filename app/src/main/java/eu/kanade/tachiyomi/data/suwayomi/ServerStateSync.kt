package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ServerStateSync {
    private val _refreshes = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    val refreshes: Flow<Long> = _refreshes.asSharedFlow()

    fun requestRefresh() {
        _refreshes.tryEmit(System.currentTimeMillis())
    }
}
