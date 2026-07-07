package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SuwayomiOfflineStateTest {

    @Test
    fun `oldest positive timestamp ignores null and zero values`() {
        assertEquals(10L, 10L.oldestPositive(null))
        assertEquals(20L, 0L.oldestPositive(20L))
        assertEquals(15L, 30L.oldestPositive(15L))
        assertEquals(null, 0L.oldestPositive(null))
    }

    @Test
    fun `server-owned actions are disabled while offline`() {
        SuwayomiServerAction.entries.forEach { action ->
            assertFalse(action.isAllowedWhenOffline(), "$action should be disabled offline")
        }
    }
}
