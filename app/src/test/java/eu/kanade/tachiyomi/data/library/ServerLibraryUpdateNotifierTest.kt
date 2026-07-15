package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerLibraryUpdateNotifierTest {

    @Test
    fun `initialization gate permits exactly one notifier owner`() {
        val initialization = OnceOnlyInitialization()

        assertTrue(initialization.tryAcquire())
        assertFalse(initialization.tryAcquire())
    }
}
