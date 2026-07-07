package eu.kanade.tachiyomi.data.suwayomi

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class SuwayomiPreferences(
    preferenceStore: PreferenceStore,
) {
    val serverUrl = preferenceStore.getString("amatsubu_server_url", "http://127.0.0.1:4567")
    val useServerPort = preferenceStore.getBoolean("amatsubu_server_port_enabled", false)
    val serverPort = preferenceStore.getString("amatsubu_server_port", "4567")
    val authType = preferenceStore.getString("amatsubu_server_auth_type", AUTH_NONE)
    val username = preferenceStore.getString("amatsubu_server_username", "")
    val password = preferenceStore.getString("amatsubu_server_password", "")
    val timeoutSeconds = preferenceStore.getInt("amatsubu_server_timeout_seconds", 30)

    init {
        migrateLegacyPreference(preferenceStore.getString(legacyKey("server_url"), "http://127.0.0.1:4567"), serverUrl)
        migrateLegacyPreference(preferenceStore.getBoolean(legacyKey("server_port_enabled"), false), useServerPort)
        migrateLegacyPreference(preferenceStore.getString(legacyKey("server_port"), "4567"), serverPort)
        migrateLegacyPreference(preferenceStore.getString(legacyKey("server_auth_type"), AUTH_NONE), authType)
        migrateLegacyPreference(preferenceStore.getString(legacyKey("server_username"), ""), username)
        migrateLegacyPreference(preferenceStore.getString(legacyKey("server_password"), ""), password)
        migrateLegacyPreference(preferenceStore.getInt(legacyKey("server_timeout_seconds"), 30), timeoutSeconds)
    }

    fun baseUrl(): String {
        val rawUrl = serverUrl.get().trim().trimEnd('/')
        val uri = runCatching { URI(rawUrl) }.getOrNull()
        val scheme = uri?.scheme.orEmpty()
        val host = uri?.host.orEmpty()
        if (rawUrl.isBlank() || scheme.isBlank() || host.isBlank()) {
            throw SuwayomiServerUnavailableException("Suwayomi server URL is not configured")
        }
        val hasExplicitPort = uri?.port != null && uri.port >= 0
        val port = serverPort.get().trim()
        val shouldAppendPort = useServerPort.get() &&
            port.isNotBlank() &&
            !hasExplicitPort &&
            host.isLoopbackHost()

        return if (shouldAppendPort) "$rawUrl:$port" else rawUrl
    }

    fun graphQlEndpoint(): String = "${baseUrl()}/api/graphql"

    fun httpClient(baseClient: OkHttpClient): OkHttpClient {
        val timeout = timeoutSeconds.get().coerceAtLeast(1).toLong()
        return baseClient.newBuilder()
            .apply {
                connectTimeout(timeout, TimeUnit.SECONDS)
                readTimeout(timeout, TimeUnit.SECONDS)
                writeTimeout(timeout, TimeUnit.SECONDS)
                if (authType.get() == AUTH_BASIC) {
                    addInterceptor { chain ->
                        val configuredBaseUrl = runCatching { baseUrl() }.getOrNull()
                        val request = if (
                            configuredBaseUrl != null &&
                            shouldAuthorizeSuwayomiRequest(configuredBaseUrl, chain.request().url)
                        ) {
                            val credentials = Credentials.basic(username.get(), password.get())
                            chain.request().newBuilder()
                                .header("Authorization", credentials)
                                .build()
                        } else {
                            chain.request()
                        }
                        chain.proceed(request)
                    }
                }
            }
            .build()
    }

    companion object {
        const val AUTH_NONE = "none"
        const val AUTH_BASIC = "basic"
    }
}

internal fun shouldAuthorizeSuwayomiRequest(baseUrl: String, requestUrl: HttpUrl): Boolean {
    val serverUrl = baseUrl.toHttpUrlOrNull() ?: return false
    if (!requestUrl.scheme.isEquivalentSuwayomiScheme(serverUrl.scheme)) return false
    if (!requestUrl.host.equals(serverUrl.host, ignoreCase = true)) return false
    if (requestUrl.port != serverUrl.port) return false

    val serverPath = serverUrl.encodedPath.trimEnd('/')
    val requestPath = requestUrl.encodedPath.trimEnd('/')
    return serverPath.isEmpty() ||
        requestPath == serverPath ||
        requestPath.startsWith("$serverPath/")
}

class SuwayomiServerUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

fun Throwable.isSuwayomiServerUnavailable(): Boolean {
    return this is SuwayomiServerUnavailableException ||
        this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        this is IOException ||
        cause?.isSuwayomiServerUnavailable() == true
}

private fun legacyKey(suffix: String): String = "sorami" + "hon_$suffix"

private fun <T> migrateLegacyPreference(
    oldPreference: Preference<T>,
    newPreference: Preference<T>,
) {
    if (!newPreference.isSet() && oldPreference.isSet()) {
        newPreference.set(oldPreference.get())
    }
}

private fun String.isLoopbackHost(): Boolean {
    return equals("localhost", ignoreCase = true) ||
        this == "127.0.0.1" ||
        this == "0.0.0.0" ||
        this == "::1"
}

private fun String.isEquivalentSuwayomiScheme(other: String): Boolean {
    return equals(other, ignoreCase = true) ||
        (equals("ws", ignoreCase = true) && other.equals("http", ignoreCase = true)) ||
        (equals("wss", ignoreCase = true) && other.equals("https", ignoreCase = true))
}
