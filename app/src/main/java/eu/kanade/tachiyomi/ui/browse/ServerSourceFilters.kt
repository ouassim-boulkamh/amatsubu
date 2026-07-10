package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSortSelectionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterChange
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTriState
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilter
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterList

internal fun List<SuwayomiSourceFilterDto>.toSourceFilterList(): SourceFilterList {
    return SourceFilterList(mapIndexedNotNull { index, dto -> dto.toSourceFilter(index) })
}

private fun SuwayomiSourceFilterDto.toSourceFilter(position: Int): SourceFilter? {
    return when (type) {
        "CheckBoxFilter" -> SourceFilter.CheckBox(name, defaultBoolean ?: false)
        "HeaderFilter" -> SourceFilter.Header(name)
        "SeparatorFilter" -> SourceFilter.Separator(name)
        "SelectFilter" -> SourceFilter.Select(name, values, defaultInt ?: 0)
        "SortFilter" -> SourceFilter.Sort(
            name = name,
            values = values,
            state = defaultSort?.let { SourceFilter.Sort.Selection(it.index, it.ascending) },
        )
        "TextFilter" -> SourceFilter.Text(name, defaultString.orEmpty())
        "TriStateFilter" -> SourceFilter.TriState(name, defaultTriState.toSourceFilterState())
        "GroupFilter" -> SourceFilter.Group(
            name = name,
            state = filters.mapIndexedNotNull { childIndex, child -> child.toSourceFilter(childIndex) },
        )
        else -> null
    }
}

internal fun List<SuwayomiSourceFilterDto>.toFilterChanges(
    filters: SourceFilterList,
): List<SuwayomiSourceFilterChange> {
    return flatMapIndexed { index, dto ->
        val filter = filters.getOrNull(index) ?: return@flatMapIndexed emptyList()
        dto.toFilterChanges(index, filter)
    }
}

private fun SuwayomiSourceFilterDto.toFilterChanges(
    position: Int,
    filter: SourceFilter,
): List<SuwayomiSourceFilterChange> {
    return when {
        this.type == "CheckBoxFilter" && filter is SourceFilter.CheckBox &&
            filter.state != (defaultBoolean ?: false) -> {
            listOf(SuwayomiSourceFilterChange(position = position, checkBoxState = filter.state))
        }
        this.type == "SelectFilter" && filter is SourceFilter.Select &&
            filter.state != (defaultInt ?: 0) -> {
            listOf(SuwayomiSourceFilterChange(position = position, selectState = filter.state))
        }
        this.type == "SortFilter" && filter is SourceFilter.Sort &&
            filter.state != defaultSort?.let { SourceFilter.Sort.Selection(it.index, it.ascending) } -> {
            listOf(
                SuwayomiSourceFilterChange(
                    position = position,
                    sortState = filter.state?.let { SuwayomiSortSelectionDto(it.index, it.ascending) },
                ),
            )
        }
        this.type == "TextFilter" && filter is SourceFilter.Text &&
            filter.state != defaultString.orEmpty() -> {
            listOf(SuwayomiSourceFilterChange(position = position, textState = filter.state))
        }
        this.type == "TriStateFilter" && filter is SourceFilter.TriState &&
            filter.state != defaultTriState.toSourceFilterState() -> {
            listOf(SuwayomiSourceFilterChange(position = position, triState = filter.state.toSuwayomiTriState()))
        }
        this.type == "GroupFilter" && filter is SourceFilter.Group -> {
            filters.mapIndexedNotNull { childIndex, childDto ->
                val childFilter = filter.state.getOrNull(childIndex) ?: return@mapIndexedNotNull null
                childDto.toFilterChanges(childIndex, childFilter)
            }.flatten().map { childChange ->
                SuwayomiSourceFilterChange(position = position, groupChange = childChange)
            }
        }
        else -> emptyList()
    }
}

private fun SuwayomiTriState?.toSourceFilterState(): SourceFilter.TriState.State {
    return when (this) {
        SuwayomiTriState.INCLUDE -> SourceFilter.TriState.State.INCLUDE
        SuwayomiTriState.EXCLUDE -> SourceFilter.TriState.State.EXCLUDE
        SuwayomiTriState.IGNORE, null -> SourceFilter.TriState.State.IGNORE
    }
}

private fun SourceFilter.TriState.State.toSuwayomiTriState(): SuwayomiTriState {
    return when (this) {
        SourceFilter.TriState.State.INCLUDE -> SuwayomiTriState.INCLUDE
        SourceFilter.TriState.State.EXCLUDE -> SuwayomiTriState.EXCLUDE
        SourceFilter.TriState.State.IGNORE -> SuwayomiTriState.IGNORE
    }
}
