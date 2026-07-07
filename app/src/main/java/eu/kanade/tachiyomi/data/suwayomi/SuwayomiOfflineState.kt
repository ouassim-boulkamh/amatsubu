package eu.kanade.tachiyomi.data.suwayomi

data class SuwayomiStaleSnapshotState(
    val syncedAt: Long,
)

fun Long?.oldestPositive(other: Long?): Long? {
    val values = listOfNotNull(this?.takeIf { it > 0L }, other?.takeIf { it > 0L })
    return values.minOrNull()
}

enum class SuwayomiServerAction {
    BrowseSource,
    MutateLibrary,
    MarkRead,
    Bookmark,
    Track,
    DownloadToServer,
    RemoveServerDownload,
    RefreshFromSource,
    OpenServerReader,
}

fun SuwayomiServerAction.isAllowedWhenOffline(): Boolean = false
