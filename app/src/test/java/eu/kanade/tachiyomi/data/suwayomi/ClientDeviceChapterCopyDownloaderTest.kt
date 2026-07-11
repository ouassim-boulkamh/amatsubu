package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest

class ClientDeviceChapterCopyDownloaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `save to device stores complete fresh copy after every page downloads`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 2)
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
        )

        val copy = downloader.saveToDevice("http://server.test/api/graphql", "Manga", manifest.chapter.id)

        assertEquals(ClientChapterCopyStatus.COMPLETE, copy.status)
        assertEquals(ClientChapterCopyFreshness.FRESH, copy.freshness)
        assertEquals(2, copy.downloadedPageCount)
        assertTrue(copy.isComplete)
        assertTrue(File(copy.storagePath!!).exists())
        assertFalse(File(File(copy.storagePath).parentFile, "${manifest.chapter.id}.tmp").exists())
        assertEquals(listOf(true, true), copy.pages.map { it.isPresent })
        assertEquals(
            listOf("bytes-page-0", "bytes-page-1"),
            copy.pages.map {
                File(java.net.URI(it.localUri)).readText()
            },
        )
        assertEquals(
            copy.pages.map {
                sha256(File(java.net.URI(it.localUri)).readBytes())
            },
            copy.pages.map { it.sha256 },
        )
        assertEquals(listOf(1, 2), store.progressUpdates)
    }

    @Test
    fun `save to device retries transient page failures before marking copy incomplete`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 2)
        val fetcher = FakeClientDeviceChapterPageFetcher(
            manifest = manifest,
            transientFailures = mapOf(1 to 1),
        )
        val downloader = downloader(
            store = store,
            fetcher = fetcher,
        )

        val copy = downloader.saveToDevice("http://server.test/api/graphql", "Manga", manifest.chapter.id)

        assertEquals(ClientChapterCopyStatus.COMPLETE, copy.status)
        assertEquals(ClientChapterCopyFreshness.FRESH, copy.freshness)
        assertEquals(2, copy.downloadedPageCount)
        assertEquals(1, fetcher.downloadAttempts.getValue(0))
        assertEquals(2, fetcher.downloadAttempts.getValue(1))
        assertFalse(store.statusUpdates.contains(ClientChapterCopyStatus.INCOMPLETE))
    }

    @Test
    fun `save to device marks incomplete copy when a page download fails`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 3)
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest, failAtIndex = 1),
        )

        val result = runCatching {
            downloader.saveToDevice("http://server.test/api/graphql", "Manga", manifest.chapter.id)
        }
        val copy = store.getCopy("http://server.test/api/graphql", manifest.chapter.mangaId, manifest.chapter.id)

        assertTrue(result.isFailure)
        assertNotNull(copy)
        assertEquals(ClientChapterCopyStatus.INCOMPLETE, copy!!.status)
        assertEquals(ClientChapterCopyFreshness.INCOMPLETE, copy.freshness)
        assertEquals(1, copy.downloadedPageCount)
        assertEquals(listOf(true, false, false), copy.pages.map { it.isPresent })
        assertTrue(File(copy.storagePath!!).exists())
        assertTrue(File(java.net.URI(copy.pages.first().localUri)).exists())
    }

    @Test
    fun `failed promotion retains a complete partial copy for recovery`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 3)
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
            promoteTempDirectory = { _, _ -> false },
        )

        val result = runCatching {
            downloader.saveToDevice("server-a", "Manga", manifest.chapter.id)
        }
        val copy = store.getCopy("server-a", manifest.chapter.mangaId, manifest.chapter.id)

        assertTrue(result.isFailure)
        assertNotNull(copy)
        assertEquals(ClientChapterCopyStatus.INCOMPLETE, copy!!.status)
        assertEquals(ClientChapterCopyFreshness.INCOMPLETE, copy.freshness)
        assertEquals(3, copy.downloadedPageCount)
        assertEquals(listOf(true, true, true), copy.pages.map { it.isPresent })
        assertTrue(File(copy.storagePath!!).isDirectory)
        assertEquals(
            listOf("bytes-page-0", "bytes-page-1", "bytes-page-2"),
            copy.pages.map { File(java.net.URI(it.localUri)).readText() },
        )
    }

    @Test
    fun `failed replacement preserves an existing complete device copy`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 3)
        val firstDownloader = downloader(store, FakeClientDeviceChapterPageFetcher(manifest))
        val existing = firstDownloader.saveToDevice("server-a", "Manga", manifest.chapter.id)
        val existingPageBytes = existing.pages.map { File(java.net.URI(it.localUri)).readBytes() }

        val replacementDownloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest, failAtIndex = 1),
        )

        assertTrue(
            runCatching {
                replacementDownloader.saveToDevice("server-a", "Manga", manifest.chapter.id)
            }.isFailure,
        )

        val retained = store.getCopy("server-a", manifest.chapter.mangaId, manifest.chapter.id)
        assertNotNull(retained)
        assertEquals(ClientChapterCopyStatus.COMPLETE, retained!!.status)
        assertTrue(retained.isComplete)
        assertTrue(File(retained.storagePath!!).isDirectory)
        assertEquals(
            existingPageBytes.map { it.decodeToString() },
            retained.pages.map { File(java.net.URI(it.localUri)).readText() },
        )
    }

    @Test
    suspend fun `generated interrupted replacements preserve the prior complete copy`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.int(1..8), Arb.int(0..31), Arb.int(1..10_000)) {
                pageCount,
                failureSeed,
                chapterId,
            ->
            val store = FakeClientDeviceChapterCopyRepository()
            val manifest = manifest(pageCount = pageCount, chapterId = chapterId)
            val initialDownloader = downloader(store, FakeClientDeviceChapterPageFetcher(manifest))
            val existing = initialDownloader.saveToDevice("server-a", "Manga", chapterId)
            val priorPageContents = existing.pages.map { File(java.net.URI(it.localUri)).readText() }

            val replacementDownloader = downloader(
                store = store,
                fetcher = FakeClientDeviceChapterPageFetcher(
                    manifest = manifest,
                    failAtIndex = failureSeed % pageCount,
                ),
            )

            assertTrue(
                runCatching {
                    replacementDownloader.saveToDevice("server-a", "Manga", chapterId)
                }.isFailure,
            )

            val retained = store.getCopy("server-a", manifest.chapter.mangaId, chapterId)
            assertNotNull(retained)
            assertEquals(ClientChapterCopyStatus.COMPLETE, retained!!.status)
            assertTrue(retained.isComplete)
            assertEquals(priorPageContents, retained.pages.map { File(java.net.URI(it.localUri)).readText() })
        }
    }

    @Test
    fun `remove device copy deletes files and metadata without touching other copies`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 1)
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
        )
        val copy = downloader.saveToDevice("http://server.test/api/graphql", "Manga", manifest.chapter.id)

        downloader.removeDeviceCopy("http://server.test/api/graphql", manifest.chapter.mangaId, manifest.chapter.id)

        assertFalse(File(copy.storagePath!!).exists())
        assertNull(store.getCopy("http://server.test/api/graphql", manifest.chapter.mangaId, manifest.chapter.id))
    }

    @Test
    fun `failed device copy removal retains files and metadata for retry`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(pageCount = 2)
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
        )
        val copy = downloader.saveToDevice("server-a", "Manga", manifest.chapter.id)
        val pageContents = copy.pages.map { File(java.net.URI(it.localUri)).readText() }
        val failingRemoval = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
            deleteCopyDirectory = { false },
        )

        val result = runCatching {
            failingRemoval.removeDeviceCopy("server-a", manifest.chapter.mangaId, manifest.chapter.id)
        }

        assertTrue(result.isFailure)
        assertTrue(File(copy.storagePath!!).isDirectory)
        assertNotNull(store.getCopy("server-a", manifest.chapter.mangaId, manifest.chapter.id))
        assertEquals(pageContents, copy.pages.map { File(java.net.URI(it.localUri)).readText() })
    }

    @Test
    suspend fun `generated copy removal deletes only the selected unreferenced copy`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.int(1..10_000), Arb.int(10_001..20_000)) {
                firstChapterId,
                secondChapterId,
            ->
            val store = FakeClientDeviceChapterCopyRepository()
            val firstManifest = manifest(pageCount = 1, chapterId = firstChapterId)
            val secondManifest = manifest(pageCount = 1, chapterId = secondChapterId)
            val firstDownloader = downloader(store, FakeClientDeviceChapterPageFetcher(firstManifest))
            val secondDownloader = downloader(store, FakeClientDeviceChapterPageFetcher(secondManifest))

            val first = firstDownloader.saveToDevice("server-a", "Manga", firstChapterId)
            val second = secondDownloader.saveToDevice("server-a", "Manga", secondChapterId)

            firstDownloader.removeDeviceCopy("server-a", first.mangaId, first.chapterId)

            assertFalse(File(first.storagePath!!).exists())
            assertNull(store.getCopy("server-a", first.mangaId, first.chapterId))
            assertTrue(File(second.storagePath!!).exists())
            assertNotNull(store.getCopy("server-a", second.mangaId, second.chapterId))
        }
    }

    @Test
    fun `device copy storage path is independent of manga and chapter titles`() = runTest {
        val store = FakeClientDeviceChapterCopyRepository()
        val manifest = manifest(
            pageCount = 1,
            chapterName = "Chapter / unsafe 日本語 title",
        )
        val downloader = downloader(
            store = store,
            fetcher = FakeClientDeviceChapterPageFetcher(manifest),
        )

        val copy = downloader.saveToDevice(
            serverKey = "http://server.test/api/graphql?name=日本語",
            mangaTitle = "Manga : unsafe 日本語 title",
            chapterId = manifest.chapter.id,
        )

        val relativePath = File(copy.storagePath!!).relativeTo(tempDir.toFile()).path
        assertEquals(ClientDeviceChapterCopyPathPolicy.relativeChapterPath(copy.serverKey, manifest), relativePath)
        assertFalse(relativePath.contains("Manga"))
        assertFalse(relativePath.contains("Chapter"))
        assertFalse(relativePath.contains("日本語"))
        assertEquals(listOf("page-0000.img"), copy.pages.map { it.fileName })
    }

    @Test
    fun `device copy generated path segments are ascii compatible and bounded`() {
        val manifest = manifest(
            pageCount = 10_000,
            mangaId = Int.MAX_VALUE,
            chapterId = Int.MAX_VALUE - 1,
            chapterName = "Very long unicode chapter title 日本語 ".repeat(20),
        )

        val relativePath = ClientDeviceChapterCopyPathPolicy.relativeChapterPath(
            serverKey = "https://example.test/with/unicode/日本語/and/a/very/long/query?x=${"z".repeat(256)}",
            manifest = manifest,
        )
        val pageName = ClientDeviceChapterCopyPathPolicy.pageFileName(9_999)

        val segments = relativePath.split(File.separatorChar) + pageName
        assertTrue(segments.all { segment -> segment.all { it.code in 0..127 } })
        assertTrue(segments.all { it.toByteArray(StandardCharsets.UTF_8).size <= 255 })
        assertEquals(listOf(Int.MAX_VALUE.toString(), (Int.MAX_VALUE - 1).toString()), segments.drop(1).take(2))
        assertEquals("page-9999.img", pageName)
    }

    @Test
    fun `device copy path policy separates expected server manga and chapter identities`() {
        val manifest = manifest(pageCount = 1, mangaId = 20, chapterId = 10)

        val baseline = ClientDeviceChapterCopyPathPolicy.relativeChapterPath("server-a", manifest)
        val differentServer = ClientDeviceChapterCopyPathPolicy.relativeChapterPath("server-b", manifest)
        val differentManga = ClientDeviceChapterCopyPathPolicy.relativeChapterPath(
            "server-a",
            manifest(pageCount = 1, mangaId = 21, chapterId = 10),
        )
        val differentChapter = ClientDeviceChapterCopyPathPolicy.relativeChapterPath(
            "server-a",
            manifest(pageCount = 1, mangaId = 20, chapterId = 11),
        )

        assertNotEquals(baseline, differentServer)
        assertNotEquals(baseline, differentManga)
        assertNotEquals(baseline, differentChapter)
    }

    private fun downloader(
        store: ClientDeviceChapterCopyRepository,
        fetcher: ClientDeviceChapterPageFetcher,
        promoteTempDirectory: (File, File) -> Boolean = { tempDir, finalDir -> tempDir.renameTo(finalDir) },
        deleteCopyDirectory: (File) -> Boolean = { directory -> directory.deleteRecursively() },
    ): ClientDeviceChapterCopyDownloader {
        return ClientDeviceChapterCopyDownloader(
            store = store,
            pageFetcher = fetcher,
            rootDirectory = { tempDir.toFile() },
            now = { 1234L },
            promoteTempDirectory = promoteTempDirectory,
            deleteCopyDirectory = deleteCopyDirectory,
        )
    }

    private fun manifest(
        pageCount: Int,
        mangaId: Int = 20,
        chapterId: Int = 10,
        chapterName: String = "Chapter 1",
    ): SuwayomiChapterPageManifest {
        return SuwayomiChapterPageManifest(
            pages = List(pageCount) { index -> "page-$index" },
            chapter = SuwayomiChapterDto(
                id = chapterId,
                mangaId = mangaId,
                name = chapterName,
                url = "/chapter/1",
                realUrl = "https://example.test/chapter/1",
                sourceOrder = 30,
                chapterNumber = 1f,
                uploadDate = 40L,
                fetchedAt = "50",
                scanlator = "scanlator",
                pageCount = pageCount,
            ),
        )
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

private class FakeClientDeviceChapterPageFetcher(
    private val manifest: SuwayomiChapterPageManifest,
    private val failAtIndex: Int? = null,
    transientFailures: Map<Int, Int> = emptyMap(),
) : ClientDeviceChapterPageFetcher {
    private val remainingTransientFailures = transientFailures.toMutableMap()
    val downloadAttempts = mutableMapOf<Int, Int>()

    override suspend fun fetchManifest(chapterId: Int): SuwayomiChapterPageManifest {
        assertEquals(manifest.chapter.id, chapterId)
        return manifest
    }

    override suspend fun downloadPage(sourceUrl: String, destination: File): DownloadedClientDevicePage {
        val index = sourceUrl.substringAfterLast('-').toInt()
        downloadAttempts[index] = downloadAttempts.getOrDefault(index, 0) + 1
        if (index == failAtIndex) {
            error("download failed")
        }
        val remainingFailures = remainingTransientFailures[index] ?: 0
        if (remainingFailures > 0) {
            remainingTransientFailures[index] = remainingFailures - 1
            throw HttpException(500)
        }
        val bytes = "bytes-$sourceUrl".toByteArray()
        destination.parentFile?.mkdirs()
        destination.writeBytes(bytes)
        return DownloadedClientDevicePage(
            mimeType = "image/test",
            byteSize = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) },
        )
    }
}

