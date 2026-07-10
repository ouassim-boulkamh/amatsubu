package eu.kanade.tachiyomi.ui.reader.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream

open class ReaderPage(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) {

    val number: Int
        get() = index + 1

    private val _statusFlow = MutableStateFlow<State>(State.Queue)

    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    private val _progressFlow = MutableStateFlow(0)

    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    open lateinit var chapter: ReaderChapter

    fun updateProgress(bytesRead: Long, contentLength: Long) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    sealed interface State {
        data object Queue : State
        data object LoadPage : State
        data object DownloadImage : State
        data object Ready : State
        data class Error(val error: Throwable) : State
    }
}
