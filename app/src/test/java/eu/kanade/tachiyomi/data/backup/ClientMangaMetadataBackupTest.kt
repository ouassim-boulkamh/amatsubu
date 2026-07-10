package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupClientMangaMetadata
import eu.kanade.tachiyomi.data.backup.models.toBackupMetadata
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadata
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientMangaMetadataBackupTest {

    @Test
    fun `client manga metadata backup leaves notes as legacy local-only data`() {
        val memo = buildJsonObject { put("rating", 5) }
        val backup = Backup(
            backupManga = emptyList(),
            clientMangaMetadata = listOf(
                BackupClientMangaMetadata(
                    serverKey = "https://server.test/api/graphql",
                    sourceId = 7,
                    mangaId = 11,
                    notes = "Device-only note",
                    memo = memo.toString(),
                    updatedAt = 1234,
                ),
            ),
        )

        val decoded = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            BackupCreator.encodeBackup(backup),
        )

        assertEquals(emptyList<Any>(), decoded.backupManga)
        assertEquals(backup.clientMangaMetadata, decoded.clientMangaMetadata)
    }

    @Test
    fun `active client metadata export does not carry legacy local notes`() {
        val backup = ClientMangaMetadata(
            serverKey = "https://server.test/api/graphql",
            sourceId = 7,
            mangaId = 11,
            notes = "Legacy local note",
            memo = JsonObject(mapOf("rating" to kotlinx.serialization.json.JsonPrimitive(5))),
            updatedAt = 1234,
        ).toBackupMetadata()

        assertEquals("", backup.notes)
        assertEquals("""{"rating":5}""", backup.memo)
    }

    @Test
    fun `old backups default client manga metadata to empty`() {
        val decoded = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            BackupCreator.encodeBackup(Backup(backupManga = emptyList())),
        )

        assertEquals(emptyList<BackupClientMangaMetadata>(), decoded.clientMangaMetadata)
    }
}
