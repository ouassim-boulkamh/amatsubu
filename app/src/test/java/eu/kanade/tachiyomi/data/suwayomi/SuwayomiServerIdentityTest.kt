package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuwayomiServerIdentityTest {

    @Test
    fun `base URL identity trims trailing slashes and derives graphQL server key`() {
        val identity = SuwayomiServerIdentity.fromBaseUrl(" https://example.org/suwayomi/ ")

        assertEquals("https://example.org/suwayomi", identity.baseUrl)
        assertEquals("https://example.org/suwayomi/api/graphql", identity.serverKey)
        assertEquals("https://example.org/suwayomi", identity.notificationCheckpointKey)
    }

    @Test
    fun `graphQL endpoint identity maps back to base URL checkpoint key`() {
        val identity = SuwayomiServerIdentity.fromGraphQlEndpoint("https://example.org/suwayomi/api/graphql/")

        assertEquals("https://example.org/suwayomi", identity.baseUrl)
        assertEquals("https://example.org/suwayomi/api/graphql", identity.serverKey)
        assertEquals("https://example.org/suwayomi", identity.notificationCheckpointKey)
    }
}
