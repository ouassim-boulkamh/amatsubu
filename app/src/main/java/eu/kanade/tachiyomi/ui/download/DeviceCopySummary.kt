package eu.kanade.tachiyomi.ui.download

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFailureReason
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyStatus
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy

internal data class DeviceCopyMangaSummary(
    val serverKey: String,
    val mangaId: Int,
    val mangaTitle: String,
    val totalChapterCopyCount: Int,
    val completeFreshCount: Int,
    val staleCount: Int,
    val unverifiedCount: Int,
    val incompleteCount: Int,
    val failedCount: Int,
    val orphanedCount: Int,
    val totalBytes: Long,
    val latestUpdatedAt: Long,
    val readiness: DeviceCopyReadiness,
    val copies: List<ClientDeviceChapterCopy>,
)

internal enum class DeviceCopyReadiness {
    READY,
    PARTIAL,
    NEEDS_ATTENTION,
    ORPHANED,
}

internal enum class DeviceCopyFilter {
    ALL,
    READY,
    PARTIAL,
    NEEDS_ATTENTION,
    ORPHANED,
}

internal enum class DeviceCopyBulkActionTarget {
    REFRESH,
    RETRY,
    REMOVE,
    REMOVE_ORPHANED,
}

internal data class DeviceCopyStorageQuotaWarning(
    val usedBytes: Long,
    val thresholdBytes: Long,
)

internal fun deviceCopyStorageQuotaWarning(
    usedBytes: Long,
    thresholdBytes: Long = DEFAULT_DEVICE_COPY_STORAGE_WARNING_BYTES,
): DeviceCopyStorageQuotaWarning? {
    return DeviceCopyStorageQuotaWarning(
        usedBytes = usedBytes,
        thresholdBytes = thresholdBytes,
    ).takeIf { usedBytes >= thresholdBytes }
}

internal fun List<ClientDeviceChapterCopy>.toDeviceCopyMangaSummaries(): List<DeviceCopyMangaSummary> {
    return groupBy { copy -> copy.serverKey to copy.mangaId }
        .values
        .map { copies ->
            val sortedCopies = copies.sortedWith(
                compareByDescending<ClientDeviceChapterCopy> { it.updatedAt }
                    .thenBy { it.sourceOrder }
                    .thenBy { it.chapterId },
            )
            val first = sortedCopies.first()
            val completeFreshCount = sortedCopies.count { it.isCompleteFreshDeviceCopy() }
            val orphanedCount = sortedCopies.count { it.freshness == ClientChapterCopyFreshness.ORPHANED }
            DeviceCopyMangaSummary(
                serverKey = first.serverKey,
                mangaId = first.mangaId,
                mangaTitle = first.mangaTitle?.takeIf { it.isNotBlank() } ?: "Manga ${first.mangaId}",
                totalChapterCopyCount = sortedCopies.size,
                completeFreshCount = completeFreshCount,
                staleCount = sortedCopies.count { it.freshness == ClientChapterCopyFreshness.STALE },
                unverifiedCount = sortedCopies.count { it.freshness == ClientChapterCopyFreshness.UNVERIFIED },
                incompleteCount = sortedCopies.count {
                    it.status == ClientChapterCopyStatus.INCOMPLETE ||
                        it.freshness == ClientChapterCopyFreshness.INCOMPLETE
                },
                failedCount = sortedCopies.count { it.status == ClientChapterCopyStatus.FAILED },
                orphanedCount = orphanedCount,
                totalBytes = sortedCopies.sumOf { copy -> copy.pages.sumOf { it.byteSize ?: 0L } },
                latestUpdatedAt = sortedCopies.maxOf { it.updatedAt },
                readiness = classifyDeviceCopyReadiness(
                    totalCount = sortedCopies.size,
                    completeFreshCount = completeFreshCount,
                    orphanedCount = orphanedCount,
                ),
                copies = sortedCopies,
            )
        }
        .sortedWith(
            compareBy<DeviceCopyMangaSummary> { it.readiness.sortOrder }
                .thenByDescending { it.latestUpdatedAt }
                .thenBy { it.mangaTitle.lowercase() },
        )
}

