package eu.kanade.tachiyomi.data.suwayomi

import java.io.File
import java.net.URI

internal class ClientDeviceCopyStorageScanner(
    private val rootDirectory: File,
) {

    fun scan(copies: List<ClientDeviceChapterCopy>): ClientDeviceCopyStorageScanResult {
        val root = rootDirectory.canonicalFile
        val expectedDirectories = copies
            .mapNotNull { copy -> copy.storagePath?.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile } }
            .filter { directory -> directory.isWithin(root) }
            .toSet()
        val rowIssues = copies.flatMap { copy -> classifyRow(copy, root) }
        val staleTempDirectories = mutableListOf<ClientDeviceCopyStorageCleanupCandidate>()
        val strayEntries = mutableListOf<ClientDeviceCopyStorageCleanupCandidate>()

        if (root.exists()) {
            root.listFiles()
                .orEmpty()
                .sortedBy { it.absolutePath }
                .forEach { file ->
                    collectFilesystemIssue(
                        file = file.canonicalFile,
                        root = root,
                        expectedDirectories = expectedDirectories,
                        staleTempDirectories = staleTempDirectories,
                        strayEntries = strayEntries,
                    )
                }
        }

        return ClientDeviceCopyStorageScanResult(
            dbByteTotal = copies.sumOf { copy -> copy.pages.sumOf { page -> page.byteSize ?: 0L } },
            filesystemByteTotal = root.totalFileBytes(),
            rowIssues = rowIssues,
            staleTempDirectories = staleTempDirectories,
            strayEntries = strayEntries,
        )
    }

    private fun classifyRow(
        copy: ClientDeviceChapterCopy,
        root: File,
    ): List<ClientDeviceCopyStorageRowIssue> {
        val directory = copy.storagePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.canonicalFile
            ?.takeIf { it.isWithin(root) }
        val issues = mutableListOf<ClientDeviceCopyStorageRowIssue>()
        if (copy.status == ClientChapterCopyStatus.COMPLETE) {
            if (directory == null || !directory.isDirectory) {
                issues += ClientDeviceCopyStorageRowIssue(
                    copy = copy,
                    type = ClientDeviceCopyStorageRowIssueType.COMPLETE_MISSING_DIRECTORY,
                    missingPageIndexes = copy.pages.map { it.index },
                )
                return issues
            }
            val missingPages = copy.pages
                .filter { page -> page.isPresent }
                .filterNot { page -> page.resolveFile(directory).isFile }
                .map { it.index }
            if (missingPages.isNotEmpty()) {
                issues += ClientDeviceCopyStorageRowIssue(
                    copy = copy,
                    type = ClientDeviceCopyStorageRowIssueType.COMPLETE_MISSING_PAGE_FILES,
                    missingPageIndexes = missingPages,
                )
            }
        }

        if (copy.isIncompleteStorageRow() && directory != null && directory.exists()) {
            val partialBytes = directory.totalFileBytes()
            if (partialBytes > 0L) {
                issues += ClientDeviceCopyStorageRowIssue(
                    copy = copy,
                    type = ClientDeviceCopyStorageRowIssueType.INCOMPLETE_WITH_PARTIAL_FILES,
                    cleanupCandidate = ClientDeviceCopyStorageCleanupCandidate(
                        type = ClientDeviceCopyStorageCleanupCandidateType.PARTIAL_COPY_DIRECTORY,
                        path = directory,
                        byteSize = partialBytes,
                        copyIdentity = copy.identity,
                    ),
                )
            }
        }
        return issues
    }

    private fun collectFilesystemIssue(
        file: File,
        root: File,
        expectedDirectories: Set<File>,
        staleTempDirectories: MutableList<ClientDeviceCopyStorageCleanupCandidate>,
        strayEntries: MutableList<ClientDeviceCopyStorageCleanupCandidate>,
    ) {
        if (!file.isWithin(root)) return
        if (file in expectedDirectories) return

        if (file.isDirectory && file.name.endsWith(TEMP_DIRECTORY_SUFFIX)) {
            staleTempDirectories += ClientDeviceCopyStorageCleanupCandidate(
                type = ClientDeviceCopyStorageCleanupCandidateType.STALE_TEMP_DIRECTORY,
                path = file,
                byteSize = file.totalFileBytes(),
            )
            return
        }

        val hasExpectedDescendant = expectedDirectories.any { expected -> expected.isWithin(file) }
        if (!hasExpectedDescendant) {
            strayEntries += ClientDeviceCopyStorageCleanupCandidate(
                type = if (file.isDirectory) {
                    ClientDeviceCopyStorageCleanupCandidateType.STRAY_DIRECTORY
                } else {
                    ClientDeviceCopyStorageCleanupCandidateType.STRAY_FILE
                },
                path = file,
                byteSize = file.totalFileBytes(),
            )
            return
        }

        file.listFiles()
            .orEmpty()
            .sortedBy { it.absolutePath }
            .forEach { child ->
                collectFilesystemIssue(
                    file = child.canonicalFile,
                    root = root,
                    expectedDirectories = expectedDirectories,
                    staleTempDirectories = staleTempDirectories,
                    strayEntries = strayEntries,
                )
            }
    }

    private fun ClientDeviceChapterCopyPage.resolveFile(directory: File): File {
        val uriFile = localUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                runCatching { File(URI(uri)) }.getOrNull()
            }
        return uriFile ?: File(directory, fileName ?: ClientDeviceChapterCopyPathPolicy.pageFileName(index))
    }

    private fun ClientDeviceChapterCopy.isIncompleteStorageRow(): Boolean {
        return status == ClientChapterCopyStatus.INCOMPLETE ||
            status == ClientChapterCopyStatus.DOWNLOADING ||
            freshness == ClientChapterCopyFreshness.INCOMPLETE
    }

    private val ClientDeviceChapterCopy.identity: ClientDeviceCopyIdentity
        get() = ClientDeviceCopyIdentity(
            serverKey = serverKey,
            mangaId = mangaId,
            chapterId = chapterId,
        )

    private companion object {
        const val TEMP_DIRECTORY_SUFFIX = ".tmp"
    }
}

