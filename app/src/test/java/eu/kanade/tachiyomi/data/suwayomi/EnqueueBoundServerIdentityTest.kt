package eu.kanade.tachiyomi.data.suwayomi

import androidx.work.Data
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnqueueBoundServerIdentityTest {

    @Test
    fun `put stores enqueue-bound server key in worker input data`() {
        val data = EnqueueBoundServerIdentity.put(
            Data.Builder(),
            "http://server.test/api/graphql",
        ).build()

        assertEquals("http://server.test/api/graphql", EnqueueBoundServerIdentity.read(data))
    }

    @Test
    fun `blank or missing server key is treated as missing identity`() {
        assertNull(EnqueueBoundServerIdentity.read(Data.EMPTY))

        val blankData = EnqueueBoundServerIdentity.put(Data.Builder(), "").build()

        assertInstanceOf(
            EnqueueBoundServerIdentityCheck.Missing::class.java,
            EnqueueBoundServerIdentity.check(blankData, "http://current.test/api/graphql"),
        )
    }

    @Test
    fun `matching server key allows enqueue-bound work to continue`() {
        val data = EnqueueBoundServerIdentity.put(
            Data.Builder(),
            "http://server.test/api/graphql",
        ).build()

        val result = EnqueueBoundServerIdentity.check(data, "http://server.test/api/graphql")

        val matched = assertInstanceOf(EnqueueBoundServerIdentityCheck.Matched::class.java, result)
        assertEquals("http://server.test/api/graphql", matched.serverKey)
    }

    @Test
    fun `different server key blocks enqueue-bound work`() {
        val data = EnqueueBoundServerIdentity.put(
            Data.Builder(),
            "http://old.test/api/graphql",
        ).build()

        val result = EnqueueBoundServerIdentity.check(data, "http://new.test/api/graphql")

        val mismatched = assertInstanceOf(EnqueueBoundServerIdentityCheck.Mismatched::class.java, result)
        assertEquals("http://old.test/api/graphql", mismatched.enqueuedServerKey)
        assertEquals("http://new.test/api/graphql", mismatched.currentServerKey)
    }
}
