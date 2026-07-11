package eu.kanade.tachiyomi.e2e

internal object Selectors {
    const val PACKAGE_NAME = "app.amatsubu.dev"

    object Tags {
        const val HOME_LIBRARY = "home_library"
        const val HOME_UPDATES = "home_updates"
        const val HOME_HISTORY = "home_history"
        const val HOME_BROWSE = "home_browse"
        const val HOME_MORE = "home_more"
        const val LIBRARY_LOADING = "library_loading"
        const val LIBRARY_CONTENT = "library_content"
        const val LIBRARY_EMPTY = "library_empty"
        const val LIBRARY_SERVER_UNAVAILABLE = "library_server_unavailable"
        const val SERVER_SETTINGS = "server_settings"
        const val CONTROLLED_SOURCE = "source_${ControlledSource.ID}"
        const val SOURCE_SEARCH_BUTTON = "source_search_button"
        const val SOURCE_FILTER_BUTTON = "source_filter_button"
        const val DOWNLOAD_QUEUE = "download_queue"
        const val DOWNLOAD_QUEUE_EMPTY = "download_queue_empty"
        const val DOWNLOAD_QUEUE_LIST = "download_queue_list"
        const val READER_SURFACE = "reader_surface"
    }

    object Text {
        const val BROWSE = "Browse"
        const val EXTENSIONS = "Extensions"
        const val HISTORY = "History"
        const val LIBRARY = "Library"
        const val MORE = "More"
        const val SERVER = "Server"
        const val SETTINGS = "Settings"
        const val SOURCES = "Sources"
        const val UPDATES = "Updates"
    }

    object ControlledSource {
        const val ID = "728120260708001"
        const val NAME = "Amatsubu Test Local"
        const val DISPLAY_NAME = "Amatsubu Test Local (ALL)"
        const val MANGA_TITLE = "Amatsubu Smoke Test"
        const val BASELINE_CHAPTER = "Chapter 1 - Baseline"
        const val SEARCH_TERM = "smoke"
    }
}