private class FakeClientDeviceChapterCopyRepository : ClientDeviceChapterCopyRepository {
    private val copies = mutableMapOf<Triple<String, Int, Int>, ClientDeviceChapterCopy>()
    val progressUpdates = mutableListOf<Int>()
    val statusUpdates = mutableListOf<ClientChapterCopyStatus>()

    override suspend fun upsert(upsert: ClientDeviceChapterCopyUpsert): ClientDeviceChapterCopy {
        statusUpdates += upsert.status
        val chapter = upsert.manifest.chapter
        val key = Triple(upsert.serverKey, chapter.mangaId, chapter.id)
        val existing = copies[key]
        val downloadedPageCount = upsert.pages.count { it.isPresent && !it.localUri.isNullOrBlank() }
        return ClientDeviceChapterCopy(
            serverKey = upsert.serverKey,
            mangaId = chapter.mangaId,
            chapterId = chapter.id,
            mangaTitle = upsert.mangaTitle,
            chapterTitle = chapter.name,
            chapterUrl = chapter.url,
            chapterRealUrl = chapter.realUrl,
            sourceOrder = chapter.sourceOrder,
            chapterNumber = chapter.chapterNumber,
            uploadDate = chapter.uploadDate,
            fetchedAt = chapter.fetchedAt,
            scanlator = chapter.scanlator,
            storagePath = upsert.storagePath,
            manifestHash = buildManifestHash(upsert.manifest),
            status = upsert.status,
            freshness = upsert.freshness,
            expectedPageCount = upsert.manifest.pages.size,
            downloadedPageCount = downloadedPageCount,
            createdAt = existing?.createdAt ?: 1L,
            updatedAt = (existing?.updatedAt ?: 1L) + 1L,
            verifiedAt = upsert.verifiedAt,
            orphanedAt = upsert.orphanedAt,
            pages = upsert.pages,
        ).also { copies[key] = it }
    }

    override suspend fun getCopy(serverKey: String, mangaId: Int, chapterId: Int): ClientDeviceChapterCopy? {
        return copies[Triple(serverKey, mangaId, chapterId)]
    }

    override suspend fun deleteCopy(serverKey: String, mangaId: Int, chapterId: Int) {
        copies.remove(Triple(serverKey, mangaId, chapterId))
    }

    override suspend fun updateDownloadProgress(
        serverKey: String,
        mangaId: Int,
        chapterId: Int,
        downloadedPageCount: Int,
    ) {
        progressUpdates += downloadedPageCount
        val key = Triple(serverKey, mangaId, chapterId)
        copies[key] = copies.getValue(key).copy(
            status = ClientChapterCopyStatus.DOWNLOADING,
            freshness = ClientChapterCopyFreshness.INCOMPLETE,
            downloadedPageCount = downloadedPageCount,
        )
    }
}
