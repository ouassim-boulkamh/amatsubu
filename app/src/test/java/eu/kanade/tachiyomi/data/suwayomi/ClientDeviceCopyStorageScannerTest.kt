package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClientDeviceCopyStorageScannerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scanner reports complete copy whose directory is missing`() {
        val copy = copy(
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            directory = File(tempDir.toFile(), "server/20/10"),
            pages = listOf(page(0, isPresent = true, byteSize = 10L)),
        )

        val result = scanner().scan(listOf(copy))

        assertEquals(10L, result.dbByteTotal)
        assertEquals(0L, result.filesystemByteTotal)
        assertEquals(listOf(ClientDeviceCopyStorageRowIssueType.COMPLETE_MISSING_DIRECTORY), result.rowIssues.map { it.type })
        assertEquals(listOf(0), result.rowIssues.single().missingPageIndexes)
        assertTrue(result.cleanupCandidates.isEmpty())
    }

    @Test
    fun `scanner reports complete copy whose page file is missing`() {
        val directory = File(tempDir.toFile(), "server/20/10").apply { mkdirs() }
        write(File(directory, "page-0000.img"), "present")
        val copy = copy(
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            directory = directory,
            pages = listOf(
                page(0, isPresent = true, byteSize = 7L),
                page(1, isPresent = true, byteSize = 11L),
            ),
        )

        val result = scanner().scan(listOf(copy))

        assertEquals(18L, result.dbByteTotal)
        assertEquals(7L, result.filesystemByteTotal)
        assertEquals(listOf(ClientDeviceCopyStorageRowIssueType.COMPLETE_MISSING_PAGE_FILES), result.rowIssues.map { it.type })
        assertEquals(listOf(1), result.rowIssues.single().missingPageIndexes)
    }

    @Test
    fun `scanner reports incomplete row with partial files as cleanup candidate`() {
        val directory = File(tempDir.toFile(), "server/20/10").apply { mkdirs() }
        write(File(directory, "page-0000.img"), "partial")
        val copy = copy(
            status = ClientChapterCopyStatus.INCOMPLETE,
            freshness = ClientChapterCopyFreshness.INCOMPLETE,
            directory = directory,
            pages = listOf(
                page(0, isPresent = true, byteSize = 7L),
                page(1, isPresent = false, byteSize = null),
            ),
        )

        val result = scanner().scan(listOf(copy))

        val issue = result.rowIssues.single()
        assertEquals(ClientDeviceCopyStorageRowIssueType.INCOMPLETE_WITH_PARTIAL_FILES, issue.type)
        assertEquals(ClientDeviceCopyStorageCleanupCandidateType.PARTIAL_COPY_DIRECTORY, issue.cleanupCandidate?.type)
        assertEquals(ClientDeviceCopyIdentity("server-a", 20, 10), issue.cleanupCandidate?.copyIdentity)
        assertEquals(7L, issue.cleanupCandidate?.byteSize)
    }

    @Test
    fun `scanner reports stale temp directories and stray entries without treating copy ancestors as stray`() {
        val directory = File(tempDir.toFile(), "server/20/10").apply { mkdirs() }
        write(File(directory, "page-0000.img"), "copy")
        write(File(tempDir.toFile(), "server/20/10.tmp/page-0000.img"), "temp")
        write(File(tempDir.toFile(), "server/20/stray-chapter/page-0000.img"), "stray-dir")
        write(File(tempDir.toFile(), "loose-file.bin"), "stray-file")
        val copy = copy(
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            directory = directory,
            pages = listOf(page(0, isPresent = true, byteSize = 4L)),
        )

        val result = scanner().scan(listOf(copy))

        assertTrue(result.rowIssues.isEmpty())
        assertEquals(listOf("10.tmp"), result.staleTempDirectories.map { it.path.name })
        assertEquals(
            listOf("loose-file.bin", "stray-chapter"),
            result.strayEntries.map { it.path.name }.sorted(),
        )
        assertEquals(
            listOf(
                ClientDeviceCopyStorageCleanupCandidateType.STALE_TEMP_DIRECTORY,
                ClientDeviceCopyStorageCleanupCandidateType.STRAY_FILE,
                ClientDeviceCopyStorageCleanupCandidateType.STRAY_DIRECTORY,
            ).sortedBy { it.name },
            result.cleanupCandidates.map { it.type }.sortedBy { it.name },
        )
    }

    @Test
    fun `scanner keeps database and filesystem byte totals separate`() {
        val directory = File(tempDir.toFile(), "server/20/10").apply { mkdirs() }
        write(File(directory, "page-0000.img"), "actual-page")
        write(File(tempDir.toFile(), "server/20/10.tmp/page-0000.img"), "temp")
        write(File(tempDir.toFile(), "stray.bin"), "stray")
        val copy = copy(
            status = ClientChapterCopyStatus.COMPLETE,
            freshness = ClientChapterCopyFreshness.FRESH,
            directory = directory,
            pages = listOf(page(0, isPresent = true, byteSize = 123L)),
        )

        val result = scanner().scan(listOf(copy))

        assertEquals(123L, result.dbByteTotal)
        assertEquals("actual-pagetempstray".length.toLong(), result.filesystemByteTotal)
    }

    private fun scanner(): ClientDeviceCopyStorageScanner {
        return ClientDeviceCopyStorageScanner(tempDir.toFile())
    }

    private fun write(file: File, value: String) {
        file.parentFile?.mkdirs()
        file.writeText(value)
    }

    private fun page(
        index: Int,
        isPresent: Boolean,
        byteSize: Long?,
    ): ClientDeviceChapterCopyPage {
        return ClientDeviceChapterCopyPage(
            index = index,
            sourceUrl = "page-$index",
            localUri = null,
            fileName = ClientDeviceChapterCopyPathPolicy.pageFileName(index),
            byteSize = byteSize,
            isPresent = isPresent,
        )
    }

    private fun copy(
        status: ClientChapterCopyStatus,
        freshness: ClientChapterCopyFreshness,
        directory: File,
        pages: List<ClientDeviceChapterCopyPage>,
    ): ClientDeviceChapterCopy {
        return ClientDeviceChapterCopy(
            serverKey = "server-a",
            mangaId = 20,
            chapterId = 10,
            mangaTitle = "Manga",
            chapterTitle = "Chapter",
            chapterUrl = "/chapter",
            chapterRealUrl = null,
            sourceOrder = 1,
            chapterNumber = 1f,
            uploadDate = 2L,
            fetchedAt = "3",
            scanlator = null,
            storagePath = directory.absolutePath,
            manifestHash = "hash",
            status = status,
            freshness = freshness,
            expectedPageCount = pages.size,
            downloadedPageCount = pages.count { it.isPresent },
            createdAt = 4L,
            updatedAt = 5L,
            verifiedAt = null,
            orphanedAt = null,
            pages = pages,
        )
    }
}
