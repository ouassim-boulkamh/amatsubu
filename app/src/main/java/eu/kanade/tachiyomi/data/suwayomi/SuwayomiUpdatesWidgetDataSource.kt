package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.widget.UpdatesWidgetDataSource
import tachiyomi.presentation.widget.UpdatesWidgetItem

internal class SuwayomiUpdatesWidgetDataSource(
    private val clientProvider: SuwayomiClientProvider = SuwayomiClientProvider(),
) : UpdatesWidgetDataSource {

    override fun subscribe(limit: Int): Flow<List<UpdatesWidgetItem>> {
        if (limit <= 0) {
            return flowOf(emptyList())
        }

        return merge(
            flowOf(Unit),
            ServerStateSync.refreshes.map { Unit },
        ).map {
            runCatching {
                loadUpdates(limit)
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to load Suwayomi updates widget data" }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun loadUpdates(limit: Int): List<UpdatesWidgetItem> {
        val baseUrl = clientProvider.baseUrl()
        return clientProvider.graphQlClient
            .getRecentChapters(limit = limit)
            .distinctBy { it.manga.id }
            .take(limit)
            .map { chapter ->
                UpdatesWidgetItem(
                    mangaId = chapter.manga.id.toLong(),
                    coverUrl = chapter.manga.thumbnailUrl?.let { resolveServerUrl(baseUrl, it) },
                )
            }
    }
}
