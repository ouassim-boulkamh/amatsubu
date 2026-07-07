package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations

interface HistoryRepository {

    fun getHistory(query: String): Flow<List<HistoryWithRelations>>

    suspend fun getLastHistory(): HistoryWithRelations?

    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)
}
