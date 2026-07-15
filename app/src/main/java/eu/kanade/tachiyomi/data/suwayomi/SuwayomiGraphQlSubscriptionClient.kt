package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.DownloadStatusChangedSubscription
import eu.kanade.tachiyomi.data.suwayomi.generated.LibraryUpdateStatusChangedSubscription
import eu.kanade.tachiyomi.data.suwayomi.generated.SyncStatusChangedSubscription
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DownloadChangedInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.LibraryUpdateStatusChangedInput
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
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import tachiyomi.core.common.util.system.logcat
import java.util.UUID

internal class SuwayomiGraphQlSubscriptionClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoint: () -> String,
) {
    fun downloadStatusChanged(maxUpdates: Int = 150): Flow<SuwayomiDownloadUpdatesDto> {
        val operation =
            DownloadStatusChangedSubscription(DownloadChangedInput(maxUpdates = Optional.present(maxUpdates)))
        return subscribe(
            operationName = operation.name(),
            query = operation.document(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("maxUpdates", maxUpdates)
                }
            },
            deserializer = GraphQlResponse.serializer(DownloadStatusChangedData.serializer()),
        ) { it.downloadStatusChanged }
    }

    fun libraryUpdateStatusChanged(maxUpdates: Int = 150): Flow<SuwayomiLibraryUpdateUpdatesDto> {
        val operation = LibraryUpdateStatusChangedSubscription(
            LibraryUpdateStatusChangedInput(maxUpdates = Optional.present(maxUpdates)),
        )
        return subscribe(
            operationName = operation.name(),
            query = operation.document(),
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("maxUpdates", maxUpdates)
                }
            },
            deserializer = GraphQlResponse.serializer(LibraryUpdateStatusChangedData.serializer()),
        ) { it.libraryUpdateStatusChanged }
    }

    fun syncStatusChanged(): Flow<SuwayomiSyncStatusDto> {
        val operation = SyncStatusChangedSubscription()
        return subscribe(
            operationName = operation.name(),
            query = operation.document(),
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
        // Suwayomi keys active websocket operations by ID for the authenticated
        // session, so separate UI and app-scoped consumers must not reuse the
        // GraphQL operation name as the protocol ID.
        val operationId = newSuwayomiSubscriptionOperationId(operationName)
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
                logcat { "Opening Suwayomi websocket subscription operation=$operationName" }
                webSocket.send(
                    json.encodeToString(
                        GraphQlWebSocketMessage.serializer(),
                        GraphQlWebSocketMessage("connection_init"),
                    ),
                )
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
                        logcat { "Connected Suwayomi websocket subscription operation=$operationName" }
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
                        if (trySend(mapper(data)).isFailure) {
                            logcat {
                                "Ignored Suwayomi websocket update after subscription closed operation=$operationName"
                            }
                        }
                    }
                    "error" -> close(
                        IllegalStateException(message.payload?.toString() ?: "Suwayomi subscription failed"),
                    )
                    "complete" -> close()
                    "ping" -> webSocket.send(
                        json.encodeToString(GraphQlWebSocketMessage.serializer(), GraphQlWebSocketMessage("pong")),
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logcat(LogPriority.WARN, t) { "Suwayomi websocket subscription failed operation=$operationName" }
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logcat { "Suwayomi websocket subscription closed operation=$operationName code=$code" }
                close()
            }
        }
        webSocket = client.newWebSocket(request, listener)
        awaitClose {
            val socket = webSocket
            socket.send(
                json.encodeToString(
                    GraphQlWebSocketMessage.serializer(),
                    GraphQlWebSocketMessage("complete", operationId),
                ),
            )
            socket.close(1000, "Closing Suwayomi subscription")
        }
    }
}

internal fun newSuwayomiSubscriptionOperationId(operationName: String): String {
    return "$operationName-${UUID.randomUUID()}"
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
