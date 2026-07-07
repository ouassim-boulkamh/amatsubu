package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupFileValidator(
    private val context: Context,
) {

    fun validate(uri: Uri): Results {
        BackupDecoder(context).decode(uri)
        return Results()
    }

    data class Results(
        val missingSources: List<String> = emptyList(),
        val missingTrackers: List<String> = emptyList(),
    )
}

class ServerBackupFileValidator(
    private val context: Context,

    private val json: Json = Injekt.get(),
) {
    private val suwayomiProvider = SuwayomiClientProvider()

    suspend fun validateOnServer(uri: Uri): Results {
        return validateBytes(readBackupBytes(uri))
    }

    suspend fun validateBytes(backup: ByteArray): Results {
        val request = POST(
            url = suwayomiProvider.restUrl("/api/v1/backup/validate"),
            body = backup.toRequestBody(PROTOBUF_BACKUP_MEDIA_TYPE),
        )

        val result = with(json) {
            suwayomiProvider.httpClient
                .newCall(request)
                .awaitSuccess()
                .parseAs<ServerValidationResult>()
        }

        return Results(
            missingSources = result.missingSources,
            missingTrackers = result.missingTrackers,
        )
    }

    private fun readBackupBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read backup file")
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )

    @Serializable
    private data class ServerValidationResult(
        val missingSources: List<String> = emptyList(),
        val missingTrackers: List<String> = emptyList(),
    )
}

private val PROTOBUF_BACKUP_MEDIA_TYPE = "application/octet-stream".toMediaType()
