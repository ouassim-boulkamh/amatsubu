package eu.kanade.tachiyomi.data.backup.create

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

class BackupOptionsTest {

    @Test
    fun `client backup exposes app source and private settings options only`() {
        assertTrue(BackupOptions.libraryOptions.isEmpty())
        assertEquals(
            listOf(
                MR.strings.app_settings,
                MR.strings.source_settings,
                MR.strings.private_settings,
            ),
            BackupOptions.settingsOptions.map { it.label },
        )
    }

    @Test
    fun `client backup can be created when app or source settings are selected`() {
        assertTrue(BackupOptions(appSettings = true, sourceSettings = false).canCreate())
        assertTrue(BackupOptions(appSettings = false, sourceSettings = true).canCreate())
        assertFalse(BackupOptions(appSettings = false, sourceSettings = false).canCreate())
    }

    @Test
    fun `private backup option is enabled only when a settings section is selected`() {
        val privateOption = BackupOptions.settingsOptions.single { it.label == MR.strings.private_settings }

        assertTrue(privateOption.enabled(BackupOptions(appSettings = true, sourceSettings = false)))
        assertTrue(privateOption.enabled(BackupOptions(appSettings = false, sourceSettings = true)))
        assertFalse(privateOption.enabled(BackupOptions(appSettings = false, sourceSettings = false)))
    }
}
