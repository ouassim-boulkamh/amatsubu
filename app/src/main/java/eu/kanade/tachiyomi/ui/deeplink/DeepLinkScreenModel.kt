package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO

class DeepLinkScreenModel(
    query: String = "",
) : StateScreenModel<DeepLinkScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            mutableState.update {
                State.NoResults(query)
            }
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class NoResults(val query: String) : State
    }
}