internal fun DeviceCopyMangaSummary.matches(filter: DeviceCopyFilter): Boolean {
    return when (filter) {
        DeviceCopyFilter.ALL -> true
        DeviceCopyFilter.READY -> readiness == DeviceCopyReadiness.READY
        DeviceCopyFilter.PARTIAL -> readiness == DeviceCopyReadiness.PARTIAL
        DeviceCopyFilter.NEEDS_ATTENTION -> readiness == DeviceCopyReadiness.NEEDS_ATTENTION
        DeviceCopyFilter.ORPHANED -> readiness == DeviceCopyReadiness.ORPHANED || orphanedCount > 0
    }
}

internal fun DeviceCopyMangaSummary.bulkActionCopies(
    target: DeviceCopyBulkActionTarget,
): List<ClientDeviceChapterCopy> {
    return when (target) {
        DeviceCopyBulkActionTarget.REFRESH -> {
            val refreshNeeded = copies.filter { copy ->
                copy.freshness != ClientChapterCopyFreshness.ORPHANED && !copy.isCompleteFreshDeviceCopy()
            }
            refreshNeeded.ifEmpty {
                copies.filter { it.freshness != ClientChapterCopyFreshness.ORPHANED }
            }
        }
        DeviceCopyBulkActionTarget.RETRY -> copies.filter { copy ->
            copy.status == ClientChapterCopyStatus.INCOMPLETE ||
                copy.status == ClientChapterCopyStatus.FAILED ||
                copy.freshness == ClientChapterCopyFreshness.INCOMPLETE
        }
        DeviceCopyBulkActionTarget.REMOVE -> copies
        DeviceCopyBulkActionTarget.REMOVE_ORPHANED -> copies.filter {
            it.freshness == ClientChapterCopyFreshness.ORPHANED
        }
    }
}

internal fun ClientDeviceChapterCopy.deviceCopyChapterStatusLabel(): String {
    return when {
        freshness == ClientChapterCopyFreshness.ORPHANED -> "Orphaned"
        status == ClientChapterCopyStatus.FAILED &&
            failureReason == ClientChapterCopyFailureReason.LOW_SPACE -> "Low storage"
        status == ClientChapterCopyStatus.FAILED -> "Failed"
        isCompleteFreshDeviceCopy() -> "Ready"
        freshness == ClientChapterCopyFreshness.STALE -> "Stale"
        freshness == ClientChapterCopyFreshness.UNVERIFIED -> "Unverified"
        status == ClientChapterCopyStatus.QUEUED -> "Queued"
        status == ClientChapterCopyStatus.DOWNLOADING -> "Saving"
        status == ClientChapterCopyStatus.INCOMPLETE ||
            freshness == ClientChapterCopyFreshness.INCOMPLETE -> "Incomplete"
        else -> "Not ready"
    }
}

internal fun ClientDeviceChapterCopy.deviceCopyChapterProgressLabel(): String {
    return "$downloadedPageCount/$expectedPageCount pages"
}

private fun classifyDeviceCopyReadiness(
    totalCount: Int,
    completeFreshCount: Int,
    orphanedCount: Int,
): DeviceCopyReadiness {
    return when {
        totalCount > 0 && orphanedCount == totalCount -> DeviceCopyReadiness.ORPHANED
        completeFreshCount == totalCount -> DeviceCopyReadiness.READY
        completeFreshCount > 0 -> DeviceCopyReadiness.PARTIAL
        else -> DeviceCopyReadiness.NEEDS_ATTENTION
    }
}

private fun ClientDeviceChapterCopy.isCompleteFreshDeviceCopy(): Boolean {
    return freshness == ClientChapterCopyFreshness.FRESH && isComplete
}

private val DeviceCopyReadiness.sortOrder: Int
    get() = when (this) {
        DeviceCopyReadiness.NEEDS_ATTENTION -> 0
        DeviceCopyReadiness.PARTIAL -> 1
        DeviceCopyReadiness.ORPHANED -> 2
        DeviceCopyReadiness.READY -> 3
    }

internal const val DEFAULT_DEVICE_COPY_STORAGE_WARNING_BYTES = 5L * 1024L * 1024L * 1024L
