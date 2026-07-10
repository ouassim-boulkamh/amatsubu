package tachiyomi.presentation.widget

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface UpdatesWidgetDataSource {
    fun subscribe(limit: Int): Flow<List<UpdatesWidgetItem>>
}

interface UpdatesWidgetDependenciesProvider {
    fun updatesWidgetDataSource(): UpdatesWidgetDataSource

    fun isWidgetLocked(): Boolean
}

internal val Context.updatesWidgetDependencies: UpdatesWidgetDependenciesProvider
    get() = applicationContext as? UpdatesWidgetDependenciesProvider
        ?: error("Application must provide updates widget dependencies")

data class UpdatesWidgetItem(
    val mangaId: Long,
    val coverUrl: String?,
)
