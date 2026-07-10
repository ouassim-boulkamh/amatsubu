package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSortSelectionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterChange
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceFilterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiTriState
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerSourceFiltersTest {

    @Test
    fun `Suwayomi filter DTOs map to local browse filter model`() {
        val filters = filterDtos().toSourceFilterList()

        assertEquals(
            SourceFilter.CheckBox(name = "Licensed", state = true),
            filters[0],
        )
        assertEquals(
            SourceFilter.Sort(
                name = "Sort by",
                values = listOf("Title", "Latest"),
                state = SourceFilter.Sort.Selection(index = 1, ascending = false),
            ),
            filters[1],
        )
        assertEquals(
            SourceFilter.Group(
                name = "Genre",
                state = listOf(SourceFilter.TriState("Action", SourceFilter.TriState.State.IGNORE)),
            ),
            filters[2],
        )
    }

    @Test
    fun `unchanged local browse filters produce no Suwayomi changes`() {
        val dtos = filterDtos()

        assertTrue(dtos.toFilterChanges(dtos.toSourceFilterList()).isEmpty())
    }

    @Test
    fun `changed local browse filters serialize to Suwayomi filter changes`() {
        val dtos = filterDtos()
        val filters = dtos.toSourceFilterList()
            .replaceAt(0, SourceFilter.CheckBox(name = "Licensed", state = false))
            .replaceAt(
                1,
                SourceFilter.Sort(
                    name = "Sort by",
                    values = listOf("Title", "Latest"),
                    state = SourceFilter.Sort.Selection(index = 0, ascending = true),
                ),
            )
            .replaceAt(
                2,
                SourceFilter.Group(
                    name = "Genre",
                    state = listOf(SourceFilter.TriState("Action", SourceFilter.TriState.State.INCLUDE)),
                ),
            )

        assertEquals(
            listOf(
                SuwayomiSourceFilterChange(position = 0, checkBoxState = false),
                SuwayomiSourceFilterChange(
                    position = 1,
                    sortState = SuwayomiSortSelectionDto(index = 0, ascending = true),
                ),
                SuwayomiSourceFilterChange(
                    position = 2,
                    groupChange = SuwayomiSourceFilterChange(position = 0, triState = SuwayomiTriState.INCLUDE),
                ),
            ),
            dtos.toFilterChanges(filters),
        )
    }

    private fun filterDtos(): List<SuwayomiSourceFilterDto> {
        return listOf(
            SuwayomiSourceFilterDto(
                type = "CheckBoxFilter",
                name = "Licensed",
                defaultBoolean = true,
            ),
            SuwayomiSourceFilterDto(
                type = "SortFilter",
                name = "Sort by",
                values = listOf("Title", "Latest"),
                defaultSort = SuwayomiSortSelectionDto(index = 1, ascending = false),
            ),
            SuwayomiSourceFilterDto(
                type = "GroupFilter",
                name = "Genre",
                filters = listOf(
                    SuwayomiSourceFilterDto(
                        type = "TriStateFilter",
                        name = "Action",
                        defaultTriState = SuwayomiTriState.IGNORE,
                    ),
                ),
            ),
        )
    }
}
