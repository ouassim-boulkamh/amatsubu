package eu.kanade.tachiyomi.data.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerNotificationReconcilerTest {

    @Test
    fun `suwayomi epoch timestamps are normalized to milliseconds`() {
        assertEquals(1_783_466_771_000L, "1783466771".toSuwayomiEpochMillis())
        assertEquals(1_783_466_771_000L, "1783466771000".toSuwayomiEpochMillis())
        assertEquals(0L, "not-a-timestamp".toSuwayomiEpochMillis())
    }

    @Test
    fun `recent chapters allow client-observed library update start skew`() {
        val observedStart = 1_783_467_003_844L

        assertEquals(true, "1783467003".wasFetchedDuringObservedLibraryUpdate(observedStart))
        assertEquals(true, "1783467003844".wasFetchedDuringObservedLibraryUpdate(observedStart))
        assertEquals(false, "1783466943".wasFetchedDuringObservedLibraryUpdate(observedStart))
    }
}
