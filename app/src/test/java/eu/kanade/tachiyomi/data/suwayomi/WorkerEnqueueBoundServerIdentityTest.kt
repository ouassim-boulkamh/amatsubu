package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.buildServerBackupCreateInputData
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.buildServerBackupRestoreInputData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class WorkerEnqueueBoundServerIdentityTest {

    @Test
    fun `client device copy save input captures enqueue-time server identity`() {
        val inputData = ClientDeviceChapterCopyWorker.buildSaveInputData(
            serverKey = SERVER_A,
            mangaTitle = "Manga",
            chapterId = 10,
        )

        val result = EnqueueBoundServerIdentity.check(inputData, SERVER_A)

        val matched = assertInstanceOf(EnqueueBoundServerIdentityCheck.Matched::class.java, result)
        assertEquals(SERVER_A, matched.serverKey)
    }

    @Test
    fun `client device copy remove input blocks execution after server switch`() {
        val inputData = ClientDeviceChapterCopyWorker.buildRemoveInputData(
            serverKey = SERVER_A,
            mangaId = 20,
            chapterId = 10,
        )

        val result = EnqueueBoundServerIdentity.check(inputData, SERVER_B)

        val mismatch = assertInstanceOf(EnqueueBoundServerIdentityCheck.Mismatched::class.java, result)
        assertEquals(SERVER_A, mismatch.enqueuedServerKey)
        assertEquals(SERVER_B, mismatch.currentServerKey)
    }

    @Test
    fun `server backup create input captures enqueue-time server identity`() {
        val inputData = buildServerBackupCreateInputData(
            locationUri = "content://backup/create",
            options = BackupOptions(appSettings = true, sourceSettings = false),
            serverKey = SERVER_A,
        )

        val result = EnqueueBoundServerIdentity.check(inputData, SERVER_A)

        val matched = assertInstanceOf(EnqueueBoundServerIdentityCheck.Matched::class.java, result)
        assertEquals(SERVER_A, matched.serverKey)
    }

    @Test
    fun `server backup restore input blocks execution after server switch`() {
        val inputData = buildServerBackupRestoreInputData(
            locationUri = "content://backup/restore",
            options = RestoreOptions(appSettings = true, sourceSettings = false),
            sync = false,
            serverKey = SERVER_A,
        )

        val result = EnqueueBoundServerIdentity.check(inputData, SERVER_B)

        val mismatch = assertInstanceOf(EnqueueBoundServerIdentityCheck.Mismatched::class.java, result)
        assertEquals(SERVER_A, mismatch.enqueuedServerKey)
        assertEquals(SERVER_B, mismatch.currentServerKey)
    }

    private companion object {
        const val SERVER_A = "http://server-a.test/api/graphql"
        const val SERVER_B = "http://server-b.test/api/graphql"
    }
}
