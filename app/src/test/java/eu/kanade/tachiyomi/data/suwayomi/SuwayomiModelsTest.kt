package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SuwayomiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `manga genre normalization drops blank entries`() {
        val manga = suwayomiManga(genre = listOf(""))

        assertNull(manga.normalizedGenre())
    }

    @Test
    fun `manga genre normalization trims and deduplicates entries`() {
        val manga = suwayomiManga(genre = listOf(" Action ", "", "Action", "Drama"))

        assertEquals(listOf("Action", "Drama"), manga.normalizedGenre())
    }

    @Test
    fun `server cover last modified uses newest server fetch timestamp`() {
        val manga = suwayomiManga(genre = emptyList()).copy(
            lastFetchedAt = 10,
            chaptersLastFetchedAt = 20,
            latestFetchedChapter = SuwayomiLatestFetchedChapterDto(fetchedAt = 30),
        )

        assertEquals(30_000L, manga.serverCoverLastModified())
    }

    @Test
    fun `server cover last modified falls back to zero without fetch timestamps`() {
        val manga = suwayomiManga(genre = emptyList())

        assertEquals(0L, manga.serverCoverLastModified())
    }

    @Test
    fun `fetch chapter pages response accepts null payload with GraphQL errors`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchChapterPagesData.serializer()),
            """
                {
                  "data": {
                    "fetchChapterPages": null
                  },
                  "errors": [
                    {
                      "message": "Log in via WebView and rent or purchase this chapter to read."
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertNull(response.data?.fetchChapterPages)
        assertEquals(
            "Log in via WebView and rent or purchase this chapter to read.",
            response.errors.single().message,
        )
    }

    @Test
    fun `fetch chapter pages response decodes pages with chapter manifest fields`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchChapterPagesData.serializer()),
            """
                {
                  "data": {
                    "fetchChapterPages": {
                      "pages": [
                        "/api/v1/manga/46/chapter/348/page/0",
                        "/api/v1/manga/46/chapter/348/page/1"
                      ],
                      "chapter": {
                        "id": 850,
                        "mangaId": 46,
                        "name": "Chapter 347",
                        "url": "/chapters/01KSJDV4VE67C3XAHAMT8C1VVX",
                        "realUrl": "https://weebcentral.com/chapters/01KSJDV4VE67C3XAHAMT8C1VVX",
                        "sourceOrder": 348,
                        "chapterNumber": 347.0,
                        "uploadDate": 1779808703346,
                        "fetchedAt": "1781503338",
                        "pageCount": 2,
                        "isDownloaded": true,
                        "isRead": true,
                        "isBookmarked": false,
                        "lastPageRead": 0,
                        "lastReadAt": 1782391027,
                        "scanlator": "Unknown"
                      }
                    }
                  }
                }
            """.trimIndent(),
        )

        val payload = response.data?.fetchChapterPages
        assertEquals(2, payload?.pages?.size)
        assertEquals(850, payload?.chapter?.id)
        assertEquals(46, payload?.chapter?.mangaId)
        assertEquals("Chapter 347", payload?.chapter?.name)
        assertEquals("https://weebcentral.com/chapters/01KSJDV4VE67C3XAHAMT8C1VVX", payload?.chapter?.realUrl)
        assertEquals(348, payload?.chapter?.sourceOrder)
        assertEquals(347.0f, payload?.chapter?.chapterNumber)
        assertEquals("1781503338", payload?.chapter?.fetchedAt)
        assertEquals(2, payload?.chapter?.pageCount)
        assertEquals(true, payload?.chapter?.isDownloaded)
    }

    @Test
    fun `fetch manga and chapters response accepts payload data with GraphQL errors`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchMangaAndChaptersData.serializer()),
            """
                {
                  "data": {
                    "fetchMangaAndChapters": {
                      "manga": {
                        "artist": "Artist",
                        "author": "Author",
                        "description": "Description",
                        "downloadCount": 0,
                        "genre": ["Action"],
                        "id": 100,
                        "inLibrary": true,
                        "initialized": true,
                        "meta": [],
                        "sourceId": "1",
                        "status": "ONGOING",
                        "title": "Cached Manga",
                        "unreadCount": 1,
                        "updateStrategy": "ALWAYS_UPDATE",
                        "url": "/manga/100"
                      },
                      "chapters": [
                        {
                          "chapterNumber": 1.0,
                          "fetchedAt": "1783261200000",
                          "id": 200,
                          "isDownloaded": false,
                          "isBookmarked": false,
                          "isRead": false,
                          "lastPageRead": 0,
                          "lastReadAt": 0,
                          "mangaId": 100,
                          "name": "Chapter 1",
                          "sourceOrder": 1,
                          "uploadDate": 1783261200000,
                          "url": "/chapter/200"
                        }
                      ]
                    }
                  },
                  "errors": [
                    {
                      "message": "Source refresh failed; returned cached manga and chapters."
                    }
                  ]
                }
            """.trimIndent(),
        )

        val payload = response.data?.fetchMangaAndChapters
        assertEquals("Cached Manga", payload?.manga?.title)
        assertEquals("Chapter 1", payload?.chapters?.single()?.name)
        assertEquals(
            "Source refresh failed; returned cached manga and chapters.",
            response.errors.single().message,
        )
    }

    @Test
    fun `fetch manga and chapters response accepts null payload with GraphQL errors`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchMangaAndChaptersData.serializer()),
            """
                {
                  "data": {
                    "fetchMangaAndChapters": null
                  },
                  "errors": [
                    {
                      "message": "Manga not found"
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertNull(response.data?.fetchMangaAndChapters)
        assertEquals("Manga not found", response.errors.single().message)
    }

    @Test
    fun `extension response decodes current fields without old compatibility fields`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchExtensionsData.serializer()),
            """
                {
                  "data": {
                    "fetchExtensions": {
                      "extensions": [
                        {
                          "apkName": "tachiyomi-en.example.apk",
                          "apkUrl": "https://example.org/tachiyomi-en.example.apk",
                          "contentWarning": "MIXED",
                          "extensionLib": "1.5",
                          "hasUpdate": true,
                          "iconUrl": "/api/v1/extension/icon/pkg.example",
                          "isInstalled": true,
                          "isObsolete": false,
                          "lang": "en",
                          "name": "Example",
                          "pkgName": "pkg.example",
                          "source": {
                            "nodes": [
                              {
                                "baseUrl": "https://source.example",
                                "contentWarning": "SAFE",
                                "displayName": "Example Source",
                                "homeUrl": "https://source.example",
                                "iconUrl": "/api/v1/source/icon/1",
                                "id": "1",
                                "isConfigurable": true,
                                "isNsfw": false,
                                "lang": "en",
                                "name": "Example Source",
                                "supportsLatest": true
                              }
                            ]
                          },
                          "storeIndexUrl": "https://store.example/index.min.json",
                          "versionCodeLong": "922337203685477580",
                          "versionName": "2.0.0",
                          "extensionStore": {
                            "name": "Example Store",
                            "badgeLabel": "EX",
                            "signingKey": "abc123",
                            "contactWebsite": "https://store.example",
                            "contactDiscord": null,
                            "indexUrl": "https://store.example/index.min.json",
                            "isLegacy": false,
                            "extensionListUrl": "https://store.example/extensions.json"
                          }
                        }
                      ],
                      "extensionStores": [
                        {
                          "name": "Example Store",
                          "badgeLabel": "EX",
                          "signingKey": "abc123",
                          "contactWebsite": "https://store.example",
                          "contactDiscord": "https://discord.gg/example",
                          "indexUrl": "https://store.example/index.min.json",
                          "isLegacy": false,
                          "extensionListUrl": "https://store.example/extensions.json"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val extension = response.data?.fetchExtensions?.extensions?.single()
        assertEquals("https://store.example/index.min.json", extension?.storeIndexUrl)
        assertNull(extension?.repo)
        assertEquals(0, extension?.versionCode)
        assertEquals("922337203685477580", extension?.versionCodeLong)
        assertEquals(922337203685477580, extension?.currentVersionCode())
        assertEquals(1.5, extension?.currentLibVersion())
        assertEquals(true, extension?.hasNsfwContent())
        assertEquals("MIXED", extension?.contentWarning)
        assertEquals("1.5", extension?.extensionLib)
        assertEquals("https://example.org/tachiyomi-en.example.apk", extension?.apkUrl)
        assertEquals("Example Store", extension?.extensionStore?.name)
        assertEquals("Example Source", extension?.sourceNodes()?.single()?.displayName)
        assertEquals("https://source.example", extension?.sourceNodes()?.single()?.webUrl())

        val store = response.data?.fetchExtensions?.extensionStores?.single()
        assertEquals("EX", store?.badgeLabel)
        assertEquals("https://store.example/index.min.json", store?.indexUrl)
        assertEquals("https://store.example/extensions.json", store?.extensionListUrl)
    }

    @Test
    fun `extension response tolerates null source nodes for sparse server payloads`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(FetchExtensionsData.serializer()),
            """
                {
                  "data": {
                    "fetchExtensions": {
                      "extensions": [
                        {
                          "apkName": null,
                          "apkUrl": null,
                          "contentWarning": null,
                          "extensionLib": null,
                          "extensionStore": null,
                          "hasUpdate": false,
                          "iconUrl": null,
                          "isInstalled": true,
                          "isNsfw": false,
                          "isObsolete": false,
                          "lang": "all",
                          "name": "Sparse Extension",
                          "pkgName": "pkg.sparse",
                          "repo": null,
                          "source": null,
                          "storeIndexUrl": null,
                          "versionCode": 0,
                          "versionCodeLong": null,
                          "versionName": ""
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val extension = response.data?.fetchExtensions?.extensions?.single()
        assertEquals("Sparse Extension", extension?.name)
        assertEquals(emptyList<SuwayomiSourceDto>(), extension?.sourceNodes())
        assertNull(extension?.extensionStore)
    }

    @Test
    fun `extension store query and mutation payloads decode current fields`() {
        val storesResponse = json.decodeFromString(
            GraphQlResponse.serializer(ExtensionStoresData.serializer()),
            """
                {
                  "data": {
                    "extensionStores": {
                      "nodes": [
                        {
                          "name": "Example Store",
                          "badgeLabel": "EX",
                          "signingKey": "abc123",
                          "contactWebsite": "https://store.example",
                          "contactDiscord": "https://discord.gg/example",
                          "indexUrl": "https://store.example/index.min.json",
                          "isLegacy": false,
                          "extensionListUrl": "https://store.example/extensions.json"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )
        val addResponse = json.decodeFromString(
            GraphQlResponse.serializer(AddExtensionStoreData.serializer()),
            """
                {
                  "data": {
                    "addExtensionStore": {
                      "extensionStore": {
                        "name": "Example Store",
                        "indexUrl": "https://store.example/index.min.json"
                      }
                    }
                  }
                }
            """.trimIndent(),
        )
        val removeResponse = json.decodeFromString(
            GraphQlResponse.serializer(RemoveExtensionStoreData.serializer()),
            """
                {
                  "data": {
                    "removeExtensionStore": {
                      "extensionStore": null
                    }
                  }
                }
            """.trimIndent(),
        )

        assertEquals("Example Store", storesResponse.data?.extensionStores?.nodes?.single()?.name)
        assertEquals(
            "https://store.example/index.min.json",
            addResponse.data?.addExtensionStore?.extensionStore?.indexUrl,
        )
        assertNull(removeResponse.data?.removeExtensionStore?.extensionStore)
    }

    @Test
    fun `local source response decodes sparse wrapper payloads`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(SourceListData.serializer()),
            """
                {
                  "data": {
                    "sources": {
                      "nodes": [
                        {
                          "displayName": "Local source",
                          "id": "0",
                          "lang": "localsourcelang",
                          "name": "LocalSource"
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val source = response.data?.sources?.nodes?.single()
        assertEquals("Local source", source?.displayName)
        assertEquals(false, source?.supportsLatest)
        assertEquals(true, source?.isLocalFolderSource())
        assertEquals(false, source?.hasNsfwContent())
        assertNull(source?.webUrl())
    }

    @Test
    fun `source response decodes current source fields with compatibility fallbacks`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(SourceListData.serializer()),
            """
                {
                  "data": {
                    "sources": {
                      "nodes": [
                        {
                          "contentWarning": "SAFE",
                          "displayName": "Example Source",
                          "homeUrl": "https://source.example",
                          "iconUrl": "/api/v1/source/icon/1",
                          "id": "1",
                          "isConfigurable": true,
                          "lang": "en",
                          "name": "Example",
                          "supportsLatest": true
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val source = response.data?.sources?.nodes?.single()
        assertEquals("SAFE", source?.contentWarning)
        assertEquals("https://source.example", source?.homeUrl)
        assertEquals("https://source.example", source?.webUrl())
        assertEquals(false, source?.hasNsfwContent())
        assertNull(source?.baseUrl)
        assertEquals(false, source?.isNsfw)
    }

    @Test
    fun `current content warning semantics fall back for older servers`() {
        val safeCurrentSource = SuwayomiSourceDto(
            contentWarning = "SAFE",
            displayName = "Current",
            homeUrl = "https://current.example",
            id = "1",
            isNsfw = true,
            lang = "en",
            name = "Current",
            supportsLatest = true,
        )
        val mixedCurrentSource = safeCurrentSource.copy(contentWarning = "MIXED", isNsfw = false)
        val unknownCurrentSource = safeCurrentSource.copy(contentWarning = "UNKNOWN", isNsfw = false)
        val unspecifiedCurrentSource = safeCurrentSource.copy(
            contentWarning = "CONTENT_WARNING_UNSPECIFIED",
            isNsfw = false,
        )
        val legacySource = safeCurrentSource.copy(
            contentWarning = null,
            homeUrl = null,
            baseUrl = "https://legacy.example",
        )
        val safeCurrentExtension = SuwayomiExtensionDto(
            contentWarning = "SAFE",
            extensionLib = "1.4",
            isNsfw = true,
            lang = "en",
            name = "Current",
            pkgName = "pkg.current",
            versionName = "1.0",
        )
        val legacyExtension = safeCurrentExtension.copy(contentWarning = null, isNsfw = true)

        assertEquals(false, safeCurrentSource.hasNsfwContent())
        assertEquals(true, mixedCurrentSource.hasNsfwContent())
        assertEquals(true, unknownCurrentSource.hasNsfwContent())
        assertEquals(false, unspecifiedCurrentSource.hasNsfwContent())
        assertEquals(true, legacySource.hasNsfwContent())
        assertEquals("https://legacy.example", legacySource.webUrl())
        assertEquals(false, safeCurrentExtension.hasNsfwContent())
        assertEquals(true, legacyExtension.hasNsfwContent())
        assertEquals(1.4, safeCurrentExtension.currentLibVersion())
    }

    @Test
    fun `sync status response decodes terminal error state`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(LastSyncStatusData.serializer()),
            """
                {
                  "data": {
                    "lastSyncStatus": {
                      "state": "ERROR",
                      "startDate": "1783261200000",
                      "endDate": "1783261260000",
                      "backupRestoreId": "restore-1",
                      "errorMessage": "SyncYomi is disabled"
                    }
                  }
                }
            """.trimIndent(),
        )

        val status = response.data?.lastSyncStatus
        assertEquals("ERROR", status?.state)
        assertEquals("1783261200000", status?.startDate)
        assertEquals("1783261260000", status?.endDate)
        assertEquals("restore-1", status?.backupRestoreId)
        assertEquals("SyncYomi is disabled", status?.errorMessage)
    }

    @Test
    fun `download status treats isDownloaded as tolerant server reported state`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(DownloadStatusData.serializer()),
            """
                {
                  "data": {
                    "downloadStatus": {
                      "state": "STOPPED",
                      "queue": [
                        {
                          "chapter": {
                            "chapterNumber": 1.0,
                            "id": 200,
                            "isDownloaded": true,
                            "name": "Chapter 1",
                            "sourceOrder": 1,
                            "uploadDate": 1783261200000,
                            "serverPathExists": false,
                            "downloadedPath": "/old/source/name.cbz"
                          },
                          "manga": {
                            "downloadCount": 1,
                            "id": 100,
                            "title": "Renamed Manga",
                            "thumbnailUrl": "/api/v1/manga/100/thumbnail",
                            "serverVersion": "2.3.2232"
                          },
                          "progress": 0.0,
                          "state": "ERROR",
                          "tries": 3,
                          "position": 0,
                          "cbzModeChanged": true
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val download = response.data?.downloadStatus?.queue?.single()
        assertEquals("ERROR", download?.state)
        assertEquals(true, download?.chapter?.isDownloaded)
        assertEquals("Renamed Manga", download?.manga?.title)
        assertEquals(3, download?.tries)
    }

    @Test
    fun `start sync payload decodes result enum`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(StartSyncData.serializer()),
            """
                {
                  "data": {
                    "startSync": {
                      "result": "STARTED"
                    }
                  }
                }
            """.trimIndent(),
        )

        assertEquals("STARTED", response.data?.startSync?.result)
    }

    @Test
    fun `bind existing track record payload decodes track record`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(BindTrackRecordData.serializer()),
            """
                {
                  "data": {
                    "bindTrackRecord": {
                      "trackRecord": {
                        "id": 42,
                        "mangaId": 100,
                        "trackerId": 1,
                        "remoteId": "1234567890",
                        "libraryId": "9876543210",
                        "title": "Tracked Manga",
                        "lastChapterRead": 12.0,
                        "totalChapters": 24,
                        "status": 1,
                        "score": 8.5,
                        "displayScore": "8.5",
                        "remoteUrl": "https://tracker.example/manga/1234567890",
                        "startDate": "1783261200000",
                        "finishDate": "0",
                        "private": true,
                        "tracker": {
                          "id": 1,
                          "name": "Example Tracker",
                          "isLoggedIn": true,
                          "supportsTrackDeletion": true,
                          "supportsReadingDates": true,
                          "supportsPrivateTracking": true,
                          "statuses": [
                            { "value": 1, "name": "Reading" }
                          ],
                          "scores": ["0", "1", "2"]
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
        )

        val record = response.data?.bindTrackRecord?.trackRecord
        assertEquals(42, record?.id)
        assertEquals(100, record?.mangaId)
        assertEquals("1234567890", record?.remoteId)
        assertEquals("Example Tracker", record?.tracker?.name)
        assertEquals(true, record?.private)
    }

    @Test
    fun `track records tolerate unsupported tracker payloads`() {
        val response = json.decodeFromString(
            GraphQlResponse.serializer(TrackRecordsData.serializer()),
            """
                {
                  "data": {
                    "trackRecords": {
                      "nodes": [
                        {
                          "id": 42,
                          "mangaId": 100,
                          "trackerId": 99,
                          "remoteId": "unsupported-remote",
                          "title": "Restored Unsupported Tracker",
                          "lastChapterRead": 3.0,
                          "totalChapters": 12,
                          "status": 1,
                          "score": 0.0,
                          "remoteUrl": "https://tracker.example/manga/unsupported",
                          "tracker": null
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
        )

        val record = response.data?.trackRecords?.nodes?.single()
        assertEquals(99, record?.trackerId)
        assertEquals("unsupported-remote", record?.remoteId)
        assertNull(record?.tracker)
    }

    private fun suwayomiManga(
        genre: List<String>,
    ): SuwayomiMangaDto {
        return SuwayomiMangaDto(
            genre = genre,
            id = 1,
            sourceId = "1",
            title = "Title",
            url = "/manga/1",
        )
    }
}
