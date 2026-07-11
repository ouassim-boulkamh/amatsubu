package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf,
) {

    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            decodeBackupInput(inputStream, parser)
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
        fun decodeBackupBytes(
            bytes: ByteArray,
            parser: ProtoBuf = ProtoBuf,
            maxCompressedBytes: Int = MAX_COMPRESSED_BYTES,
            maxDecodedBytes: Int = MAX_DECODED_BYTES,
        ): Backup {
            return decodeBackupInput(ByteArrayInputStream(bytes), parser, maxCompressedBytes, maxDecodedBytes)
        }

        private fun decodeBackupInput(
            input: InputStream,
            parser: ProtoBuf,
            maxCompressedBytes: Int = MAX_COMPRESSED_BYTES,
            maxDecodedBytes: Int = MAX_DECODED_BYTES,
        ): Backup {
            try {
                val pushback = PushbackInputStream(input, 2)
                val signature = ByteArray(2)
                val signatureSize = pushback.read(signature)
                if (signatureSize > 0) pushback.unread(signature, 0, signatureSize)
                val signatureValue = if (signatureSize == 2) {
                    ((signature[0].toInt() and 0xff) shl 8) or (signature[1].toInt() and 0xff)
                } else {
                    -1
                }
                when (signatureValue) {
                    MAGIC_JSON_SIGNATURE1,
                    MAGIC_JSON_SIGNATURE2,
                    MAGIC_JSON_SIGNATURE3,
                    -> throw JsonBackupException()
                }
                val backupBytes = if (signatureValue == GZIP_MAGIC) {
                    GZIPInputStream(SizeLimitedInputStream(pushback, maxCompressedBytes)).use {
                        it.readByteArrayWithinLimit(maxDecodedBytes)
                    }
                } else {
                    SizeLimitedInputStream(pushback, maxDecodedBytes).use {
                        it.readByteArrayWithinLimit(maxDecodedBytes)
                    }
                }

                return parser.decodeFromByteArray(Backup.serializer(), backupBytes)
            } catch (error: JsonBackupException) {
                throw error
            } catch (error: SerializationException) {
                throw error
            } catch (error: Exception) {
                throw SerializationException("Unable to decode backup", error)
            }
        }

        private fun InputStream.readByteArrayWithinLimit(maxBytes: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = read(buffer)
                if (count < 0) return output.toByteArray()
                total += count
                if (total > maxBytes) throw BackupSizeLimitException(maxBytes)
                output.write(buffer, 0, count)
            }
        }

        internal const val MAX_COMPRESSED_BYTES = 16 * 1024 * 1024
        internal const val MAX_DECODED_BYTES = 64 * 1024 * 1024
        private const val GZIP_MAGIC = 0x1f8b
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }
}

private class JsonBackupException : IllegalArgumentException()

private class BackupSizeLimitException(maxBytes: Int) : IOException("Backup exceeds $maxBytes bytes")

private class SizeLimitedInputStream(
    input: InputStream,
    private val maxBytes: Int,
) : FilterInputStream(input) {
    private var bytesRead = 0

    override fun read(): Int {
        if (bytesRead >= maxBytes) return readPastLimit()
        return super.read().also { if (it >= 0) bytesRead++ }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRead >= maxBytes) return readPastLimit()
        val count = super.read(buffer, offset, minOf(length, maxBytes - bytesRead))
        if (count > 0) bytesRead += count
        return count
    }

    private fun readPastLimit(): Int {
        if (super.read() < 0) return -1
        throw BackupSizeLimitException(maxBytes)
    }
}
