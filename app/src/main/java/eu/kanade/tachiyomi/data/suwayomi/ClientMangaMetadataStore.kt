package eu.kanade.tachiyomi.data.suwayomi

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Client_manga_metadata
import tachiyomi.data.Database

/**
 * Device-local Amatsubu metadata. The notes field is legacy local-only data;
 * active Amatsubu notes are Suwayomi-owned manga metadata under
 * [SERVER_MANGA_NOTES_META_KEY].
 */
data class ClientMangaMetadata(
    val serverKey: String,
    val sourceId: Long,
    val mangaId: Long,
    val notes: String,
    val memo: JsonObject,
    val updatedAt: Long,
)

class ClientMangaMetadataStore(
    private val database: Database,
    private val json: Json,
) {
    suspend fun get(serverKey: String, sourceId: Long, mangaId: Long): ClientMangaMetadata? {
        return database.client_manga_metadataQueries
            .getMetadata(serverKey, sourceId, mangaId)
            .awaitAsOneOrNull()
            ?.toModel()
    }

    suspend fun save(
        serverKey: String,
        sourceId: Long,
        mangaId: Long,
        notes: String,
        memo: JsonObject = JsonObject(emptyMap()),
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        database.client_manga_metadataQueries.upsertMetadata(
            serverKey = serverKey,
            sourceId = sourceId,
            mangaId = mangaId,
            notes = notes,
            memo = json.encodeToString(memo),
            updatedAt = updatedAt,
        )
    }

    suspend fun forServer(serverKey: String): List<ClientMangaMetadata> {
        return database.client_manga_metadataQueries.getMetadataForServer(serverKey)
            .awaitAsList()
            .map { it.toModel() }
    }

    /** Imports only records explicitly addressed to the current server. */
    suspend fun restoreForServer(serverKey: String, metadata: List<ClientMangaMetadata>) {
        database.transaction {
            metadata.asSequence()
                .filter { it.serverKey == serverKey }
                .forEach {
                    database.client_manga_metadataQueries.upsertMetadata(
                        serverKey = it.serverKey,
                        sourceId = it.sourceId,
                        mangaId = it.mangaId,
                        notes = it.notes,
                        memo = json.encodeToString(it.memo),
                        updatedAt = it.updatedAt,
                    )
                }
        }
    }

    private fun Client_manga_metadata.toModel() = ClientMangaMetadata(
        serverKey = server_key,
        sourceId = source_id,
        mangaId = manga_id,
        notes = notes,
        memo = json.decodeFromString(memo),
        updatedAt = updated_at,
    )
}
