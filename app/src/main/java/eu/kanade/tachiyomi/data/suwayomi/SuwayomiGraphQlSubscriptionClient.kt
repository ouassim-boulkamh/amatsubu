package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class SuwayomiGraphQlSubscriptionClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoint: () -> String,
) {
    fun downloadStatusChanged(maxUpdates: Int = 150): Flow<SuwayomiDownloadUpdatesDto> {
        val query = """
            subscription DownloadStatusChanged(${'$'}input: DownloadChangedInput!) {
              downloadStatusChanged(input: ${'$'}input) {
                state
                omittedUpdates
                updates {
                  type
                  download {
                    ...AmatsubuDownload
                  }
                }
                initial {
                  ...AmatsubuDownload
                }
              }
            }

            $DOWNLOAD_FRAGMENT
        """.trimIndent()
        return subscribe(
            operationName = "DownloadStatusChanged",
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("maxUpdates", maxUpdates)
                }
            },
            deserializer = GraphQlResponse.serializer(DownloadStatusChangedData.serializer()),
        ) { it.downloadStatusChanged }
    }

    fun libraryUpdateStatusChanged(maxUpdates: Int = 150): Flow<SuwayomiLibraryUpdateUpdatesDto> {
        val query = """
            subscription LibraryUpdateStatusChanged(${'$'}input: LibraryUpdateStatusChangedInput!) {
              libraryUpdateStatusChanged(input: ${'$'}input) {
                ...AmatsubuLibraryUpdateUpdates
              }
            }

            $LIBRARY_UPDATE_UPDATES_FRAGMENT
            $LIBRARY_UPDATE_STATUS_FRAGMENT
            $CATEGORY_FRAGMENT
            $MANGA_FRAGMENT
        """.trimIndent()
        return subscribe(
            operationName = "LibraryUpdateStatusChanged",
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("maxUpdates", maxUpdates)
                }
            },
            deserializer = GraphQlResponse.serializer(LibraryUpdateStatusChangedData.serializer()),
        ) { it.libraryUpdateStatusChanged }
    }

    fun syncStatusChanged(): Flow<SuwayomiSyncStatusDto> {
        val query = """
            subscription SyncStatusChanged {
              syncStatusChanged {
                ...AmatsubuSyncStatus
              }
            }

            $SYNC_STATUS_FRAGMENT
        """.trimIndent()
        return subscribe(
            operationName = "SyncStatusChanged",
            query = query,
            deserializer = GraphQlResponse.serializer(SyncStatusChangedData.serializer()),
        ) { it.syncStatusChanged }
    }

    private fun <T, R> subscribe(
        operationName: String,
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        deserializer: DeserializationStrategy<GraphQlResponse<T>>,
        mapper: (T) -> R,
    ): Flow<R> = callbackFlow {
        val operationId = operationName
        val request = Request.Builder()
            .url(toSuwayomiWebSocketUrl(endpoint()))
            .build()

        val subscribeMessage = GraphQlWebSocketMessage(
            type = "subscribe",
            id = operationId,
            payload = buildJsonObject {
                put("operationName", operationName)
                put("query", query)
                put("variables", variables)
            },
        )
        var webSocket: WebSocket? = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(json.encodeToString(GraphQlWebSocketMessage.serializer(), GraphQlWebSocketMessage("connection_init")))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching {
                    json.decodeFromString(GraphQlWebSocketMessage.serializer(), text)
                }.getOrElse { error ->
                    close(error)
                    return
                }

                when (message.type) {
                    "connection_ack" -> {
                        webSocket.send(json.encodeToString(GraphQlWebSocketMessage.serializer(), subscribeMessage))
                    }
                    "next" -> {
                        val payload = message.payload ?: return
                        val response = runCatching {
                            json.decodeFromJsonElement(deserializer, payload)
                        }.getOrElse { error ->
                            close(error)
                            return
                        }
                        if (response.errors.isNotEmpty()) {
                            close(IllegalStateException(response.errors.joinToString("; ") { it.message }))
                            return
                        }
                        val data = response.data ?: return
                        trySend(mapper(data))
                    }
                    "error" -> close(IllegalStateException(message.payload?.toString() ?: "Suwayomi subscription failed"))
                    "complete" -> close()
                    "ping" -> webSocket.send(json.encodeToString(GraphQlWebSocketMessage.serializer(), GraphQlWebSocketMessage("pong")))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }
        webSocket = client.newWebSocket(request, listener)
        awaitClose {
            val socket = webSocket
            socket.send(json.encodeToString(GraphQlWebSocketMessage.serializer(), GraphQlWebSocketMessage("complete", operationId)))
            socket.close(1000, "Closing Suwayomi subscription")
        }
    }

    private companion object {
        private val CATEGORY_FRAGMENT = """
            fragment AmatsubuCategory on CategoryType {
              id
              name
              order
              includeInDownload
              includeInUpdate
            }
        """.trimIndent()

        private val MANGA_FRAGMENT = """
            fragment AmatsubuManga on MangaType {
              artist
              author
              description
              downloadCount
              genre
              id
              inLibrary
              inLibraryAt
              initialized
              latestFetchedChapter {
                fetchedAt
              }
              latestReadChapter {
                lastReadAt
              }
              latestUploadedChapter {
                uploadDate
              }
              meta {
                key
                mangaId
                value
              }
              sourceId
              status
              thumbnailUrl
              title
              unreadCount
              updateStrategy
              url
            }
        """.trimIndent()

        private val DOWNLOAD_FRAGMENT = """
            fragment AmatsubuDownload on DownloadType {
              chapter {
                id
                name
                chapterNumber
                uploadDate
                sourceOrder
                isDownloaded
              }
              manga {
                id
                title
                downloadCount
                thumbnailUrl
              }
              progress
              state
              tries
              position
            }
        """.trimIndent()

        private val LIBRARY_UPDATE_STATUS_FRAGMENT = """
            fragment AmatsubuLibraryUpdateStatus on LibraryUpdateStatus {
              categoryUpdates {
                category {
                  ...AmatsubuCategory
                }
                status
              }
              mangaUpdates {
                manga {
                  ...AmatsubuManga
                }
                status
              }
              jobsInfo {
                isRunning
                totalJobs
                finishedJobs
                skippedCategoriesCount
                skippedMangasCount
              }
            }
        """.trimIndent()

        private val LIBRARY_UPDATE_UPDATES_FRAGMENT = """
            fragment AmatsubuLibraryUpdateUpdates on UpdaterUpdates {
              categoryUpdates {
                category {
                  ...AmatsubuCategory
                }
                status
              }
              mangaUpdates {
                manga {
                  ...AmatsubuManga
                }
                status
              }
              initial {
                ...AmatsubuLibraryUpdateStatus
              }
              jobsInfo {
                isRunning
                totalJobs
                finishedJobs
                skippedCategoriesCount
                skippedMangasCount
              }
              omittedUpdates
            }
        """.trimIndent()

        private val SYNC_STATUS_FRAGMENT = """
            fragment AmatsubuSyncStatus on SyncStatus {
              state
              startDate
              endDate
              backupRestoreId
              errorMessage
            }
        """.trimIndent()
    }
}

internal fun toSuwayomiWebSocketUrl(endpoint: String): String {
    return when {
        endpoint.startsWith("https://", ignoreCase = true) -> "wss://${endpoint.drop(8)}"
        endpoint.startsWith("http://", ignoreCase = true) -> "ws://${endpoint.drop(7)}"
        else -> endpoint
    }
}

@Serializable
private data class GraphQlWebSocketMessage(
    val type: String,
    val id: String? = null,
    val payload: JsonElement? = null,
)
