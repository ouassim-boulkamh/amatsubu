package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterChange
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTriState
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilter
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ServerSourceFiltersPropertyTest {

    @Test
    fun `generated unsupported server filters never shift supported filter change positions`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(Arb.boolean(), 0..24)) { unsupportedSlots ->
            val dtos = unsupportedSlots.mapIndexed { index, isUnsupported ->
                if (isUnsupported) {
                    SuwayomiSourceFilterDto(type = "FutureFilter", name = "future-$index")
                } else {
                    SuwayomiSourceFilterDto(type = "CheckBoxFilter", name = "supported-$index", defaultBoolean = true)
                }
            }
            val changedFilters = dtos.toSourceFilterList().foldIndexed(dtos.toSourceFilterList()) {
                    index,
                    current,
                    filter,
                ->
                if (filter is SourceFilter.CheckBox) current.replaceAt(index, filter.copy(state = false)) else current
            }

            val changes = dtos.toFilterChanges(changedFilters)
            val expected = unsupportedSlots.mapIndexedNotNull { index, isUnsupported ->
                if (isUnsupported) null else SuwayomiSourceFilterChange(position = index, checkBoxState = false)
            }

            changes shouldBe expected
        }
    }

    @Test
    fun `generated mixed nested filter trees preserve server positions and skip unknown siblings`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), Arb.list(Arb.int(), 1..80)) { shape ->
            val sourceA = filterTree(shape, source = "A")
            val sourceB = filterTree(shape.reversed(), source = "B")
            val sourceAFilters = sourceA.toSourceFilterList().let { filters ->
                filters.copy(items = filters.map(::changeEveryMutableFilter))
            }
            val sourceBFilters = sourceB.toSourceFilterList()

            sourceA.toFilterChanges(sourceAFilters) shouldBe expectedChanges(sourceA, sourceAFilters)
            sourceB.toFilterChanges(sourceBFilters) shouldBe emptyList()
        }
    }

    private fun filterTree(
        shape: List<Int>,
        source: String,
        depth: Int = 0,
    ): List<SuwayomiSourceFilterDto> {
        return shape.take(if (depth == 0) 12 else 6).mapIndexed { index, token ->
            val name = "$source-duplicate-${index % 2}"
            when {
                depth < 2 && token % 7 == 0 -> SuwayomiSourceFilterDto(
                    type = "GroupFilter",
                    name = name,
                    filters = filterTree(shape.drop(index + 1), source, depth + 1),
                )
                token % 7 == 1 -> SuwayomiSourceFilterDto(type = "FutureFilter", name = name)
                token % 7 == 2 -> SuwayomiSourceFilterDto(
                    type = "CheckBoxFilter",
                    name = name,
                    defaultBoolean =
                    token % 2 == 0,
                )
                token % 7 == 3 -> SuwayomiSourceFilterDto(
                    type = "SelectFilter",
                    name = name,
                    values = listOf("zero", "one", "two"),
                    defaultInt = token,
                )
                token % 7 == 4 -> SuwayomiSourceFilterDto(
                    type = "TextFilter",
                    name = name,
                    defaultString = "value-$token",
                )
                token % 7 == 5 -> SuwayomiSourceFilterDto(
                    type = "TriStateFilter",
                    name = name,
                    defaultTriState = SuwayomiTriState.entries[Math.floorMod(token, SuwayomiTriState.entries.size)],
                )
                else -> SuwayomiSourceFilterDto(
                    type = "SortFilter",
                    name = name,
                    values = listOf("zero", "one", "two"),
                    defaultSort = eu.kanade.tachiyomi.data.suwayomi.SuwayomiSortSelectionDto(
                        index = Math.floorMod(token, 3),
                        ascending = token % 2 == 0,
                    ),
                )
            }
        }
    }

    private fun changeEveryMutableFilter(filter: SourceFilter): SourceFilter = when (filter) {
        is SourceFilter.CheckBox -> filter.copy(state = !filter.state)
        is SourceFilter.Select -> filter.copy(state = (filter.state + 1) % filter.values.size)
        is SourceFilter.Sort -> filter.copy(
            state = filter.state?.let {
                SourceFilter.Sort.Selection((it.index + 1) % filter.values.size, !it.ascending)
            },
        )
        is SourceFilter.Text -> filter.copy(state = "${filter.state}-edited")
        is SourceFilter.TriState -> filter.copy(
            state = when (filter.state) {
                SourceFilter.TriState.State.IGNORE -> SourceFilter.TriState.State.INCLUDE
                SourceFilter.TriState.State.INCLUDE -> SourceFilter.TriState.State.EXCLUDE
                SourceFilter.TriState.State.EXCLUDE -> SourceFilter.TriState.State.IGNORE
            },
        )
        is SourceFilter.Group -> filter.copy(state = filter.state.map(::changeEveryMutableFilter))
        is SourceFilter.Header, is SourceFilter.Separator -> filter
    }

    private fun expectedChanges(
        dtos: List<SuwayomiSourceFilterDto>,
        filters: List<SourceFilter>,
    ): List<SuwayomiSourceFilterChange> {
        var filterIndex = 0
        return dtos.flatMapIndexed { position, dto ->
            if (dto.type !in supportedTypes) return@flatMapIndexed emptyList()
            val filter = filters[filterIndex++]
            expectedChanges(dto, position, filter)
        }
    }

    private fun expectedChanges(
        dto: SuwayomiSourceFilterDto,
        position: Int,
        filter: SourceFilter,
    ): List<SuwayomiSourceFilterChange> = when (dto.type) {
        "CheckBoxFilter" -> listOf(
            SuwayomiSourceFilterChange(position, checkBoxState = (filter as SourceFilter.CheckBox).state),
        )
        "SelectFilter" -> listOf(
            SuwayomiSourceFilterChange(position, selectState = (filter as SourceFilter.Select).state),
        )
        "SortFilter" -> listOf(
            SuwayomiSourceFilterChange(
                position,
                sortState = (filter as SourceFilter.Sort).state?.let {
                    eu.kanade.tachiyomi.data.suwayomi.SuwayomiSortSelectionDto(it.index, it.ascending)
                },
            ),
        )
        "TextFilter" -> listOf(SuwayomiSourceFilterChange(position, textState = (filter as SourceFilter.Text).state))
        "TriStateFilter" -> listOf(
            SuwayomiSourceFilterChange(
                position,
                triState = when ((filter as SourceFilter.TriState).state) {
                    SourceFilter.TriState.State.IGNORE -> SuwayomiTriState.IGNORE
                    SourceFilter.TriState.State.INCLUDE -> SuwayomiTriState.INCLUDE
                    SourceFilter.TriState.State.EXCLUDE -> SuwayomiTriState.EXCLUDE
                },
            ),
        )
        "GroupFilter" -> expectedChanges(dto.filters, (filter as SourceFilter.Group).state)
            .map { SuwayomiSourceFilterChange(position, groupChange = it) }
        else -> emptyList()
    }

    private companion object {
        val supportedTypes = setOf(
            "CheckBoxFilter",
            "SelectFilter",
            "SortFilter",
            "TextFilter",
            "TriStateFilter",
            "GroupFilter",
        )
    }
}
