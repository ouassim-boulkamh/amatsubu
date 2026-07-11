package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SuwayomiGraphQlClientTest {

    @Test
    fun `generated server information operations retain boundary error normalization`() = runTest {
        val aboutRequests = mutableListOf<String>()
        val aboutClient = graphQlClient(aboutRequests) {
            """{ "errors": [{ "message": "Server information unavailable" }] }"""
        }

        val aboutError = assertThrows<IllegalStateException> { aboutClient.serverAbout() }

        assertEquals("Server information unavailable", aboutError.message)
        assertTrue(aboutRequests.single().contains("ServerAbout"))

        val webUiRequests = mutableListOf<String>()
        val webUiClient = graphQlClient(webUiRequests) {
            """{ "errors": [{ "message": "Web UI information unavailable" }] }"""
        }

        val webUiError = assertThrows<IllegalStateException> { webUiClient.webUiAbout() }

        assertEquals("Web UI information unavailable", webUiError.message)
        assertTrue(webUiRequests.single().contains("WebUiAbout"))

        val settingsRequests = mutableListOf<String>()
        val settingsClient = graphQlClient(settingsRequests) {
            """{ "errors": [{ "message": "Settings unavailable" }] }"""
        }

        val settingsError = assertThrows<IllegalStateException> { settingsClient.serverSettings() }

        assertEquals("Settings unavailable", settingsError.message)
        assertTrue(settingsRequests.single().contains("ServerSettings"))

        val mutationRequests = mutableListOf<String>()
        val mutationClient = graphQlClient(mutationRequests) {
            """{ "errors": [{ "message": "Settings update unavailable" }] }"""
        }

        val mutationError = assertThrows<IllegalStateException> {
            mutationClient.setExtensionRepos(listOf("https://repo.example/index.min.json"))
        }

        assertEquals("Settings update unavailable", mutationError.message)
        assertTrue(mutationRequests.single().contains("SetSettings"))
    }

    @Test
    fun `generated manga read operations retain boundary error normalization`() = runTest {
        val mangaRequests = mutableListOf<String>()
        val mangaClient = graphQlClient(mangaRequests) {
            """{ "errors": [{ "message": "Manga no longer exists" }] }"""
        }

        val mangaError = assertThrows<IllegalStateException> {
            mangaClient.getManga(mangaId = 42)
        }

        assertEquals("Manga no longer exists", mangaError.message)
        assertTrue(mangaRequests.single().contains("GetManga"))
        assertTrue(mangaRequests.single().contains("\"id\":42"))

        val summaryRequests = mutableListOf<String>()
        val summaryClient = graphQlClient(summaryRequests) {
            """{ "errors": [{ "message": "Tracking summary unavailable" }] }"""
        }

        val summaryError = assertThrows<IllegalStateException> {
            summaryClient.getMangaTrackSummary(mangaId = 42)
        }

        assertEquals("Tracking summary unavailable", summaryError.message)
        assertTrue(summaryRequests.single().contains("GetMangaTrackSummary"))
        assertTrue(summaryRequests.single().contains("\"id\":42"))
    }

    @Test
    fun `library manga operation sends in-library filter and ignores stale rows`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "mangas": {
                      "nodes": [
                        {
                          "__typename": "MangaType",
                          "id": 1,
                          "inLibrary": true,
                          "sourceId": "1",
                          "title": "Kept",
                          "url": "/manga/1",
                          "downloadCount": 0,
                          "genre": [],
                          "inLibraryAt": 0,
                          "initialized": false,
                          "meta": [],
                          "status": "ONGOING",
                          "unreadCount": 0,
                          "updateStrategy": "ALWAYS_UPDATE"
                        },
                        {
                          "__typename": "MangaType",
                          "id": 2,
                          "inLibrary": false,
                          "sourceId": "1",
                          "title": "Stale",
                          "url": "/manga/2",
                          "downloadCount": 0,
                          "genre": [],
                          "inLibraryAt": 0,
                          "initialized": false,
                          "meta": [],
                          "status": "ONGOING",
                          "unreadCount": 0,
                          "updateStrategy": "ALWAYS_UPDATE"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val mangas = graphQlClient.getLibraryMangas()

        assertEquals(listOf(1), mangas.map { it.id })
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("GetLibraryMangas"))
            assertTrue(requestBody.contains("inLibrary"))
            assertTrue(requestBody.contains("equalTo: true"))
        }
    }

    @Test
    fun `manga detail fetch sends fetch flags and preserves partial GraphQL errors`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "fetchMangaAndChapters": {
                      "manga": {
                        "__typename": "MangaType",
                        "id": 10,
                        "inLibrary": true,
                        "sourceId": "1",
                        "title": "Fetched Manga",
                        "url": "/manga/10",
                        "artist": null,
                        "author": null,
                        "description": null,
                        "downloadCount": 0,
                        "genre": [],
                        "inLibraryAt": "0",
                        "initialized": true,
                        "chaptersLastFetchedAt": null,
                        "lastFetchedAt": null,
                        "latestFetchedChapter": null,
                        "latestReadChapter": null,
                        "latestUploadedChapter": null,
                        "meta": [],
                        "realUrl": null,
                        "status": "ONGOING",
                        "thumbnailUrl": null,
                        "unreadCount": 0,
                        "updateStrategy": "ALWAYS_UPDATE"
                      },
                      "chapters": [
                        {
                          "__typename": "ChapterType",
                          "id": 20,
                          "mangaId": 10,
                          "name": "Chapter 1",
                          "url": "/chapter/20",
                          "chapterNumber": 1,
                          "fetchedAt": "0",
                          "isDownloaded": false,
                          "isBookmarked": false,
                          "isRead": false,
                          "lastPageRead": 0,
                          "lastReadAt": "0",
                          "realUrl": null,
                          "scanlator": null,
                          "sourceOrder": 1,
                          "uploadDate": "0"
                        }
                      ]
                    }
                  },
                  "errors": [
                    {
                      "message": "Source returned cached chapters"
                    }
                  ]
                }
            """.trimIndent()
        }

        val response = graphQlClient.fetchMangaAndChaptersPartial(
            mangaId = 10,
            fetchManga = true,
            fetchChapters = false,
        )

        assertEquals("Fetched Manga", response.data?.manga?.title)
        assertEquals("Chapter 1", response.data?.chapters?.single()?.name)
        assertEquals(listOf("Source returned cached chapters"), response.errorMessages)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("FetchMangaAndChapters"))
            assertTrue(requestBody.contains("\"id\":10"))
            assertTrue(requestBody.contains("\"fetchManga\":true"))
            assertTrue(requestBody.contains("\"fetchChapters\":false"))
        }
    }

    @Test
    fun `global meta operations use Suwayomi boundary keys`() = runTest {
        val getRequests = mutableListOf<String>()
        val getClient = graphQlClient(getRequests) {
            """
                {
                  "data": {
                    "meta": {
                      "key": "amatsubu.newChapterCheckpoint",
                      "value": "{\"version\":1}"
                    }
                  }
                }
            """.trimIndent()
        }

        val meta = getClient.getGlobalMeta("amatsubu.newChapterCheckpoint")

        assertEquals("amatsubu.newChapterCheckpoint", meta?.key)
        assertEquals("{\"version\":1}", meta?.value)
        getRequests.single().also { requestBody ->
            assertTrue(requestBody.contains("GetGlobalMeta"))
            assertTrue(requestBody.contains("\"key\":\"amatsubu.newChapterCheckpoint\""))
        }

        val setRequests = mutableListOf<String>()
        val setClient = graphQlClient(setRequests) {
            """
                {
                  "data": {
                    "setGlobalMeta": {
                      "meta": {
                        "key": "amatsubu.newChapterCheckpoint",
                        "value": "{\"version\":1,\"lastChapterId\":20}"
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val updated = setClient.setGlobalMeta(
            key = "amatsubu.newChapterCheckpoint",
            value = "{\"version\":1,\"lastChapterId\":20}",
        )

        assertEquals("amatsubu.newChapterCheckpoint", updated.key)
        assertEquals("{\"version\":1,\"lastChapterId\":20}", updated.value)
        setRequests.single().also { requestBody ->
            assertTrue(requestBody.contains("SetGlobalMeta"))
            assertTrue(requestBody.contains("\"key\":\"amatsubu.newChapterCheckpoint\""))
            assertTrue(requestBody.contains("\\\"lastChapterId\\\":20"))
        }

        val deleteRequests = mutableListOf<String>()
        val deleteClient = graphQlClient(deleteRequests) {
            """{ "data": { "deleteGlobalMeta": { "meta": null } } }"""
        }

        assertEquals(null, deleteClient.deleteGlobalMeta("amatsubu.newChapterCheckpoint"))
        deleteRequests.single().also { requestBody ->
            assertTrue(requestBody.contains("DeleteGlobalMeta"))
            assertTrue(requestBody.contains("\"key\":\"amatsubu.newChapterCheckpoint\""))
        }
    }

    @Test
    fun `global meta errors remain boundary errors`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """{ "errors": [{ "message": "Meta store unavailable" }] }"""
        }

        val error = assertThrows<IllegalStateException> {
            graphQlClient.getGlobalMeta("amatsubu.newChapterCheckpoint")
        }

        assertEquals("Meta store unavailable", error.message)
        assertTrue(requests.single().contains("GetGlobalMeta"))
    }

    @Test
    fun `clear cached images uses explicit server cache arguments`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "clearCachedImages": {
                      "cachedPages": false,
                      "cachedThumbnails": true,
                      "downloadedThumbnails": true
                    }
                  }
                }
            """.trimIndent()
        }

        val result = graphQlClient.clearCachedImages(
            cachedThumbnails = true,
            downloadedThumbnails = true,
        )

        assertEquals(false, result.cachedPages)
        assertEquals(true, result.cachedThumbnails)
        assertEquals(true, result.downloadedThumbnails)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("ClearCachedImages"))
            assertTrue(requestBody.contains("\"cachedThumbnails\":true"))
            assertTrue(requestBody.contains("\"downloadedThumbnails\":true"))
            assertFalse(requestBody.contains("\"cachedPages\""))
        }
    }

    @Test
    fun `server manga metadata refresh fetches source state without clearing image caches`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            mangaRefreshResponse()
        }

        val response = graphQlClient.refreshServerMangaFromSource(
            mangaId = 10,
            mode = ServerMangaRefreshMode.Metadata,
        )

        assertEquals("Fetched Manga", response.data?.manga?.title)
        assertEquals(1, requests.size)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("FetchMangaAndChapters"))
            assertTrue(requestBody.contains("\"id\":10"))
            assertTrue(requestBody.contains("\"fetchManga\":true"))
            assertTrue(requestBody.contains("\"fetchChapters\":true"))
            assertFalse(requestBody.contains("ClearCachedImages"))
        }
    }

    @Test
    fun `server manga cover refresh clears thumbnail caches before fetching source state`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) { requestBody ->
            if (requestBody.contains("ClearCachedImages")) {
                """
                    {
                      "data": {
                        "clearCachedImages": {
                          "cachedPages": false,
                          "cachedThumbnails": true,
                          "downloadedThumbnails": true
                        }
                      }
                    }
                """.trimIndent()
            } else {
                mangaRefreshResponse()
            }
        }

        val response = graphQlClient.refreshServerMangaFromSource(
            mangaId = 10,
            mode = ServerMangaRefreshMode.MetadataAndCover,
        )

        assertEquals("Fetched Manga", response.data?.manga?.title)
        assertEquals(2, requests.size)
        requests[0].also { requestBody ->
            assertTrue(requestBody.contains("ClearCachedImages"))
            assertTrue(requestBody.contains("\"cachedThumbnails\":true"))
            assertTrue(requestBody.contains("\"downloadedThumbnails\":true"))
            assertFalse(requestBody.contains("\"cachedPages\""))
        }
        requests[1].also { requestBody ->
            assertTrue(requestBody.contains("FetchMangaAndChapters"))
            assertTrue(requestBody.contains("\"id\":10"))
            assertTrue(requestBody.contains("\"fetchManga\":true"))
            assertTrue(requestBody.contains("\"fetchChapters\":true"))
        }
    }

    @Test
    fun `history operation sends server-side history filter and drops non-history rows`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "chapters": {
                      "nodes": [
                        {
                          "__typename": "ChapterType",
                          "chapterNumber": 1,
                          "fetchedAt": "0",
                          "id": 30,
                          "isDownloaded": false,
                          "isBookmarked": false,
                          "isRead": false,
                          "lastPageRead": 0,
                          "lastReadAt": "0",
                          "mangaId": 10,
                          "name": "Not history",
                          "realUrl": null,
                          "scanlator": null,
                          "sourceOrder": 1,
                          "uploadDate": "0",
                          "url": "/chapter/30",
                          "manga": {
                            "__typename": "MangaType",
                            "artist": null,
                            "author": null,
                            "description": null,
                            "downloadCount": 0,
                            "genre": [],
                            "id": 10,
                            "inLibrary": true,
                            "inLibraryAt": "0",
                            "initialized": true,
                            "chaptersLastFetchedAt": null,
                            "lastFetchedAt": null,
                            "latestFetchedChapter": null,
                            "latestReadChapter": null,
                            "latestUploadedChapter": null,
                            "meta": [],
                            "realUrl": null,
                            "sourceId": "1",
                            "status": "UNKNOWN",
                            "thumbnailUrl": null,
                            "title": "History Manga",
                            "unreadCount": 0,
                            "updateStrategy": "ALWAYS_UPDATE",
                            "url": "/manga/10"
                          }
                        },
                        {
                          "__typename": "ChapterType",
                          "chapterNumber": 2,
                          "fetchedAt": "0",
                          "id": 31,
                          "isDownloaded": false,
                          "isBookmarked": false,
                          "isRead": true,
                          "lastPageRead": 0,
                          "lastReadAt": "1",
                          "mangaId": 10,
                          "name": "Read",
                          "realUrl": null,
                          "scanlator": null,
                          "sourceOrder": 2,
                          "uploadDate": "0",
                          "url": "/chapter/31",
                          "manga": {
                            "__typename": "MangaType",
                            "artist": null,
                            "author": null,
                            "description": null,
                            "downloadCount": 0,
                            "genre": [],
                            "id": 10,
                            "inLibrary": true,
                            "inLibraryAt": "0",
                            "initialized": true,
                            "chaptersLastFetchedAt": null,
                            "lastFetchedAt": null,
                            "latestFetchedChapter": null,
                            "latestReadChapter": null,
                            "latestUploadedChapter": null,
                            "meta": [],
                            "realUrl": null,
                            "sourceId": "1",
                            "status": "UNKNOWN",
                            "thumbnailUrl": null,
                            "title": "History Manga",
                            "unreadCount": 0,
                            "updateStrategy": "ALWAYS_UPDATE",
                            "url": "/manga/10"
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val chapters = graphQlClient.getReadingHistory(limit = 3)

        assertEquals(listOf(31), chapters.map { it.id })
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("GetReadingHistory"))
            assertTrue(requestBody.contains("\"first\":3"))
            assertTrue(requestBody.contains("lastReadAt"))
            assertTrue(requestBody.contains("LAST_READ_AT"))
        }
    }

    @Test
    fun `library update operation sends empty mutation input and decodes running status`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "updateLibraryManga": {
                      "updateStatus": {
                        "isRunning": true
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val running = graphQlClient.updateLibraryMangas()

        assertEquals(true, running)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UpdateLibraryMangas"))
            assertTrue(requestBody.contains("\"input\":{}"))
        }
    }

    @Test
    fun `download dequeue operation de-duplicates ids and decodes returned status`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "dequeueChapterDownloads": {
                      "downloadStatus": {
                        "__typename": "DownloadStatus",
                        "state": "STOPPED",
                        "queue": []
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val status = graphQlClient.dequeueChapterDownloads(listOf(40, 41, 40))

        assertEquals("STOPPED", status.state)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("DequeueChapterDownloads"))
            assertTrue(requestBody.contains("\"ids\":[40,41]"))
        }
    }

    @Test
    fun `extension update operation sends patch booleans and decodes nullable extension payload`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "updateExtension": {
                      "extension": {
                        "__typename": "ExtensionType",
                        "contentWarning": "SAFE",
                        "hasUpdate": false,
                        "iconUrl": "",
                        "isInstalled": true,
                        "isNsfw": false,
                        "isObsolete": false,
                        "lang": "en",
                        "name": "Example",
                        "pkgName": "pkg.example",
                        "source": { "nodes": [] },
                        "versionCode": 1,
                        "versionCodeLong": "1",
                        "versionName": "1.0"
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val extension = graphQlClient.updateExtension(
            pkgName = "pkg.example",
            install = true,
            uninstall = false,
            update = true,
        )

        assertEquals("pkg.example", extension?.pkgName)
        assertEquals(true, extension?.isInstalled)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UpdateExtension"))
            assertTrue(requestBody.contains("\"id\":\"pkg.example\""))
            assertTrue(requestBody.contains("\"install\":true"))
            assertTrue(requestBody.contains("\"uninstall\":false"))
            assertTrue(requestBody.contains("\"update\":true"))
        }
    }

    @Test
    fun `bulk extension action operation de-duplicates ids and decodes returned extensions`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "updateExtensions": {
                      "extensions": [
                        {
                          "__typename": "ExtensionType",
                          "contentWarning": "SAFE",
                          "isInstalled": true,
                          "hasUpdate": false,
                          "iconUrl": "",
                          "isNsfw": false,
                          "isObsolete": false,
                          "lang": "en",
                          "name": "First",
                          "pkgName": "pkg.first",
                          "source": { "nodes": [] },
                          "versionCode": 2,
                          "versionCodeLong": "2",
                          "versionName": "2.0"
                        },
                        {
                          "__typename": "ExtensionType",
                          "contentWarning": "SAFE",
                          "isInstalled": false,
                          "hasUpdate": false,
                          "iconUrl": "",
                          "isNsfw": false,
                          "isObsolete": false,
                          "lang": "ja",
                          "name": "Second",
                          "pkgName": "pkg.second",
                          "source": { "nodes": [] },
                          "versionCode": 1,
                          "versionCodeLong": "1",
                          "versionName": "1.0"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val extensions = graphQlClient.updateExtensions(
            pkgNames = listOf("pkg.first", "pkg.second", "pkg.first"),
            install = false,
            uninstall = true,
            update = false,
        )

        assertEquals(listOf("pkg.first", "pkg.second"), extensions.map { it.pkgName })
        assertEquals(listOf(true, false), extensions.map { it.isInstalled })
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UpdateExtensions"))
            assertTrue(requestBody.contains("\"ids\":[\"pkg.first\",\"pkg.second\"]"))
            assertTrue(requestBody.contains("\"install\":false"))
            assertTrue(requestBody.contains("\"uninstall\":true"))
            assertTrue(requestBody.contains("\"update\":false"))
        }
    }

    @Test
    fun `bulk extension action with empty ids does not call server`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            error("Unexpected request: $it")
        }

        val extensions = graphQlClient.updateExtensions(
            pkgNames = emptyList(),
            install = true,
        )

        assertEquals(emptyList<SuwayomiExtensionDto>(), extensions)
        assertEquals(emptyList<String>(), requests)
    }

    @Test
    fun `tracker update operation sends only provided fields and decodes returned record`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "updateTrack": {
                      "trackRecord": {
                        "__typename": "TrackRecordType",
                        "id": 50,
                        "mangaId": 10,
                        "trackerId": 1,
                        "remoteId": "remote-50",
                        "libraryId": "library-50",
                        "title": "Tracked Manga",
                        "lastChapterRead": 7.0,
                        "totalChapters": 12,
                        "status": 2,
                        "score": 8.0,
                        "displayScore": "8",
                        "remoteUrl": "https://tracker.example/title/remote-50",
                        "startDate": "0",
                        "finishDate": "0",
                        "tracker": {
                          "__typename": "TrackerType",
                          "id": 1,
                          "name": "Example Tracker",
                          "icon": "https://tracker.example/icon.png",
                          "isLoggedIn": true,
                          "authUrl": null,
                          "supportsTrackDeletion": true,
                          "supportsReadingDates": true,
                          "supportsPrivateTracking": true,
                          "statuses": [],
                          "scores": []
                        },
                        "private": true
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val record = graphQlClient.updateTrack(
            recordId = 50,
            lastChapterRead = 7.0,
            scoreString = "8",
            private = true,
        )

        assertEquals(50, record?.id)
        assertEquals(true, record?.private)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UpdateTrack"))
            assertTrue(requestBody.contains("\"recordId\":50"))
            assertTrue(requestBody.contains("\"lastChapterRead\":7.0"))
            assertTrue(requestBody.contains("\"scoreString\":\"8\""))
            assertTrue(requestBody.contains("\"private\":true"))
            assertFalse(requestBody.contains("\"status\""))
            assertFalse(requestBody.contains("\"finishDate\""))
        }
    }

    @Test
    fun `tracker bind operation sends manga tracker remote id and privacy and decodes record`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "bindTrack": {
                      "trackRecord": {
                        "__typename": "TrackRecordType",
                        "id": 60,
                        "mangaId": 10,
                        "trackerId": 1,
                        "remoteId": "remote-60",
                        "libraryId": "library-60",
                        "title": "Bound Manga",
                        "lastChapterRead": 3.0,
                        "totalChapters": 24,
                        "status": 1,
                        "score": 0.0,
                        "displayScore": "0",
                        "remoteUrl": "https://tracker.example/title/remote-60",
                        "startDate": "0",
                        "finishDate": "0",
                        "private": true,
                        "tracker": {
                          "__typename": "TrackerType",
                          "id": 1,
                          "name": "Example Tracker",
                          "icon": "https://tracker.example/icon.png",
                          "isLoggedIn": true,
                          "authUrl": null,
                          "supportsTrackDeletion": true,
                          "supportsReadingDates": true,
                          "supportsPrivateTracking": true,
                          "statuses": [
                            {
                              "value": 1,
                              "name": "Reading"
                            }
                          ],
                          "scores": ["0", "1", "2"]
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val record = graphQlClient.bindTrack(
            mangaId = 10,
            trackerId = 1,
            remoteId = 1234567890,
            private = true,
        )

        assertEquals(60, record.id)
        assertEquals("remote-60", record.remoteId)
        assertEquals("library-60", record.libraryId)
        assertEquals(true, record.private)
        assertEquals("Example Tracker", record.tracker?.name)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("BindTrack"))
            assertTrue(requestBody.contains("\"mangaId\":10"))
            assertTrue(requestBody.contains("\"trackerId\":1"))
            assertTrue(requestBody.contains("\"remoteId\":\"1234567890\""))
            assertTrue(requestBody.contains("\"private\":true"))
            assertTrue(requestBody.contains("AmatsubuTrackRecord"))
            assertTrue(requestBody.contains("AmatsubuTracker"))
        }
    }

    @Test
    fun `generated tracker record operations preserve the boundary DTO contracts`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) { requestBody ->
            when {
                requestBody.contains("TrackRecords") ->
                    """{ "data": { "trackRecords": { "nodes": [${trackerRecordJson()}] } } }"""
                requestBody.contains("SearchTracker") ->
                    """
                        {
                          "data": {
                            "searchTracker": {
                              "trackSearches": [{
                                "__typename": "TrackSearchType",
                                "id": 61, "trackerId": 1, "remoteId": "remote-61", "libraryId": null,
                                "title": "Search result", "lastChapterRead": 0.0, "totalChapters": 24,
                                "trackingUrl": "https://tracker.example/title/remote-61",
                                "coverUrl": "https://tracker.example/cover.png", "summary": "Summary",
                                "publishingStatus": "ONGOING", "publishingType": "MANGA", "startDate": "0",
                                "status": 1, "score": 0.0, "startedReadingDate": "0",
                                "finishedReadingDate": "0", "private": false
                              }]
                            }
                          }
                        }
                    """.trimIndent()
                requestBody.contains("BindTrackRecord") ->
                    """{ "data": { "bindTrackRecord": { "trackRecord": ${trackerRecordJson()} } } }"""
                requestBody.contains("FetchTrack") ->
                    """{ "data": { "fetchTrack": { "trackRecord": ${trackerRecordJson()} } } }"""
                else -> error("Unexpected request: $requestBody")
            }
        }

        assertEquals(60, graphQlClient.getTrackRecords(mangaId = 10).single().id)
        assertEquals("Search result", graphQlClient.searchTracker(1, "search").single().title)
        assertEquals(60, graphQlClient.bindTrackRecord(10, 60).id)
        assertEquals(60, graphQlClient.fetchTrack(60).id)

        assertTrue(requests[0].contains("TrackRecords"))
        assertTrue(requests[0].contains("\"mangaId\":10"))
        assertTrue(requests[1].contains("SearchTracker"))
        assertTrue(requests[1].contains("\"query\":\"search\""))
        assertTrue(requests[2].contains("BindTrackRecord"))
        assertTrue(requests[2].contains("\"trackRecordId\":60"))
        assertTrue(requests[3].contains("FetchTrack"))
        assertTrue(requests[3].contains("\"recordId\":60"))
    }

    @Test
    fun `tracker mutation refetch failure preserves accepted mutation and recovers on next fetch`() = runTest {
        val requests = mutableListOf<String>()
        var trackRecordsAttempts = 0
        val graphQlClient = graphQlClient(requests) { requestBody ->
            when {
                requestBody.contains("UpdateTrack") ->
                    """{ "data": { "updateTrack": { "trackRecord": ${trackerRecordJson()} } } }"""
                requestBody.contains("TrackRecords") -> {
                    trackRecordsAttempts += 1
                    if (trackRecordsAttempts == 1) {
                        """{ "errors": [{ "message": "Tracker refetch unavailable" }] }"""
                    } else {
                        """{ "data": { "trackRecords": { "nodes": [${trackerRecordJson()}] } } }"""
                    }
                }
                else -> error("Unexpected request: $requestBody")
            }
        }

        val accepted = graphQlClient.updateTrack(recordId = 60, private = true)
            ?: error("Expected accepted tracker mutation to return a record")

        val refetchFailure = assertThrows<IllegalStateException> {
            graphQlClient.getTrackRecords(mangaId = 10)
        }
        val recovered = graphQlClient.getTrackRecords(mangaId = 10)

        assertEquals("Tracker refetch unavailable", refetchFailure.message)
        assertEquals(60, accepted.id)
        assertEquals(listOf(60), recovered.map { it.id })
        assertEquals(
            setOf(ServerStateEntity.Manga(10), ServerStateEntity.Trackers(10)),
            serverTrackAffectedEntities(accepted.mangaId),
        )
        assertTrue(requests[0].contains("UpdateTrack"))
        assertTrue(requests[1].contains("TrackRecords"))
        assertTrue(requests[2].contains("TrackRecords"))
    }

    @Test
    fun `tracker unbind operation sends delete remote flag and decodes nullable record`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "unbindTrack": {
                      "trackRecord": null
                    }
                  }
                }
            """.trimIndent()
        }

        val record = graphQlClient.unbindTrack(
            recordId = 60,
            deleteRemoteTrack = true,
        )

        assertEquals(null, record)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UnbindTrack"))
            assertTrue(requestBody.contains("\"recordId\":60"))
            assertTrue(requestBody.contains("\"deleteRemoteTrack\":true"))
            assertTrue(requestBody.contains("AmatsubuTrackRecord"))
        }
    }

    @Test
    fun `sync and settings operations keep backup and settings contracts at the boundary`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) { requestBody ->
            when {
                requestBody.contains("StartSync") -> """
                    {
                      "data": {
                        "startSync": {
                          "result": "SUCCESS"
                        }
                      }
                    }
                """.trimIndent()
                requestBody.contains("SetSettings") -> """
                    {
                      "data": {
                        "setSettings": {
                          "settings": {
                            "__typename": "SettingsType",
                            "authMode": "NONE",
                            "authPassword": "",
                            "authUsername": "",
                            "autoDownloadIgnoreReUploads": false,
                            "autoDownloadNewChapters": false,
                            "autoDownloadNewChaptersLimit": 0,
                            "autoBackupIncludeCategories": false,
                            "autoBackupIncludeChapters": false,
                            "autoBackupIncludeClientData": false,
                            "autoBackupIncludeHistory": false,
                            "autoBackupIncludeManga": false,
                            "autoBackupIncludeServerSettings": false,
                            "autoBackupIncludeTracking": false,
                            "backupInterval": 0,
                            "backupPath": "",
                            "backupTTL": 0,
                            "backupTime": "00:00",
                            "basicAuthEnabled": false,
                            "basicAuthPassword": "",
                            "basicAuthUsername": "",
                            "ip": "0.0.0.0",
                            "port": 4567,
                            "downloadAsCbz": false,
                            "downloadsPath": "",
                            "electronPath": "",
                            "excludeCompleted": false,
                            "excludeEntryWithUnreadChapters": false,
                            "excludeNotStarted": false,
                            "excludeUnreadChapters": false,
                            "extensionRepos": [
                              "https://repo.example/index.min.json"
                            ],
                            "flareSolverrAsResponseFallback": false,
                            "globalUpdateInterval": 0.0,
                            "initialOpenInBrowserEnabled": false,
                            "localSourcePath": "",
                            "maxLogFileSize": "0",
                            "maxLogFiles": 0,
                            "maxLogFolderSize": "0",
                            "maxSourcesInParallel": 1,
                            "socksProxyEnabled": false,
                            "socksProxyHost": "",
                            "socksProxyPassword": "",
                            "socksProxyPort": "",
                            "socksProxyUsername": "",
                            "socksProxyVersion": 5,
                            "flareSolverrEnabled": false,
                            "flareSolverrSessionName": "",
                            "flareSolverrSessionTtl": 0,
                            "flareSolverrTimeout": 0,
                            "flareSolverrUrl": "",
                            "debugLogsEnabled": false,
                            "systemTrayEnabled": false,
                            "updateMangas": false,
                            "webUIChannel": "BUNDLED",
                            "webUIFlavor": "WEBUI",
                            "webUIInterface": "BROWSER",
                            "webUIUpdateCheckInterval": 0.0
                          }
                        }
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected request: $requestBody")
            }
        }

        val sync = graphQlClient.startSync()
        val settings = graphQlClient.setExtensionRepos(listOf("https://repo.example/index.min.json"))

        assertEquals("SUCCESS", sync.result)
        assertEquals(listOf("https://repo.example/index.min.json"), settings.extensionRepos)
        assertTrue(requests[0].contains("StartSync"))
        requests[1].also { requestBody ->
            assertTrue(requestBody.contains("SetSettings"))
            assertTrue(requestBody.contains("\"extensionRepos\":[\"https://repo.example/index.min.json\"]"))
        }
    }

    @Test
    fun `source details operation requests filters preferences and decodes configurable source payload`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "source": {
                      "baseUrl": "https://source.example",
                      "contentWarning": "SAFE",
                      "displayName": "Example Source",
                      "homeUrl": "https://source.example/home",
                      "iconUrl": "/api/v1/source/icon/728120260708001",
                      "id": "728120260708001",
                      "isConfigurable": true,
                      "isNsfw": false,
                      "lang": "en",
                      "name": "Example",
                      "supportsLatest": true,
                      "filters": [
                        {
                          "__typename": "SortFilter",
                          "name": "Sort",
                          "values": ["Title", "Latest"],
                          "defaultSort": {
                            "index": 1,
                            "ascending": false
                          }
                        }
                      ],
                      "preferences": [
                        {
                          "__typename": "ListPreference",
                          "key": "quality",
                          "title": "Quality",
                          "summary": null,
                          "enabled": true,
                          "visible": true,
                          "currentString": "high",
                          "defaultString": "medium",
                          "entries": ["High", "Medium"],
                          "entryValues": ["high", "medium"]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val details = graphQlClient.sourceDetails("728120260708001")

        assertEquals(true, details.isConfigurable)
        assertEquals("SortFilter", details.filters.single().type)
        assertEquals(SuwayomiSortSelectionDto(index = 1, ascending = false), details.filters.single().defaultSort)
        assertEquals("quality", details.preferences.single().key)
        assertEquals(listOf("high", "medium"), details.preferences.single().entryValues)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("SourceDetails"))
            assertTrue(requestBody.contains("\"id\":\"728120260708001\""))
            assertTrue(requestBody.contains("filters"))
            assertTrue(requestBody.contains("preferences"))
            assertTrue(requestBody.contains("AmatsubuSourceFilter"))
            assertTrue(requestBody.contains("AmatsubuSourcePreference"))
        }
    }

    @Test
    fun `source manga fetch sends search filters without storing source authority locally`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "fetchSourceManga": {
                      "hasNextPage": true,
                      "mangas": [
                        {
                          "__typename": "MangaType",
                          "artist": null,
                          "author": null,
                          "description": null,
                          "downloadCount": 0,
                          "genre": [],
                          "id": 1192,
                          "inLibrary": false,
                          "inLibraryAt": "2026-07-10T00:00:00Z",
                          "initialized": true,
                          "chaptersLastFetchedAt": null,
                          "lastFetchedAt": null,
                          "latestFetchedChapter": null,
                          "latestReadChapter": null,
                          "latestUploadedChapter": null,
                          "meta": [],
                          "realUrl": null,
                          "sourceId": "728120260708001",
                          "status": "UNKNOWN",
                          "thumbnailUrl": null,
                          "title": "Amatsubu Smoke Test",
                          "unreadCount": 0,
                          "updateStrategy": "ALWAYS_UPDATE",
                          "url": "/manga/smoke"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val page = graphQlClient.fetchSourceManga(
            sourceId = "728120260708001",
            type = FetchSourceMangaType.SEARCH,
            page = 2,
            queryText = "smoke",
            filters = listOf(
                SuwayomiSourceFilterChange(
                    position = 0,
                    groupChange = SuwayomiSourceFilterChange(
                        position = 1,
                        triState = SuwayomiTriState.INCLUDE,
                    ),
                    sortState = SuwayomiSortSelectionDto(index = 2, ascending = false),
                ),
                SuwayomiSourceFilterChange(
                    position = 2,
                    checkBoxState = true,
                    selectState = 3,
                    textState = "translated",
                ),
            ),
        )

        assertEquals(true, page.hasNextPage)
        assertEquals("Amatsubu Smoke Test", page.mangas.single().title)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("FetchSourceManga"))
            assertTrue(requestBody.contains("\"source\":\"728120260708001\""))
            assertTrue(requestBody.contains("\"type\":\"SEARCH\""))
            assertTrue(requestBody.contains("\"page\":2"))
            assertTrue(requestBody.contains("\"query\":\"smoke\""))
            assertTrue(requestBody.contains("\"groupChange\":{\"position\":1,\"triState\":\"INCLUDE\"}"))
            assertTrue(requestBody.contains("\"sortState\":{\"ascending\":false,\"index\":2}"))
            assertTrue(requestBody.contains("\"checkBoxState\":true"))
            assertTrue(requestBody.contains("\"selectState\":3"))
            assertTrue(requestBody.contains("\"textState\":\"translated\""))
        }
    }

    @Test
    fun `source preference update sends server-mediated preference change payload`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "updateSourcePreference": {
                      "preferences": [
                        {
                          "__typename": "MultiSelectListPreference",
                          "key": "enabledLanguages",
                          "title": "Languages",
                          "summary": null,
                          "enabled": true,
                          "visible": true,
                          "currentStringList": ["en", "ja"],
                          "defaultStringList": ["en"],
                          "entries": ["English", "Japanese"],
                          "entryValues": ["en", "ja"],
                          "dialogTitle": "Choose languages",
                          "dialogMessage": "Server-owned source preference"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val preferences = graphQlClient.updateSourcePreference(
            sourceId = "728120260708001",
            change = SuwayomiSourcePreferenceChange(
                position = 4,
                checkBoxState = true,
                editTextState = "https://fixture.example",
                listState = "high",
                multiSelectState = listOf("en", "ja"),
                switchState = false,
            ),
        )

        assertEquals("enabledLanguages", preferences.single().key)
        assertEquals(listOf("en", "ja"), preferences.single().currentStringList)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("UpdateSourcePreference"))
            assertTrue(requestBody.contains("\"source\":\"728120260708001\""))
            assertTrue(requestBody.contains("\"position\":4"))
            assertTrue(requestBody.contains("\"checkBoxState\":true"))
            assertTrue(requestBody.contains("\"editTextState\":\"https://fixture.example\""))
            assertTrue(requestBody.contains("\"listState\":\"high\""))
            assertTrue(requestBody.contains("\"multiSelectState\":[\"en\",\"ja\"]"))
            assertTrue(requestBody.contains("\"switchState\":false"))
            assertTrue(requestBody.contains("AmatsubuSourcePreference"))
        }
    }

    @Test
    fun `Tsumiru issue 27 named category query filters by inLibrary and category id`() = runTest {
        var requestBody = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestBody = chain.request().bodyString()
                jsonResponse(
                    chain.request(),
                    """
                        {
                          "data": {
                            "mangas": {
                              "nodes": [
                                {
                                  "__typename": "MangaType",
                                  "id": 1,
                                  "inLibrary": true,
                                  "sourceId": "1",
                                  "title": "Kept",
                                  "url": "/manga/1",
                                  "downloadCount": 0,
                                  "genre": [],
                                  "inLibraryAt": 0,
                                  "initialized": false,
                                  "meta": [],
                                  "status": "ONGOING",
                                  "unreadCount": 0,
                                  "updateStrategy": "ALWAYS_UPDATE"
                                },
                                {
                                  "__typename": "MangaType",
                                  "id": 2,
                                  "inLibrary": false,
                                  "sourceId": "1",
                                  "title": "Removed",
                                  "url": "/manga/2",
                                  "downloadCount": 0,
                                  "genre": [],
                                  "inLibraryAt": 0,
                                  "initialized": false,
                                  "meta": [],
                                  "status": "ONGOING",
                                  "unreadCount": 0,
                                  "updateStrategy": "ALWAYS_UPDATE"
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                )
            }
            .build()
        val graphQlClient = SuwayomiGraphQlClient(
            client = client,
            endpoint = { "http://example.org/api/graphql" },
        )

        val mangas = graphQlClient.getCategoryMangas(categoryId = 7)

        assertEquals(listOf(1), mangas.map { it.id })
        assertTrue(requestBody.contains("GetNamedCategoryMangas"))
        assertTrue(requestBody.contains("condition: { inLibrary: ${'$'}"))
        assertTrue(requestBody.contains("categoryIds"))
        assertTrue(requestBody.contains("\"categoryIds\":[7]"))
        assertTrue(requestBody.contains("\"inLibrary\":true"))
    }

    @Test
    fun `Batch F schema drift errors are surfaced as compatibility failures`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                jsonResponse(
                    chain.request(),
                    """
                        {
                          "data": null,
                          "errors": [
                            {
                              "message": "Unknown argument 'condition' on field 'Query.sources'."
                            }
                          ]
                        }
                    """.trimIndent(),
                )
            }
            .build()
        val graphQlClient = SuwayomiGraphQlClient(
            client = client,
            endpoint = { "http://example.org/api/graphql" },
        )

        val error = assertThrows<IllegalStateException> {
            graphQlClient.sourceList()
        }

        assertEquals("Unknown argument 'condition' on field 'Query.sources'.", error.message)
    }

    @Test
    fun `source list uses generated operation and maps it to the boundary DTO`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "sources": {
                      "nodes": [
                        {
                          "baseUrl": "https://example.org",
                          "contentWarning": "NSFW",
                          "displayName": "Example",
                          "homeUrl": "https://example.org/home",
                          "iconUrl": "https://example.org/icon.png",
                          "id": "42",
                          "isConfigurable": true,
                          "isNsfw": true,
                          "lang": "en",
                          "name": "Example source",
                          "supportsLatest": true
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val sources = graphQlClient.sourceList()

        assertEquals(listOf("42"), sources.map { it.id })
        assertEquals("NSFW", sources.single().contentWarning)
        assertTrue(sources.single().isConfigurable)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("SourceList"))
            assertTrue(requestBody.contains("contentWarning"))
        }
    }

    @Test
    fun `tracker list uses generated operation and maps it to the boundary DTO`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "trackers": {
                      "nodes": [
                        {
                          "id": 7,
                          "name": "Example tracker",
                          "icon": "https://example.org/icon.png",
                          "isLoggedIn": true,
                          "authUrl": "https://example.org/auth",
                          "supportsTrackDeletion": true,
                          "supportsReadingDates": true,
                          "supportsPrivateTracking": false,
                          "statuses": [{ "value": 1, "name": "Reading" }],
                          "scores": ["0", "10"]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }

        val trackers = graphQlClient.trackerList()

        trackers.single().also { tracker ->
            assertEquals(7, tracker.id)
            assertEquals("Example tracker", tracker.name)
            assertTrue(tracker.isLoggedIn)
            assertEquals(listOf(SuwayomiTrackStatusDto(1, "Reading")), tracker.statuses)
        }
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("TrackerList"))
            assertTrue(requestBody.contains("supportsPrivateTracking"))
        }
    }

    @Test
    fun `tracker credential login uses generated operation and maps it to the boundary DTO`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "loginTrackerCredentials": {
                      "isLoggedIn": true,
                      "tracker": {
                        "id": 7,
                        "name": "Example tracker",
                        "icon": "https://example.org/icon.png",
                        "isLoggedIn": true,
                        "authUrl": null,
                        "supportsTrackDeletion": true,
                        "supportsReadingDates": false,
                        "supportsPrivateTracking": true,
                        "statuses": [{ "value": 1, "name": "Reading" }],
                        "scores": ["0", "10"]
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val tracker = graphQlClient.loginTrackerCredentials(
            trackerId = 7,
            username = "alice",
            password = "not-a-real-password",
        )

        assertEquals(7, tracker.id)
        assertTrue(tracker.isLoggedIn)
        assertEquals(listOf(SuwayomiTrackStatusDto(1, "Reading")), tracker.statuses)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("LoginTrackerCredentials"))
            assertTrue(requestBody.contains("\"trackerId\":7"))
            assertTrue(requestBody.contains("\"username\":\"alice\""))
            assertTrue(requestBody.contains("\"password\":\"not-a-real-password\""))
        }
    }

    @Test
    fun `tracker credential login surfaces generated GraphQL errors`() = runTest {
        val graphQlClient = graphQlClient(mutableListOf()) {
            """
                {
                  "errors": [
                    { "message": "Could not find tracker" }
                  ]
                }
            """.trimIndent()
        }

        val error = assertThrows<IllegalStateException> {
            graphQlClient.loginTrackerCredentials(
                trackerId = -1,
                username = "alice",
                password = "not-a-real-password",
            )
        }

        assertEquals("Could not find tracker", error.message)
    }

    @Test
    fun `tracker OAuth login uses generated operation and maps it to the boundary DTO`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "loginTrackerOAuth": {
                      "isLoggedIn": true,
                      "tracker": {
                        "id": 2,
                        "name": "AniList",
                        "icon": "https://example.org/anilist.png",
                        "isLoggedIn": true,
                        "authUrl": null,
                        "supportsTrackDeletion": true,
                        "supportsReadingDates": true,
                        "supportsPrivateTracking": true,
                        "statuses": [{ "value": 1, "name": "Reading" }],
                        "scores": ["0", "10"]
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val tracker = graphQlClient.loginTrackerOAuth(
            trackerId = 2,
            callbackUrl = "amatsubu://tracker/callback",
        )

        assertEquals(2, tracker.id)
        assertTrue(tracker.isLoggedIn)
        assertEquals(listOf(SuwayomiTrackStatusDto(1, "Reading")), tracker.statuses)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("LoginTrackerOAuth"))
            assertTrue(requestBody.contains("\"trackerId\":2"))
            assertTrue(requestBody.contains("\"callbackUrl\":\"amatsubu://tracker/callback\""))
        }
    }

    @Test
    fun `tracker logout uses generated operation and maps it to the boundary DTO`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "logoutTracker": {
                      "isLoggedIn": false,
                      "tracker": {
                        "id": 7,
                        "name": "Example tracker",
                        "icon": "https://example.org/icon.png",
                        "isLoggedIn": false,
                        "authUrl": null,
                        "supportsTrackDeletion": true,
                        "supportsReadingDates": false,
                        "supportsPrivateTracking": true,
                        "statuses": [{ "value": 1, "name": "Reading" }],
                        "scores": ["0", "10"]
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        val tracker = graphQlClient.logoutTracker(trackerId = 7)

        assertEquals(7, tracker.id)
        assertFalse(tracker.isLoggedIn)
        assertEquals(listOf(SuwayomiTrackStatusDto(1, "Reading")), tracker.statuses)
        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("LogoutTracker"))
            assertTrue(requestBody.contains("\"trackerId\":7"))
        }
    }

    @Test
    fun `track progress uses generated operation`() = runTest {
        val requests = mutableListOf<String>()
        val graphQlClient = graphQlClient(requests) {
            """
                {
                  "data": {
                    "trackProgress": {
                      "clientMutationId": null
                    }
                  }
                }
            """.trimIndent()
        }

        graphQlClient.trackProgress(mangaId = 42)

        requests.single().also { requestBody ->
            assertTrue(requestBody.contains("TrackProgress"))
            assertTrue(requestBody.contains("\"mangaId\":42"))
        }
    }

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun trackerRecordJson() =
        """
            {
              "__typename": "TrackRecordType",
              "id": 60, "mangaId": 10, "trackerId": 1, "remoteId": "remote-60",
              "libraryId": "library-60", "title": "Bound Manga", "lastChapterRead": 3.0,
              "totalChapters": 24, "status": 1, "score": 0.0, "displayScore": "0",
              "remoteUrl": "https://tracker.example/title/remote-60", "startDate": "0",
              "finishDate": "0", "private": true,
              "tracker": {
                "__typename": "TrackerType",
                "id": 1, "name": "Example Tracker", "icon": "https://tracker.example/icon.png",
                "isLoggedIn": true, "authUrl": null, "supportsTrackDeletion": true,
                "supportsReadingDates": true, "supportsPrivateTracking": true,
                "statuses": [{ "value": 1, "name": "Reading" }], "scores": ["0", "1", "2"]
              }
            }
        """.trimIndent()

    private fun mangaRefreshResponse() =
        """
            {
              "data": {
                "fetchMangaAndChapters": {
                  "manga": {
                    "__typename": "MangaType",
                    "id": 10,
                    "inLibrary": true,
                    "sourceId": "1",
                    "title": "Fetched Manga",
                    "url": "/manga/10",
                    "artist": null,
                    "author": null,
                    "description": null,
                    "downloadCount": 0,
                    "genre": [],
                    "inLibraryAt": "0",
                    "initialized": true,
                    "chaptersLastFetchedAt": null,
                    "lastFetchedAt": null,
                    "latestFetchedChapter": null,
                    "latestReadChapter": null,
                    "latestUploadedChapter": null,
                    "meta": [],
                    "realUrl": null,
                    "status": "ONGOING",
                    "thumbnailUrl": null,
                    "unreadCount": 0,
                    "updateStrategy": "ALWAYS_UPDATE"
                  },
                  "chapters": [
                    {
                      "__typename": "ChapterType",
                      "id": 20,
                      "mangaId": 10,
                      "name": "Chapter 1",
                      "url": "/chapter/20",
                      "chapterNumber": 1,
                      "fetchedAt": "0",
                      "isDownloaded": false,
                      "isBookmarked": false,
                      "isRead": false,
                      "lastPageRead": 0,
                      "lastReadAt": "0",
                      "realUrl": null,
                      "scanlator": null,
                      "sourceOrder": 1,
                      "uploadDate": "0"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

    private fun jsonResponse(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun graphQlClient(
        requests: MutableList<String>,
        responseBody: (String) -> String,
    ): SuwayomiGraphQlClient {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBody = chain.request().bodyString()
                requests += requestBody
                jsonResponse(chain.request(), responseBody(requestBody))
            }
            .build()
        return SuwayomiGraphQlClient(
            client = client,
            endpoint = { "http://example.org/api/graphql" },
        )
    }
}
