package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiCategoryFlag
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoryScreenModel : StateScreenModel<CategoryScreenState>(CategoryScreenState.Loading) {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val suwayomiClient = SuwayomiClientProvider().graphQlClient

    init {
        screenModelScope.launchIO {
            refreshCategories()
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launchIO {
            runServerCategoryAction {
                val nextOrder = successState?.categories
                    ?.maxOfOrNull { it.order }
                    ?.plus(1)
                    ?.toInt()
                    ?: 0
                suwayomiClient.createCategory(name = name, order = nextOrder)
                refreshCategories()
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launchIO {
            runServerCategoryAction {
                suwayomiClient.deleteCategory(categoryId.toInt())
                refreshCategories()
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launchIO {
            runServerCategoryAction {
                suwayomiClient.updateCategoryOrder(category.id.toInt(), newIndex)
                refreshCategories()
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launchIO {
            runServerCategoryAction {
                suwayomiClient.updateCategoryName(category.id.toInt(), name)
                refreshCategories()
            }
        }
    }

    fun updateCategoryFlags(
        category: Category,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ) {
        screenModelScope.launchIO {
            runServerCategoryAction {
                suwayomiClient.updateCategoryFlags(
                    categoryId = category.id.toInt(),
                    includeInUpdate = includeInUpdate,
                    includeInDownload = includeInDownload,
                )
                refreshCategories()
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
    }

    private suspend fun runServerCategoryAction(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to update Suwayomi categories" }
            _events.send(CategoryEvent.InternalError)
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
