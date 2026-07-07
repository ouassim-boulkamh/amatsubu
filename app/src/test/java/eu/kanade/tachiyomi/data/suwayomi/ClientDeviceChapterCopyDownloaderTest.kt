package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
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
        assertEquals(listOf("bytes-page-0", "bytes-page-1"), copy.pages.map { File(java.net.URI(it.localUri)).readText() })
        assertEquals(copy.pages.map { sha256(File(java.net.URI(it.localUri)).readBytes()) }, copy.pages.map { it.sha256 })
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

    private fun downloader(
        store: ClientDeviceChapterCopyRepository,
        fetcher: ClientDeviceChapterPageFetcher,
    ): ClientDeviceChapterCopyDownloader {
        return ClientDeviceChapterCopyDownloader(
            store = store,
            pageFetcher = fetcher,
            rootDirectory = { tempDir.toFile() },
            now = { 1234L },
        )
    }

    private fun manifest(pageCount: Int): SuwayomiChapterPageManifest {
        return SuwayomiChapterPageManifest(
            pages = List(pageCount) { index -> "page-$index" },
            chapter = SuwayomiChapterDto(
                id = 10,
                mangaId = 20,
                name = "Chapter 1",
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
