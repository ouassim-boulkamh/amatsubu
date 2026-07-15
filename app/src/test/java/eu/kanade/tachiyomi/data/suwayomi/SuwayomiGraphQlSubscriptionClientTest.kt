package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuwayomiGraphQlSubscriptionClientTest {

    @Test
    fun `subscription protocol IDs are unique while retaining their operation name`() {
        val first = newSuwayomiSubscriptionOperationId("LibraryUpdateStatusChanged")
        val second = newSuwayomiSubscriptionOperationId("LibraryUpdateStatusChanged")

        assertTrue(first.startsWith("LibraryUpdateStatusChanged-"))
        assertTrue(second.startsWith("LibraryUpdateStatusChanged-"))
        assertNotEquals(first, second)
    }
}
