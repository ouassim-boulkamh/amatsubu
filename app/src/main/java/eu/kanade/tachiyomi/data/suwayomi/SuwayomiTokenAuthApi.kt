package eu.kanade.tachiyomi.data.suwayomi

internal interface SuwayomiTokenOperations {
    suspend fun login(username: String, password: String): SuwayomiTokens
    suspend fun refreshToken(refreshToken: String): String
}

/** Generated login and refresh operations, with no generated type leaking from the boundary. */
internal class SuwayomiTokenAuthApi(
    private val operations: SuwayomiTokenOperations,
    private val tokenStore: SuwayomiTokenStore,
    private val serverKey: () -> String,
) {
    suspend fun login(username: String, password: String): SuwayomiTokens {
        val tokens = operations.login(username, password)
        tokenStore.write(serverKey(), tokens)
        return tokens
    }

    suspend fun refresh(): String? {
        val existing = tokenStore.read(serverKey()) ?: return null
        return runCatching {
            operations.refreshToken(existing.refreshToken).also { accessToken ->
                tokenStore.write(serverKey(), existing.copy(accessToken = accessToken))
            }
        }.getOrElse {
            tokenStore.clear(serverKey())
            null
        }
    }

    fun logout() = tokenStore.clear(serverKey())
}
