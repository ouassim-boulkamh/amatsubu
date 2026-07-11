package eu.kanade.tachiyomi.ui.reader

internal fun coerceRequestedReaderPageIndex(requestedPage: Int, pageCount: Int): Int? {
    if (pageCount <= 0) return null
    return requestedPage.coerceIn(0, pageCount - 1)
}
