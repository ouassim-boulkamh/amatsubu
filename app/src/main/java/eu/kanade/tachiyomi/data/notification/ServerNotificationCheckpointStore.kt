package eu.kanade.tachiyomi.data.notification

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiUpdaterJobsInfoDto
import eu.kanade.tachiyomi.data.suwayomi.currentVersionCode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class ServerNotificationCheckpointStore(
    preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val serverIdentity = preferenceStore.getString(key("server_identity"), "")
    private val libraryUpdateRunning = preferenceStore.getBoolean(key("library_update_running"), false)
    private val libraryUpdateStartedAt = preferenceStore.getLong(key("library_update_started_at"), 0L)
    private val libraryUpdateFinishedAt = preferenceStore.getLong(key("library_update_finished_at"), 0L)
    private val notifiedChapterIds = preferenceStore.getString(key("notified_chapter_ids"), "")
    private val downloadQueueVisible = preferenceStore.getBoolean(key("download_queue_visible"), false)
    private val downloadQueueState = preferenceStore.getString(key("download_queue_state"), "")
    private val notifiedDownloadErrorIds = preferenceStore.getString(key("notified_download_error_ids"), "")
    private val syncYomiRunning = preferenceStore.getBoolean(key("syncyomi_running"), false)
    private val syncYomiTerminalKey = preferenceStore.getString(key("syncyomi_terminal_key"), "")
    private val notifiedExtensionUpdateIds = preferenceStore.getString(key("notified_extension_update_ids"), "")

    fun recordLibraryUpdate(
        serverIdentity: String,
        jobs: SuwayomiUpdaterJobsInfoDto,
        nowMillis: Long = System.currentTimeMillis(),
    ): LibraryUpdateTransition {
        ensureServerIdentity(serverIdentity)

        val wasRunning = libraryUpdateRunning.get()
        val isRunning = jobs.isRunning
        val startedAt = when {
            !wasRunning && isRunning -> nowMillis
            isRunning && libraryUpdateStartedAt.get() == 0L -> nowMillis
            else -> libraryUpdateStartedAt.get()
        }
        val finishedAt = if (wasRunning && !isRunning) nowMillis else libraryUpdateFinishedAt.get()

        libraryUpdateRunning.set(isRunning)
        if (isRunning) {
            libraryUpdateStartedAt.set(startedAt)
        }
        if (wasRunning && !isRunning) {
            libraryUpdateFinishedAt.set(finishedAt)
        }

        return LibraryUpdateTransition(
            wasRunning = wasRunning,
            isRunning = isRunning,
            startedAt = startedAt,
            finishedAt = finishedAt,
            completed = wasRunning && !isRunning,
        )
    }

    fun filterUnnotifiedChapterIds(chapterIds: Collection<Int>): Set<Int> {
        val alreadyNotified = notifiedChapterIds.get().decodeIdSet()
        return chapterIds
            .map(Int::toString)
            .filterNot(alreadyNotified::contains)
            .mapNotNull(String::toIntOrNull)
            .toSet()
    }

    fun markChaptersNotified(chapterIds: Collection<Int>) {
        if (chapterIds.isEmpty()) return
        notifiedChapterIds.set((notifiedChapterIds.get().decodeIdSet() + chapterIds.map(Int::toString)).trimmed().encodeIdSet())
    }

    fun recordDownloadQueue(
        serverIdentity: String,
        visible: Boolean,
        state: String,
    ): DownloadQueueTransition {
        ensureServerIdentity(serverIdentity)

        val wasVisible = downloadQueueVisible.get()
        val previousState = downloadQueueState.get()
        downloadQueueVisible.set(visible)
        downloadQueueState.set(state)
        return DownloadQueueTransition(
            wasVisible = wasVisible,
            isVisible = visible,
            previousState = previousState,
            state = state,
            becameHidden = wasVisible && !visible,
        )
    }

    fun markDownloadErrorsNotified(downloads: Collection<SuwayomiDownloadDto>) {
        val ids = downloads.map { it.errorCheckpointId() }
        if (ids.isEmpty()) return
        notifiedDownloadErrorIds.set(
            (notifiedDownloadErrorIds.get().decodeIdSet() + ids).trimmed().encodeIdSet(),
        )
    }

    fun filterUnnotifiedDownloadErrors(downloads: Collection<SuwayomiDownloadDto>): List<SuwayomiDownloadDto> {
        val alreadyNotified = notifiedDownloadErrorIds.get().decodeIdSet()
        return downloads.filterNot { it.errorCheckpointId() in alreadyNotified }
    }

    fun notifiedDownloadErrorIds(): Set<String> {
        return notifiedDownloadErrorIds.get().decodeIdSet()
    }

    fun recordSyncYomiStatus(
        serverIdentity: String,
        status: SuwayomiSyncStatusDto?,
    ): SyncYomiTransition {
        ensureServerIdentity(serverIdentity)

        val wasRunning = syncYomiRunning.get()
        val isRunning = status?.state?.isSyncYomiRunningState() == true
        val terminalKey = status?.terminalCheckpointKey()
        val notifyTerminal = status?.state?.isSyncYomiTerminalState() == true &&
            wasRunning &&
            terminalKey != null &&
            terminalKey != syncYomiTerminalKey.get()

        syncYomiRunning.set(isRunning)
        if (notifyTerminal) {
            syncYomiTerminalKey.set(terminalKey)
        }

        return SyncYomiTransition(
            wasRunning = wasRunning,
            isRunning = isRunning,
            notifyTerminal = notifyTerminal,
        )
    }

    fun filterUnnotifiedExtensionUpdates(
        serverIdentity: String,
        extensions: Collection<SuwayomiExtensionDto>,
    ): List<SuwayomiExtensionDto> {
        ensureServerIdentity(serverIdentity)

        val updateExtensions = extensions.filter { it.isInstalled && it.hasUpdate }
        if (updateExtensions.isEmpty()) {
            notifiedExtensionUpdateIds.set("")
            return emptyList()
        }

        val updateIds = updateExtensions.map { it.extensionUpdateCheckpointId() }.toSet()
        val alreadyNotified = notifiedExtensionUpdateIds.get().decodeIdSet()
        val staleIdsRemoved = alreadyNotified.intersect(updateIds)
        if (staleIdsRemoved != alreadyNotified) {
            notifiedExtensionUpdateIds.set(staleIdsRemoved.trimmed().encodeIdSet())
        }

        return updateExtensions.filterNot { it.extensionUpdateCheckpointId() in staleIdsRemoved }
    }

    fun markExtensionUpdatesNotified(extensions: Collection<SuwayomiExtensionDto>) {
        val ids = extensions.map { it.extensionUpdateCheckpointId() }
        if (ids.isEmpty()) return
        notifiedExtensionUpdateIds.set(
            (notifiedExtensionUpdateIds.get().decodeIdSet() + ids).trimmed().encodeIdSet(),
        )
    }

    fun notifiedExtensionUpdateIds(): Set<String> {
        return notifiedExtensionUpdateIds.get().decodeIdSet()
    }

    fun hasActiveServerJob(currentServerIdentity: String): Boolean {
        ensureServerIdentity(currentServerIdentity)
        return libraryUpdateRunning.get() || downloadQueueVisible.get() || syncYomiRunning.get()
    }

    fun resetForTesting() {
        listOf(
            serverIdentity,
            libraryUpdateRunning,
            libraryUpdateStartedAt,
            libraryUpdateFinishedAt,
            notifiedChapterIds,
            downloadQueueVisible,
            downloadQueueState,
            notifiedDownloadErrorIds,
            syncYomiRunning,
            syncYomiTerminalKey,
            notifiedExtensionUpdateIds,
        ).forEach { it.delete() }
    }

    private fun ensureServerIdentity(currentServerIdentity: String) {
        val current = currentServerIdentity.trim().trimEnd('/')
        if (serverIdentity.get() == current) return

        serverIdentity.set(current)
        libraryUpdateRunning.set(false)
        libraryUpdateStartedAt.set(0L)
        libraryUpdateFinishedAt.set(0L)
        notifiedChapterIds.set("")
        downloadQueueVisible.set(false)
        downloadQueueState.set("")
        notifiedDownloadErrorIds.set("")
        syncYomiRunning.set(false)
        syncYomiTerminalKey.set("")
        notifiedExtensionUpdateIds.set("")
    }

    private fun Set<String>.trimmed(): Set<String> {
        return if (size <= MAX_STORED_IDS) {
            this
        } else {
            toList().takeLast(MAX_STORED_IDS).toSet()
        }
    }

    data class LibraryUpdateTransition(
        val wasRunning: Boolean,
        val isRunning: Boolean,
        // Suwayomi does not expose library update start/finish timestamps here.
        // These are client observation times used only for notification reconciliation.
        val startedAt: Long,
        val finishedAt: Long,
        val completed: Boolean,
    )

    data class DownloadQueueTransition(
        val wasVisible: Boolean,
        val isVisible: Boolean,
        val previousState: String,
        val state: String,
        val becameHidden: Boolean,
    )

    data class SyncYomiTransition(
        val wasRunning: Boolean,
        val isRunning: Boolean,
        val notifyTerminal: Boolean,
    )

    companion object {
        private const val MAX_STORED_IDS = 500

        fun downloadErrorId(download: SuwayomiDownloadDto): String {
            return download.errorCheckpointId()
        }

        private fun key(suffix: String): String {
            return Preference.appStateKey("server_notification_checkpoint_$suffix")
        }
    }
}

private fun String.decodeIdSet(): Set<String> {
    if (isBlank()) return emptySet()
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}

private fun Set<String>.encodeIdSet(): String {
    return joinToString(separator = "\n")
}

private fun SuwayomiDownloadDto.errorCheckpointId(): String {
    return "${manga.id}:${chapter.id}"
}

private fun SuwayomiExtensionDto.extensionUpdateCheckpointId(): String {
    return listOf(pkgName, currentVersionCode(), versionName).joinToString(separator = "|")
}

private fun SuwayomiSyncStatusDto.terminalCheckpointKey(): String {
    return listOf(state, startDate, endDate.orEmpty(), backupRestoreId.orEmpty(), errorMessage.orEmpty())
        .joinToString(separator = "|")
}

private fun String.isSyncYomiRunningState(): Boolean {
    return uppercase() in setOf(
        "STARTED",
        "CREATING_BACKUP",
        "DOWNLOADING",
        "MERGING",
        "UPLOADING",
        "RESTORING",
    )
}

private fun String.isSyncYomiTerminalState(): Boolean {
    return equals("SUCCESS", ignoreCase = true) || equals("ERROR", ignoreCase = true)
}
