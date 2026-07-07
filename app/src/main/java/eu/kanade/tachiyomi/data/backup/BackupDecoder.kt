package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {

    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            decode(inputStream.readBytes())
        } ?: throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
    }

    fun decode(bytes: ByteArray): Backup {
        return try {
            decodeBackupBytes(bytes, parser)
        } catch (_: JsonBackupException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
        } catch (_: SerializationException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
        }
    }

    companion object {
        fun decodeBackupBytes(bytes: ByteArray, parser: ProtoBuf = ProtoBuf): Backup {
            val source = bytes.inputStream().source().buffer()

            val backupBytes = source.use {
                val peeked = it.peek()
                if (bytes.size >= 2) {
                    peeked.require(2)
                    when (peeked.readShort().toInt()) {
                        GZIP_MAGIC -> it.gzip().buffer().use { gzipSource -> gzipSource.readByteArray() }
                        MAGIC_JSON_SIGNATURE1,
                        MAGIC_JSON_SIGNATURE2,
                        MAGIC_JSON_SIGNATURE3,
                        -> throw JsonBackupException()
                        else -> it.readByteArray()
                    }
                } else {
                    it.readByteArray()
                }
            }

            return parser.decodeFromByteArray(Backup.serializer(), backupBytes)
        }

        private const val GZIP_MAGIC = 0x1f8b
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}

private class JsonBackupException : IllegalArgumentException()
