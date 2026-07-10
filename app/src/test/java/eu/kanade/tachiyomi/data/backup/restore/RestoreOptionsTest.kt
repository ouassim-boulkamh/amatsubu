package eu.kanade.tachiyomi.data.backup.restore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

class RestoreOptionsTest {

    @Test
    fun `client restore exposes app source and private settings options only`() {
        assertEquals(
            listOf(
                MR.strings.app_settings,
                MR.strings.source_settings,
                MR.strings.private_settings,
            ),
            RestoreOptions.options.map { it.label },
        )
    }

    @Test
    fun `client restore ignores hidden library fields for enablement`() {
        assertFalse(RestoreOptions(libraryEntries = true, categories = true).canRestore())
        assertTrue(RestoreOptions(appSettings = true).canRestore())
        assertTrue(RestoreOptions(sourceSettings = true).canRestore())
    }

    @Test
    fun `client restore defaults keep legacy server owned sections disabled`() {
        val options = RestoreOptions()

        assertFalse(options.libraryEntries)
        assertFalse(options.categories)
    }

    @Test
    fun `restore options keep backwards compatible boolean array decoding`() {
        val options = RestoreOptions.fromBooleanArray(
            booleanArrayOf(
                true,
                true,
                true,
                false,
            ),
        )

        assertFalse(options.libraryEntries)
        assertFalse(options.categories)
        assertTrue(options.appSettings)
        assertFalse(options.sourceSettings)
        assertFalse(options.privateSettings)
    }

    @Test
    fun `private restore option is enabled only when a settings section is selected`() {
        val privateOption = RestoreOptions.options.single { it.label == MR.strings.private_settings }

        assertTrue(privateOption.enabled(RestoreOptions(appSettings = true, sourceSettings = false)))
        assertTrue(privateOption.enabled(RestoreOptions(appSettings = false, sourceSettings = true)))
        assertFalse(privateOption.enabled(RestoreOptions(appSettings = false, sourceSettings = false)))
    }
}
