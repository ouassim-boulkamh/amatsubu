package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryFlag
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.di.AppDependencies
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import eu.kanade.domain.category.model.Category
import tachiyomi.i18n.MR

class CategoryScreenModel private constructor(
    private val suwayomiProvider: SuwayomiClientProvider,
) : StateScreenModel<CategoryScreenState>(CategoryScreenState.Loading) {

    internal constructor(dependencies: AppDependencies) : this(dependencies.suwayomiClientProvider)

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val suwayomiClient = suwayomiProvider.graphQlClient

    init {
        screenModelScope.launchIO {
            refreshCategories()
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launchIO {
            runServerCategoryMutation {
                val nextOrder = successState?.categories
                    ?.maxOfOrNull { it.order }
                    ?.plus(1)
                    ?.toInt()
                    ?: 0
                suwayomiClient.createCategory(name = name, order = nextOrder)
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launchIO {
            runServerCategoryMutation {
                suwayomiClient.deleteCategory(categoryId.toInt())
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launchIO {
            runServerCategoryMutation {
                suwayomiClient.updateCategoryOrder(category.id.toInt(), newIndex)
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launchIO {
            runServerCategoryMutation {
                suwayomiClient.updateCategoryName(category.id.toInt(), name)
            }
        }
    }

    fun updateCategoryFlags(
        category: Category,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ) {
        screenModelScope.launchIO {
            runServerCategoryMutation {
                suwayomiClient.updateCategoryFlags(
                    categoryId = category.id.toInt(),
                    includeInUpdate = includeInUpdate,
                    includeInDownload = includeInDownload,
                )
            }
        }
    }

    fun showDialog(dialog: CategoryDialog) {
        mutableState.update {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }

    private val successState: CategoryScreenState.Success?
        get() = state.value as? CategoryScreenState.Success

    private suspend fun refreshCategories() {
        runServerCategoryAction {
            val serverCategories = suwayomiClient.getCategories()
            applyCategories(serverCategories)
        }
    }

    private suspend fun runServerCategoryAction(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to update Suwayomi categories" }
            _events.send(CategoryEvent.InternalError)
        }
    }

    private suspend fun runServerCategoryMutation(mutation: suspend () -> Unit) {
        when (
            val result = runCategoryMutationWithSharedRefresh(
                mutation = mutation,
                refetchCategories = suwayomiClient::getCategories,
                applyCategories = ::applyCategories,
                requestSharedRefresh = { affected ->
                    ServerStateSync.requestRefresh(*affected.toTypedArray())
                },
            )
        ) {
            CategoryMutationRefreshResult.AcceptedFresh -> Unit
            is CategoryMutationRefreshResult.AcceptedRefreshFailed -> {
                logcat(LogPriority.ERROR, result.error) {
                    "Suwayomi accepted category mutation but category refresh failed"
                }
                _events.send(CategoryEvent.InternalError)
            }
            is CategoryMutationRefreshResult.MutationFailed -> {
                logcat(LogPriority.ERROR, result.error) { "Failed to update Suwayomi categories" }
                _events.send(CategoryEvent.InternalError)
            }
        }
    }

    private fun applyCategories(serverCategories: List<SuwayomiCategoryDto>) {
        mutableState.update {
            CategoryScreenState.Success(
                categories = serverCategories
                    .map { category -> category.toCategory() },
                categoryFlags = serverCategories.associate { category ->
                    category.id.toLong() to category.toFlags()
                },
            )
        }
    }

    private fun SuwayomiCategoryDto.toCategory(): Category {
        return Category(
            id = id.toLong(),
            name = name,
            order = order.toLong(),
            flags = 0L,
        )
    }

    private fun SuwayomiCategoryDto.toFlags(): CategoryFlagSettings {
        return CategoryFlagSettings(
            includeInUpdate = includeInUpdate,
            includeInDownload = includeInDownload,
        )
    }
}

sealed interface CategoryDialog {
    data object Create : CategoryDialog
    data class Rename(val category: Category) : CategoryDialog
    data class Delete(val category: Category) : CategoryDialog
    data class EditFlags(val category: Category) : CategoryDialog
}

@Immutable
data class CategoryFlagSettings(
    val includeInUpdate: SuwayomiCategoryFlag = SuwayomiCategoryFlag.UNSET,
    val includeInDownload: SuwayomiCategoryFlag = SuwayomiCategoryFlag.UNSET,
)

sealed interface CategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface CategoryScreenState {

    @Immutable
    data object Loading : CategoryScreenState

    @Immutable
    data class Success(
        val categories: List<Category>,
        val categoryFlags: Map<Long, CategoryFlagSettings> = emptyMap(),
        val dialog: CategoryDialog? = null,
    ) : CategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
