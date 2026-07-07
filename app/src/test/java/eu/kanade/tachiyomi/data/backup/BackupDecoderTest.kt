package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BackupDecoderTest {

    @Test
    fun `decodes gzip wrapped protobuf backup`() {
        val backup = Backup(backupManga = emptyList())
        val gzipBytes = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(BackupCreator.encodeBackup(backup))
            }
            output.toByteArray()
        }

        val decoded = BackupDecoder.decodeBackupBytes(gzipBytes)

        assertEquals(backup.backupManga, decoded.backupManga)
        assertEquals(backup.backupCategories, decoded.backupCategories)
        assertEquals(backup.backupPreferences, decoded.backupPreferences)
    }

    @Test
    fun `decodes raw protobuf backup`() {
        val backup = Backup(backupManga = emptyList())

        val decoded = BackupDecoder.decodeBackupBytes(BackupCreator.encodeBackup(backup))

        assertEquals(backup.backupManga, decoded.backupManga)
    }

    @Test
    fun `empty backup encodes to protobuf bytes that decode as an empty backup`() {
        val backupBytes = BackupCreator.encodeBackup(Backup(backupManga = emptyList()))

        val decoded = BackupDecoder.decodeBackupBytes(backupBytes)

        assertTrue(decoded.backupManga.isEmpty())
    }

    @Test
    fun `rejects json backup bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupDecoder.decodeBackupBytes("""{"backup":[]}""".encodeToByteArray())
        }
    }

    @Test
    fun `rejects corrupt backup bytes`() {
        assertThrows(Throwable::class.java) {
            BackupDecoder.decodeBackupBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03))
        }
    }
}
