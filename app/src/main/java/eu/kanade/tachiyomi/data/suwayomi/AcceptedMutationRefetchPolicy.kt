package eu.kanade.tachiyomi.data.suwayomi

import kotlin.coroutines.cancellation.CancellationException

internal sealed interface AcceptedMutationRefetchResult<out T> {
    data class AcceptedFresh<T>(val value: T) : AcceptedMutationRefetchResult<T>
    data class AcceptedRefreshFailed(val error: Throwable) : AcceptedMutationRefetchResult<Nothing>
    data class MutationFailed(val error: Throwable) : AcceptedMutationRefetchResult<Nothing>
}

internal suspend fun <T> runAcceptedMutationWithRefetch(
    mutation: suspend () -> Unit,
    refetch: suspend () -> T,
): AcceptedMutationRefetchResult<T> {
    try {
        mutation()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        return AcceptedMutationRefetchResult.MutationFailed(error)
    }

    return try {
        AcceptedMutationRefetchResult.AcceptedFresh(refetch())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        AcceptedMutationRefetchResult.AcceptedRefreshFailed(error)
    }
}
