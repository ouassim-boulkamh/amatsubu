package tachiyomi.presentation.widget

import kotlinx.coroutines.flow.Flow

interface UpdatesWidgetDataSource {
    fun subscribe(limit: Int): Flow<List<UpdatesWidgetItem>>
}

data class UpdatesWidgetItem(
    val mangaId: Long,
    val coverUrl: String?,
)
