package eu.kanade.tachiyomi.data.suwayomi

import java.net.URI

internal const val SUWAYOMI_PORT_MIN = 0
internal const val SUWAYOMI_PORT_MAX = 65535
internal const val SUWAYOMI_TIMEOUT_MIN_SECONDS = 5
internal const val SUWAYOMI_TIMEOUT_MAX_SECONDS = 120

internal fun isValidSuwayomiServerUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim().trimEnd('/')) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}

internal fun isValidSuwayomiServerPort(value: String): Boolean {
    return value.trim().toIntOrNull() in SUWAYOMI_PORT_MIN..SUWAYOMI_PORT_MAX
}

internal fun isValidSuwayomiAuthType(value: String): Boolean {
    return value == SuwayomiPreferences.AUTH_NONE ||
        value == SuwayomiPreferences.AUTH_BASIC ||
        value == SuwayomiPreferences.AUTH_TOKEN
}

internal fun isValidSuwayomiTimeoutSeconds(value: Int): Boolean {
    return value in SUWAYOMI_TIMEOUT_MIN_SECONDS..SUWAYOMI_TIMEOUT_MAX_SECONDS
}

internal fun isValidSuwayomiConnectionSettings(
    serverUrl: String,
    useServerPort: Boolean,
    serverPort: String,
    authType: String,
    timeoutSeconds: Int,
): Boolean {
    return isValidSuwayomiServerUrl(serverUrl) &&
        (!useServerPort || isValidSuwayomiServerPort(serverPort)) &&
        isValidSuwayomiAuthType(authType) &&
        isValidSuwayomiTimeoutSeconds(timeoutSeconds)
}

internal fun SuwayomiConnectionCheck.successMessage(): String {
    return "Connected to $endpoint.\nLoaded $sourceCount server sources.\nServer port: $serverPort."
}

internal fun Throwable.userMessage(): String {
    return buildString {
        append(this@userMessage::class.simpleName ?: "Error")
        message?.takeIf { it.isNotBlank() }?.let {
            append(": ")
            append(it)
        }
        cause?.message?.takeIf { it.isNotBlank() && it != message }?.let {
            append("\nCause: ")
            append(it)
        }
    }
}