internal data class ClientDeviceCopyStorageScanResult(
    val dbByteTotal: Long,
    val filesystemByteTotal: Long,
    val rowIssues: List<ClientDeviceCopyStorageRowIssue>,
    val staleTempDirectories: List<ClientDeviceCopyStorageCleanupCandidate>,
    val strayEntries: List<ClientDeviceCopyStorageCleanupCandidate>,
) {
    val cleanupCandidates: List<ClientDeviceCopyStorageCleanupCandidate>
        get() = rowIssues.mapNotNull { it.cleanupCandidate } + staleTempDirectories + strayEntries
}

internal data class ClientDeviceCopyStorageRowIssue(
    val copy: ClientDeviceChapterCopy,
    val type: ClientDeviceCopyStorageRowIssueType,
    val missingPageIndexes: List<Int> = emptyList(),
    val cleanupCandidate: ClientDeviceCopyStorageCleanupCandidate? = null,
)

internal enum class ClientDeviceCopyStorageRowIssueType {
    COMPLETE_MISSING_DIRECTORY,
    COMPLETE_MISSING_PAGE_FILES,
    INCOMPLETE_WITH_PARTIAL_FILES,
}

internal data class ClientDeviceCopyStorageCleanupCandidate(
    val type: ClientDeviceCopyStorageCleanupCandidateType,
    val path: File,
    val byteSize: Long,
    val copyIdentity: ClientDeviceCopyIdentity? = null,
)

internal enum class ClientDeviceCopyStorageCleanupCandidateType {
    PARTIAL_COPY_DIRECTORY,
    STALE_TEMP_DIRECTORY,
    STRAY_DIRECTORY,
    STRAY_FILE,
}

internal data class ClientDeviceCopyIdentity(
    val serverKey: String,
    val mangaId: Int,
    val chapterId: Int,
)

private fun File.totalFileBytes(): Long {
    return when {
        isFile -> length()
        isDirectory -> walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        else -> 0L
    }
}

private fun File.isWithin(root: File): Boolean {
    return path == root.path || path.startsWith(root.path + File.separator)
}
