package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class WidgetManager(
    private val updatesWidgetDataSource: UpdatesWidgetDataSource,
    private val securityPreferences: SecurityPreferences,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        combine(
            updatesWidgetDataSource.subscribe(WIDGET_REFRESH_LIMIT),
            securityPreferences.useAuthenticator.changes(),
            transform = { a, b -> a to b },
        )
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.map { it.mangaId }.toSet() == new.first.map { it.mangaId }.toSet()
            }
            .onEach {
                try {
                    UpdatesGridGlanceWidget().updateAll(this)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }

    private companion object {
        const val WIDGET_REFRESH_LIMIT = 24
    }
}
