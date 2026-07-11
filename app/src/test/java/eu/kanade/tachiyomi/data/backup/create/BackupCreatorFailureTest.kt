package eu.kanade.tachiyomi.data.backup.create

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream

class BackupCreatorFailureTest {

    @Test
    fun `failed backup write deletes the partial target`() {
        val target = FakeBackupOutputFile(FailingAfterBytesOutputStream(maxBytesBeforeFailure = 8))

        assertThrows(IOException::class.java) {
            writeClientBackupFile(
                file = target,
                backupBytes = ByteArray(64) { it.toByte() },
            )
        }

        assertTrue(target.deleted)
        assertTrue(target.stream.bytesAccepted > 0)
    }

    @Test
    fun `failed backup validation deletes the written target`() {
        val target = FakeBackupOutputFile(
            stream = CountingOutputStream(),
            validateFailure = IllegalStateException("validation failed"),
        )

        assertThrows(IllegalStateException::class.java) {
            writeClientBackupFile(
                file = target,
                backupBytes = ByteArray(64) { it.toByte() },
            )
        }

        assertTrue(target.deleted)
        assertTrue(target.stream.bytesAccepted > 0)
    }

    @Test
    fun `successful backup write returns the target uri without cleanup`() {
        val target = FakeBackupOutputFile(CountingOutputStream())

        val location = writeClientBackupFile(
            file = target,
            backupBytes = ByteArray(64) { it.toByte() },
        )

        assertEquals("content://backup/test.tachibk", location)
        assertTrue(target.stream.bytesAccepted > 0)
        assertTrue(target.validated)
        assertEquals(false, target.deleted)
    }

    private class FakeBackupOutputFile(
        val stream: CountingOutputStream,
        private val validateFailure: RuntimeException? = null,
    ) : BackupOutputFile {
        override val location = "content://backup/test.tachibk"
        override val isFile = true
        var deleted = false
        var validated = false

        override fun openOutputStream(): OutputStream {
            return stream
        }

        override fun validate() {
            validated = true
            validateFailure?.let { throw it }
        }

        override fun delete(): Boolean {
            deleted = true
            return true
        }
    }

    private open class CountingOutputStream : OutputStream() {
        var bytesAccepted = 0
            private set

        override fun write(byte: Int) {
            bytesAccepted++
        }
    }

    private class FailingAfterBytesOutputStream(
        private val maxBytesBeforeFailure: Int,
    ) : CountingOutputStream() {
        override fun write(byte: Int) {
            if (bytesAccepted >= maxBytesBeforeFailure) {
                throw IOException("simulated storage write failure")
            }
            super.write(byte)
        }
    }
}
