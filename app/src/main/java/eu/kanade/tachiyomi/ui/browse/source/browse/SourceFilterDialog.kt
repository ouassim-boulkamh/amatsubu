package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.CollapsibleBox
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TextItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.Button
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SourceFilterDialog(
    onDismissRequest: () -> Unit,
    filters: SourceFilterList,
    onReset: () -> Unit,
    onFilter: () -> Unit,
    onUpdate: (SourceFilterList) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyColumn {
            stickyHeader {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
                ) {
                    TextButton(onClick = onReset) {
                        Text(
                            text = stringResource(MR.strings.action_reset),
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(onClick = {
                        onFilter()
                        onDismissRequest()
                    }) {
                        Text(stringResource(MR.strings.action_filter))
                    }
                }
                HorizontalDivider()
            }

            itemsIndexed(filters.items) { index, filter ->
                FilterItem(filter) { updatedFilter ->
                    onUpdate(filters.replaceAt(index, updatedFilter))
                }
            }
        }
    }
}

@Composable
private fun FilterItem(filter: SourceFilter, onUpdate: (SourceFilter) -> Unit) {
    when (filter) {
        is SourceFilter.Header -> {
            HeadingItem(filter.name)
        }
        is SourceFilter.Separator -> {
            HorizontalDivider()
        }
        is SourceFilter.CheckBox -> {
            CheckboxItem(
                label = filter.name,
                checked = filter.state,
            ) {
                onUpdate(filter.copy(state = !filter.state))
            }
        }
        is SourceFilter.TriState -> {
            TriStateItem(
                label = filter.name,
                state = filter.state.toTriStateFilter(),
            ) {
                onUpdate(filter.copy(state = filter.state.toTriStateFilter().next().toSourceFilterState()))
            }
        }
        is SourceFilter.Text -> {
            TextItem(
                label = filter.name,
                value = filter.state,
            ) {
                onUpdate(filter.copy(state = it))
            }
        }
        is SourceFilter.Select -> {
            SelectItem(
                label = filter.name,
                options = filter.values.toTypedArray(),
                selectedIndex = filter.state,
            ) {
                onUpdate(filter.copy(state = it))
            }
        }
        is SourceFilter.Sort -> {
            CollapsibleBox(
                heading = filter.name,
            ) {
                Column {
                    filter.values.mapIndexed { index, item ->
                        val sortAscending = filter.state
                            ?.takeIf { index == it.index }
                            ?.ascending
                        SortItem(
                            label = item,
                            sortDescending = if (sortAscending != null) !sortAscending else null,
                            onClick = {
                                val currentSelection = filter.state
                                val ascending = if (index == currentSelection?.index) {
                                    !currentSelection.ascending
                                } else {
                                    currentSelection?.ascending ?: true
                                }
                                onUpdate(
                                    filter.copy(
                                        state = SourceFilter.Sort.Selection(
                                            index = index,
                                            ascending = ascending,
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        is SourceFilter.Group -> {
            CollapsibleBox(
                heading = filter.name,
            ) {
                Column {
                    filter.state.mapIndexed { index, childFilter ->
                        FilterItem(filter = childFilter) { updatedChildFilter ->
                            onUpdate(filter.replaceAt(index, updatedChildFilter))
                        }
                    }
                }
            }
        }
    }
}

private fun SourceFilter.TriState.State.toTriStateFilter(): TriState {
    return when (this) {
        SourceFilter.TriState.State.IGNORE -> TriState.DISABLED
        SourceFilter.TriState.State.INCLUDE -> TriState.ENABLED_IS
        SourceFilter.TriState.State.EXCLUDE -> TriState.ENABLED_NOT
    }
}

private fun TriState.toSourceFilterState(): SourceFilter.TriState.State {
    return when (this) {
        TriState.DISABLED -> SourceFilter.TriState.State.IGNORE
        TriState.ENABLED_IS -> SourceFilter.TriState.State.INCLUDE
        TriState.ENABLED_NOT -> SourceFilter.TriState.State.EXCLUDE
    }
}
