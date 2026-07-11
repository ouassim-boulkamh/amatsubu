package eu.kanade.tachiyomi.ui.category

import eu.kanade.tachiyomi.data.suwayomi.AcceptedMutationRefetchResult
import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.runAcceptedMutationWithRefetch

internal sealed interface CategoryMutationRefreshResult {
    data object AcceptedFresh : CategoryMutationRefreshResult
    data class AcceptedRefreshFailed(val error: Throwable) : CategoryMutationRefreshResult
    data class MutationFailed(val error: Throwable) : CategoryMutationRefreshResult
}

internal suspend fun runCategoryMutationWithSharedRefresh(
    mutation: suspend () -> Unit,
    refetchCategories: suspend () -> List<SuwayomiCategoryDto>,
    applyCategories: (List<SuwayomiCategoryDto>) -> Unit,
    requestSharedRefresh: (Set<ServerStateEntity>) -> Unit,
): CategoryMutationRefreshResult {
    val affected = setOf(ServerStateEntity.Categories, ServerStateEntity.Library)
    return when (
        val result = runAcceptedMutationWithRefetch(
            mutation = mutation,
            refetch = refetchCategories,
        )
    ) {
        is AcceptedMutationRefetchResult.AcceptedFresh -> {
            applyCategories(result.value)
            requestSharedRefresh(affected)
            CategoryMutationRefreshResult.AcceptedFresh
        }
        is AcceptedMutationRefetchResult.AcceptedRefreshFailed -> {
            requestSharedRefresh(affected)
            CategoryMutationRefreshResult.AcceptedRefreshFailed(result.error)
        }
        is AcceptedMutationRefetchResult.MutationFailed -> {
            CategoryMutationRefreshResult.MutationFailed(result.error)
        }
    }
}
