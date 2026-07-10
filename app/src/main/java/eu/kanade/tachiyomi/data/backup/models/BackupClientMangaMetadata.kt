package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoNumber

/** Explicit device-owned metadata; notes is legacy local-only data and is no longer restored. */
@Serializable
data class BackupClientMangaMetadata(
    @ProtoNumber(1) val serverKey: String,
    @ProtoNumber(2) val sourceId: Long,
    @ProtoNumber(3) val mangaId: Long,
    @ProtoNumber(4) val notes: String,
    @ProtoNumber(5) val memo: String,
    @ProtoNumber(6) val updatedAt: Long,
) {
    fun toClientMetadata() = ClientMangaMetadata(
        serverKey,
        sourceId,
        mangaId,
        notes = "",
        Json.decodeFromString(memo),
        updatedAt,
    )
}

fun ClientMangaMetadata.toBackupMetadata() = BackupClientMangaMetadata(
    serverKey = serverKey,
    sourceId = sourceId,
    mangaId = mangaId,
    notes = "",
    memo = Json.encodeToString(JsonObject.serializer(), memo),
    updatedAt = updatedAt,
)
