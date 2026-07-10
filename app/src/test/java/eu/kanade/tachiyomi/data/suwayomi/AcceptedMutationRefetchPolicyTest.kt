package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AcceptedMutationRefetchPolicyTest {

    @Test
    fun `download queue accepted mutation returns fresh refetch result`() = runTest {
        val client = FakeAcceptedMutationClient(refetchResult = "fresh queue")

        val result = runAcceptedMutationWithRefetch(
            mutation = client::mutate,
            refetch = client::refetch,
        )

        val fresh = assertInstanceOf(AcceptedMutationRefetchResult.AcceptedFresh::class.java, result)
        assertEquals("fresh queue", fresh.value)
        assertEquals(1, client.mutationCalls)
        assertEquals(1, client.refetchCalls)
    }

    @Test
    fun `download queue accepted mutation with failed refetch is not reported as mutation failure`() = runTest {
        val refreshError = IllegalStateException("status reload failed")
        val client = FakeAcceptedMutationClient(refetchError = refreshError)

        val result = runAcceptedMutationWithRefetch(
            mutation = client::mutate,
            refetch = client::refetch,
        )

        val acceptedRefreshFailed = assertInstanceOf(
            AcceptedMutationRefetchResult.AcceptedRefreshFailed::class.java,
            result,
        )
        assertEquals(refreshError, acceptedRefreshFailed.error)
        assertEquals(1, client.mutationCalls)
        assertEquals(1, client.refetchCalls)
    }

    @Test
    fun `manga detail accepted mutation with failed refetch is not reported as mutation failure`() = runTest {
        assertAcceptedRefreshFailureIsSeparated(
            refetchError = IllegalStateException("manga reload failed"),
            expectedMutationCalls = 1,
            expectedRefetchCalls = 1,
            mutation = { client -> client.mutate("toggle-library") },
            refetch = { client -> client.refetch("manga-detail") },
        )
    }

    @Test
    fun `library category accepted mutation with failed refetch is not reported as mutation failure`() = runTest {
        assertAcceptedRefreshFailureIsSeparated(
            refetchError = IllegalStateException("library categories reload failed"),
            expectedMutationCalls = 1,
            expectedRefetchCalls = 1,
            mutation = { client -> client.mutate("set-categories") },
            refetch = { client -> client.refetch("library") },
        )
    }

    @Test
    fun `updates accepted mutation with failed refetch is not reported as mutation failure`() = runTest {
        assertAcceptedRefreshFailureIsSeparated(
            refetchError = IllegalStateException("updates reload failed"),
            expectedMutationCalls = 1,
            expectedRefetchCalls = 1,
            mutation = { client -> client.mutate("mark-updates-read") },
            refetch = { client -> client.refetch("recent-chapters") },
        )
    }

    @Test
    fun `tracker accepted mutation with failed refetch is not reported as mutation failure`() = runTest {
        assertAcceptedRefreshFailureIsSeparated(
            refetchError = IllegalStateException("track records reload failed"),
            expectedMutationCalls = 1,
            expectedRefetchCalls = 1,
            mutation = { client -> client.mutate("update-track") },
            refetch = { client -> client.refetch("track-records") },
        )
    }

    @Test
    fun `failed mutation does not refetch stale server state`() = runTest {
        val mutationError = IllegalStateException("mutation rejected")
        val client = FakeAcceptedMutationClient(mutationError = mutationError)

        val result = runAcceptedMutationWithRefetch(
            mutation = client::mutate,
            refetch = client::refetch,
        )

        val failed = assertInstanceOf(AcceptedMutationRefetchResult.MutationFailed::class.java, result)
        assertEquals(mutationError, failed.error)
        assertEquals(1, client.mutationCalls)
        assertEquals(0, client.refetchCalls)
    }

    @Test
    fun `cancellation is propagated from mutation and refetch`() {
        val mutationCancellation = CancellationException("mutation cancelled")
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runAcceptedMutationWithRefetch(
                    mutation = { throw mutationCancellation },
                    refetch = { "unused" },
                )
            }
        }

        val refetchCancellation = CancellationException("refetch cancelled")
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runAcceptedMutationWithRefetch(
                    mutation = {},
                    refetch = { throw refetchCancellation },
                )
            }
        }
    }
}

private suspend fun assertAcceptedRefreshFailureIsSeparated(
    refetchError: Throwable,
    expectedMutationCalls: Int,
    expectedRefetchCalls: Int,
    mutation: suspend (FakeAcceptedMutationClient) -> Unit,
    refetch: suspend (FakeAcceptedMutationClient) -> String,
) {
    val client = FakeAcceptedMutationClient(refetchError = refetchError)

    val result = runAcceptedMutationWithRefetch(
        mutation = { mutation(client) },
        refetch = { refetch(client) },
    )

    val acceptedRefreshFailed = assertInstanceOf(
        AcceptedMutationRefetchResult.AcceptedRefreshFailed::class.java,
        result,
    )
    assertEquals(refetchError, acceptedRefreshFailed.error)
    assertEquals(expectedMutationCalls, client.mutationCalls)
    assertEquals(expectedRefetchCalls, client.refetchCalls)
}

private class FakeAcceptedMutationClient(
    private val refetchResult: String = "queue",
    private val mutationError: Throwable? = null,
    private val refetchError: Throwable? = null,
) {
    var mutationCalls = 0
        private set
    var refetchCalls = 0
        private set

    suspend fun mutate(operation: String = "mutation") {
        check(operation.isNotBlank())
        mutationCalls += 1
        mutationError?.let { throw it }
    }

    suspend fun refetch(operation: String = "refetch"): String {
        check(operation.isNotBlank())
        refetchCalls += 1
        refetchError?.let { throw it }
        return refetchResult
    }
}
