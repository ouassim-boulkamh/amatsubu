package eu.kanade.tachiyomi.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.domain.library.service.LibraryPreferences
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal class LibrarySettingsScreenModel(
    val preferences: BasePreferences,
    val libraryPreferences: LibraryPreferences,
    private val suwayomiProvider: SuwayomiClientProvider,
) : ScreenModel {

    private val suwayomiClient = suwayomiProvider.graphQlClient

    val trackersFlow = flow {
        emit(
            runCatching {
                suwayomiClient.trackerList().filter { it.isLoggedIn }
            }.getOrDefault(emptyList()),
        )
    }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        libraryPreferences.displayMode.set(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        screenModelScope.launchIO {
            val sort = LibrarySort(mode, direction)
            if (mode == LibrarySort.Type.Random) {
                libraryPreferences.randomSortSeed.set(Random.nextInt())
            }
            if (category != null && libraryPreferences.categorizedDisplaySettings.get()) {
                libraryPreferences.categorySortingModes.getAndSet { it + (category.id to sort) }
            } else {
                libraryPreferences.sortingMode.set(sort)
                libraryPreferences.categorySortingModes.set(emptyMap())
            }
        }
    }
}
