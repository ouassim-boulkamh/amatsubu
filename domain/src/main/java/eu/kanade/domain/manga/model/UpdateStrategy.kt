package eu.kanade.domain.manga.model

import kotlinx.serialization.Serializable

@Serializable
enum class UpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}
