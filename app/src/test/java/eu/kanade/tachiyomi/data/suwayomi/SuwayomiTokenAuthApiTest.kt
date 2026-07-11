package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SuwayomiTokenAuthApiTest {

    @Test
    fun `login stores tokens under the current server key`() = runTest {
        val store = FakeTokenStore()
        val api = SuwayomiTokenAuthApi(
            operations = FakeOperations(loginTokens = SuwayomiTokens("access", "refresh")),
            tokenStore = store,
            serverKey = { "server-a" },
        )

        assertEquals(SuwayomiTokens("access", "refresh"), api.login("alice", "secret"))
        assertEquals(SuwayomiTokens("access", "refresh"), store.read("server-a"))
        assertNull(store.read("server-b"))
    }

    @Test
    fun `refresh failure clears only the current server token`() = runTest {
        val store = FakeTokenStore().apply {
            write("server-a", SuwayomiTokens("old-access", "refresh-a"))
            write("server-b", SuwayomiTokens("other-access", "refresh-b"))
        }
        val api = SuwayomiTokenAuthApi(
            operations = FakeOperations(refreshFailure = IllegalStateException("expired")),
            tokenStore = store,
            serverKey = { "server-a" },
        )

        assertNull(api.refresh())
        assertNull(store.read("server-a"))
        assertEquals(SuwayomiTokens("other-access", "refresh-b"), store.read("server-b"))
    }

    @Test
    fun `refresh replaces only the current server access token`() = runTest {
        val store = FakeTokenStore().apply {
            write("server-a", SuwayomiTokens("old-access", "refresh-a"))
            write("server-b", SuwayomiTokens("other-access", "refresh-b"))
        }
        val api = SuwayomiTokenAuthApi(
            operations = FakeOperations(),
            tokenStore = store,
            serverKey = { "server-a" },
        )

        assertEquals("new-access", api.refresh())
        assertEquals(SuwayomiTokens("new-access", "refresh-a"), store.read("server-a"))
        assertEquals(SuwayomiTokens("other-access", "refresh-b"), store.read("server-b"))
    }

    private class FakeTokenStore : SuwayomiTokenStore {
        private val tokens = mutableMapOf<String, SuwayomiTokens>()
        override fun read(serverKey: String) = tokens[serverKey]
        override fun write(serverKey: String, tokens: SuwayomiTokens) {
            this.tokens[serverKey] = tokens
        }
        override fun clear(serverKey: String) {
            tokens.remove(serverKey)
        }
    }

    private class FakeOperations(
        private val loginTokens: SuwayomiTokens = SuwayomiTokens("access", "refresh"),
        private val refreshFailure: Throwable? = null,
    ) : SuwayomiTokenOperations {
        override suspend fun login(username: String, password: String) = loginTokens
        override suspend fun refreshToken(refreshToken: String): String {
            refreshFailure?.let { throw it }
            return "new-access"
        }
    }
}
