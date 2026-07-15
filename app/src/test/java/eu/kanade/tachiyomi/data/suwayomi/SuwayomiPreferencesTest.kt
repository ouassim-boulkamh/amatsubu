package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import java.util.concurrent.TimeUnit

class SuwayomiPreferencesTest {

    @Test
    fun `live server notifications default to enabled and can be disabled`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())

        assertEquals(true, preferences.liveServerNotifications.get())
        preferences.liveServerNotifications.set(false)
        assertFalse(preferences.liveServerNotifications.get())
    }

    @Test
    fun `live notification server address defaults to visible and uses host with port`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())

        assertEquals(true, preferences.showServerAddressInLiveNotification.get())
        assertEquals("127.0.0.1:4567", preferences.notificationServerAddress())
        preferences.serverUrl.set("https://192.168.1.100:1234/server")
        assertEquals("192.168.1.100:1234", preferences.notificationServerAddress())
    }

    @Test
    fun `base url uses configured default server`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())

        assertEquals("http://127.0.0.1:4567", preferences.baseUrl())
        assertEquals("http://127.0.0.1:4567/api/graphql", preferences.graphQlEndpoint())
    }

    @Test
    fun `base url preserves reverse proxy path and trims trailing slash`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi/")

        assertEquals("https://example.org/suwayomi", preferences.baseUrl())
        assertEquals("https://example.org/suwayomi/api/graphql", preferences.graphQlEndpoint())
    }

    @Test
    fun `base url does not append configured port to non loopback hosts`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("http://127.0.0.1:4567/suwayomi")
        preferences.useServerPort.set(true)
        preferences.serverPort.set("4567")

        assertEquals("http://127.0.0.1:4567/suwayomi", preferences.baseUrl())
    }

    @Test
    fun `base url does not append configured port when URL already has explicit port`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("http://127.0.0.1:8080/suwayomi")
        preferences.useServerPort.set(true)
        preferences.serverPort.set("4567")

        assertEquals("http://127.0.0.1:8080/suwayomi", preferences.baseUrl())
    }

    @Test
    fun `base url rejects blank server setting`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("")

        assertThrows(SuwayomiServerUnavailableException::class.java) {
            preferences.baseUrl()
        }
    }

    @Test
    fun `base url rejects server setting without host`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("http://")

        assertThrows(SuwayomiServerUnavailableException::class.java) {
            preferences.graphQlEndpoint()
        }
    }

    @Test
    fun `token login password is cleared without changing basic auth credentials`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.password.set("token-password")
        preferences.authType.set(SuwayomiPreferences.AUTH_TOKEN)

        preferences.clearTokenLoginPassword()

        assertEquals("", preferences.password.get())
        preferences.password.set("basic-password")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)

        preferences.clearTokenLoginPassword()

        assertEquals("basic-password", preferences.password.get())
    }

    @Test
    fun `token auth is applied only to the configured server`() {
        val serverKey = SuwayomiServerIdentity.fromBaseUrl("http://example.org/suwayomi").serverKey
        val tokenStore = FakeTokenStore().apply {
            write(serverKey, SuwayomiTokens("access-token", "refresh-token"))
        }
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore(), tokenStore)
        preferences.serverUrl.set("http://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_TOKEN)

        val sameServerHeader = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "http://example.org/suwayomi/api/graphql",
        )
        val externalHeader = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://cdn.example.net/cover.jpg",
        )

        assertEquals("Bearer access-token", sameServerHeader)
        assertNull(externalHeader)
    }

    @Test
    fun `basic auth is applied to configured suwayomi asset urls`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://example.org/suwayomi/api/v1/source/icon/1",
        )

        assertEquals(Credentials.basic("user", "pass"), header)
    }

    @Test
    fun `Batch E auth policy applies Basic credentials to GraphQL websocket backup and page requests`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")
        val expected = Credentials.basic("user", "pass")

        listOf(
            "https://example.org/suwayomi/api/graphql",
            "wss://example.org/suwayomi/api/graphql",
            "https://example.org/suwayomi/api/v1/backup/export/file",
            "https://example.org/suwayomi/api/v1/chapter/2/page/1",
        ).forEach { requestUrl ->
            val header = captureAuthorizationHeader(
                preferences = preferences,
                requestUrl = requestUrl,
            )

            assertEquals(expected, header, requestUrl)
        }
    }

    @Test
    fun `basic auth is not applied outside configured suwayomi base path`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://example.org/other/cover.jpg",
        )

        assertNull(header)
    }

    @Test
    fun `basic auth is not applied to sibling reverse proxy paths`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://example.org/suwayomi-other/api/v1/source/icon/1",
        )

        assertNull(header)
    }

    @Test
    fun `basic auth matches explicit configured non loopback port`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://proxy.example.org:8443/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://proxy.example.org:8443/suwayomi/api/v1/extension/icon/pkg",
        )

        assertEquals(Credentials.basic("user", "pass"), header)
    }

    @Test
    fun `basic auth does not match a different explicit port`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://proxy.example.org:8443/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://proxy.example.org:9443/suwayomi/api/v1/extension/icon/pkg",
        )

        assertNull(header)
    }

    @Test
    fun `basic auth is not applied to external asset urls`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("https://example.org/suwayomi")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://cdn.example.net/suwayomi/api/v1/source/icon/1",
        )

        assertNull(header)
    }

    @Test
    fun `malformed server setting does not break external image requests`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.serverUrl.set("http://")
        preferences.authType.set(SuwayomiPreferences.AUTH_BASIC)
        preferences.username.set("user")
        preferences.password.set("pass")

        val header = captureAuthorizationHeader(
            preferences = preferences,
            requestUrl = "https://cdn.example.net/cover.jpg",
        )

        assertNull(header)
    }

    @Test
    fun `server timeout setting applies to connect read and write timeouts`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.timeoutSeconds.set(12)

        val client = preferences.httpClient(
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build(),
        )

        assertEquals(TimeUnit.SECONDS.toMillis(12).toInt(), client.connectTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(12).toInt(), client.readTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(12).toInt(), client.writeTimeoutMillis)
    }

    @Test
    fun `server timeout setting is clamped to at least one second`() {
        val preferences = SuwayomiPreferences(InMemoryPreferenceStore())
        preferences.timeoutSeconds.set(0)

        val client = preferences.httpClient(OkHttpClient())

        assertEquals(TimeUnit.SECONDS.toMillis(1).toInt(), client.connectTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(1).toInt(), client.readTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(1).toInt(), client.writeTimeoutMillis)
    }

    @Test
    fun `connection settings validation accepts valid saved settings`() {
        assertEquals(
            true,
            isValidSuwayomiConnectionSettings(
                serverUrl = "http://127.0.0.1:4567/",
                useServerPort = false,
                serverPort = "4567",
                authType = SuwayomiPreferences.AUTH_NONE,
                timeoutSeconds = 30,
            ),
        )
    }

    @Test
    fun `connection settings validation rejects invalid server urls`() {
        assertFalse(isValidSuwayomiServerUrl(""))
        assertFalse(isValidSuwayomiServerUrl("http://"))
        assertFalse(isValidSuwayomiServerUrl("ftp://example.org"))
    }

    @Test
    fun `connection settings validation rejects invalid optional ports`() {
        assertEquals(
            true,
            isValidSuwayomiConnectionSettings(
                serverUrl = "http://127.0.0.1",
                useServerPort = false,
                serverPort = "not-a-port",
                authType = SuwayomiPreferences.AUTH_NONE,
                timeoutSeconds = 30,
            ),
        )
        assertFalse(
            isValidSuwayomiConnectionSettings(
                serverUrl = "http://127.0.0.1",
                useServerPort = true,
                serverPort = "65536",
                authType = SuwayomiPreferences.AUTH_NONE,
                timeoutSeconds = 30,
            ),
        )
    }

    @Test
    fun `connection settings validation rejects unsupported auth and timeout values`() {
        assertFalse(
            isValidSuwayomiConnectionSettings(
                serverUrl = "http://127.0.0.1:4567",
                useServerPort = false,
                serverPort = "4567",
                authType = "digest",
                timeoutSeconds = 30,
            ),
        )
        assertFalse(
            isValidSuwayomiConnectionSettings(
                serverUrl = "http://127.0.0.1:4567",
                useServerPort = false,
                serverPort = "4567",
                authType = SuwayomiPreferences.AUTH_NONE,
                timeoutSeconds = 4,
            ),
        )
    }

    @Test
    fun `websocket endpoint conversion preserves reverse proxy path`() {
        assertEquals(
            "wss://example.org/suwayomi/api/graphql",
            toSuwayomiWebSocketUrl("https://example.org/suwayomi/api/graphql"),
        )
    }

    @Test
    fun `websocket endpoint conversion handles uppercase schemes`() {
        assertEquals(
            "ws://example.org:4567/api/graphql",
            toSuwayomiWebSocketUrl("HTTP://example.org:4567/api/graphql"),
        )
    }

    @Test
    fun `connection test uses authenticated user relevant queries`() = runTest {
        val queries = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val query = chain.request().bodyString()
                queries += query
                val body = when {
                    "settings" in query -> """{"data":{"settings":{"port":4567}}}"""
                    "sources" in query -> """
                        {
                          "data": {
                            "sources": {
                              "nodes": [
                                {
                                  "baseUrl": "https://example.org",
                                  "contentWarning": "SAFE",
                                  "displayName": "Source",
                                  "homeUrl": "https://example.org",
                                  "iconUrl": "https://example.org/icon.png",
                                  "id": "1",
                                  "isConfigurable": false,
                                  "isNsfw": false,
                                  "lang": "en",
                                  "name": "Source",
                                  "supportsLatest": true
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent()
                    else -> error("Unexpected query: $query")
                }
                jsonResponse(chain.request(), body)
            }
            .build()
        val graphQlClient = SuwayomiGraphQlClient(
            client = client,
            endpoint = { "http://example.org/api/graphql" },
        )

        val result = graphQlClient.testConnection()

        assertEquals("http://example.org/api/graphql", result.endpoint)
        assertEquals(1, result.sourceCount)
        assertEquals(4567, result.serverPort)
        assertFalse(queries.any { "aboutServer" in it })
    }

    private fun captureAuthorizationHeader(
        preferences: SuwayomiPreferences,
        requestUrl: String,
    ): String? {
        var authorizationHeader: String? = null
        val client = preferences.httpClient(OkHttpClient())
            .newBuilder()
            .addInterceptor { chain ->
                authorizationHeader = chain.request().header("Authorization")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("OK".toResponseBody())
                    .build()
            }
            .build()

        client.newCall(Request.Builder().url(requestUrl).build()).execute().close()

        return authorizationHeader
    }

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun jsonResponse(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private class FakeTokenStore : SuwayomiTokenStore {
        private val tokens = mutableMapOf<String, SuwayomiTokens>()

        override fun read(serverKey: String): SuwayomiTokens? = tokens[serverKey]

        override fun write(serverKey: String, tokens: SuwayomiTokens) {
            this.tokens[serverKey] = tokens
        }

        override fun clear(serverKey: String) {
            tokens.remove(serverKey)
        }
    }
}
