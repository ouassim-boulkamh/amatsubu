package eu.kanade.tachiyomi.ui.category

import eu.kanade.tachiyomi.data.suwayomi.ServerStateEntity
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CategoryMutationRefreshPolicyTest {

    @Test
    fun `accepted category mutation applies fresh categories and requests shared refresh`() = runTest {
        val client = FakeCategoryMutationClient(
            categories = listOf(category(id = 1, name = "Reading")),
        )
        val appliedCategories = mutableListOf<List<SuwayomiCategoryDto>>()
        val sharedRefreshes = mutableListOf<Set<ServerStateEntity>>()

        val result = runCategoryMutationWithSharedRefresh(
            mutation = client::mutate,
            refetchCategories = client::refetchCategories,
            applyCategories = appliedCategories::add,
            requestSharedRefresh = sharedRefreshes::add,
        )

        assertEquals(CategoryMutationRefreshResult.AcceptedFresh, result)
        assertEquals(1, client.mutationCalls)
        assertEquals(1, client.refetchCalls)
        assertEquals(listOf(listOf(category(id = 1, name = "Reading"))), appliedCategories)
        assertEquals(listOf(setOf(ServerStateEntity.Categories, ServerStateEntity.Library)), sharedRefreshes)
    }

    @Test
    fun `accepted category mutation still requests shared refresh when refetch fails`() = runTest {
        val refetchError = IllegalStateException("categories reload failed")
        val client = FakeCategoryMutationClient(refetchError = refetchError)
        val appliedCategories = mutableListOf<List<SuwayomiCategoryDto>>()
        val sharedRefreshes = mutableListOf<Set<ServerStateEntity>>()

        val result = runCategoryMutationWithSharedRefresh(
            mutation = client::mutate,
            refetchCategories = client::refetchCategories,
            applyCategories = appliedCategories::add,
            requestSharedRefresh = sharedRefreshes::add,
        )

        val failed = assertInstanceOf(CategoryMutationRefreshResult.AcceptedRefreshFailed::class.java, result)
        assertEquals(refetchError, failed.error)
        assertEquals(1, client.mutationCalls)
        assertEquals(1, client.refetchCalls)
        assertEquals(emptyList<List<SuwayomiCategoryDto>>(), appliedCategories)
        assertEquals(listOf(setOf(ServerStateEntity.Categories, ServerStateEntity.Library)), sharedRefreshes)
    }

    @Test
    fun `rejected category mutation does not refetch or request shared refresh`() = runTest {
        val mutationError = IllegalStateException("category update rejected")
        val client = FakeCategoryMutationClient(mutationError = mutationError)
        val appliedCategories = mutableListOf<List<SuwayomiCategoryDto>>()
        val sharedRefreshes = mutableListOf<Set<ServerStateEntity>>()

        val result = runCategoryMutationWithSharedRefresh(
            mutation = client::mutate,
            refetchCategories = client::refetchCategories,
            applyCategories = appliedCategories::add,
            requestSharedRefresh = sharedRefreshes::add,
        )

        val failed = assertInstanceOf(CategoryMutationRefreshResult.MutationFailed::class.java, result)
        assertEquals(mutationError, failed.error)
        assertEquals(1, client.mutationCalls)
        assertEquals(0, client.refetchCalls)
        assertEquals(emptyList<List<SuwayomiCategoryDto>>(), appliedCategories)
        assertEquals(emptyList<Set<ServerStateEntity>>(), sharedRefreshes)
    }

    @Test
    fun `cancellation is propagated from category mutation and refetch`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runCategoryMutationWithSharedRefresh(
                    mutation = { throw CancellationException("mutation cancelled") },
                    refetchCategories = { emptyList() },
                    applyCategories = {},
                    requestSharedRefresh = { _ -> },
                )
            }
        }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runCategoryMutationWithSharedRefresh(
                    mutation = {},
                    refetchCategories = { throw CancellationException("refetch cancelled") },
                    applyCategories = {},
                    requestSharedRefresh = { _ -> },
                )
            }
        }
    }
}

private class FakeCategoryMutationClient(
    private val categories: List<SuwayomiCategoryDto> = emptyList(),
    private val mutationError: Throwable? = null,
    private val refetchError: Throwable? = null,
) {
    var mutationCalls = 0
        private set
    var refetchCalls = 0
        private set

    suspend fun mutate() {
        mutationCalls += 1
        mutationError?.let { throw it }
    }

    suspend fun refetchCategories(): List<SuwayomiCategoryDto> {
        refetchCalls += 1
        refetchError?.let { throw it }
        return categories
    }
}

private fun category(
    id: Int,
    name: String,
): SuwayomiCategoryDto {
    return SuwayomiCategoryDto(
        id = id,
        name = name,
        order = id,
    )
}
