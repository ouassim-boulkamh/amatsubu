package eu.kanade.tachiyomi.ui.browse.source.browse

data class SourceFilterList(
    val items: List<SourceFilter> = emptyList(),
) : List<SourceFilter> by items {
    fun replaceAt(index: Int, filter: SourceFilter): SourceFilterList {
        return copy(items = items.mapIndexed { itemIndex, item -> if (itemIndex == index) filter else item })
    }
}

sealed interface SourceFilter {
    val name: String

    data class Header(
        override val name: String,
    ) : SourceFilter

    data class Separator(
        override val name: String = "",
    ) : SourceFilter

    data class CheckBox(
        override val name: String,
        val state: Boolean,
    ) : SourceFilter

    data class TriState(
        override val name: String,
        val state: State,
    ) : SourceFilter {
        enum class State {
            IGNORE,
            INCLUDE,
            EXCLUDE,
        }
    }

    data class Text(
        override val name: String,
        val state: String,
    ) : SourceFilter

    data class Select(
        override val name: String,
        val values: List<String>,
        val state: Int,
    ) : SourceFilter

    data class Sort(
        override val name: String,
        val values: List<String>,
        val state: Selection?,
    ) : SourceFilter {
        data class Selection(
            val index: Int,
            val ascending: Boolean,
        )
    }

    data class Group(
        override val name: String,
        val state: List<SourceFilter>,
    ) : SourceFilter {
        fun replaceAt(index: Int, filter: SourceFilter): Group {
            return copy(state = state.mapIndexed { itemIndex, item -> if (itemIndex == index) filter else item })
        }
    }
}
