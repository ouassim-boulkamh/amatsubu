package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PreferenceBackupCreatorTest {

    @Test
    fun `serializes primitive app preferences and round trips through backup protobuf`() {
        val creator = PreferenceBackupCreator(
            preferenceStore = MapPreferenceStore(
                mapOf(
                    "int" to 1,
                    "long" to 2L,
                    "float" to 3.5f,
                    "string" to "value",
                    "boolean" to true,
                ),
            ),
        )

        val preferences = creator.createApp(includePrivatePreferences = false)
        val decoded = BackupDecoder.decodeBackupBytes(
            gzip(BackupCreator.encodeBackup(Backup(backupManga = emptyList(), backupPreferences = preferences))),
        )
        val values = decoded.backupPreferences.associate { it.key to it.value }

        assertEquals(IntPreferenceValue(1), values["int"])
        assertEquals(LongPreferenceValue(2L), values["long"])
        assertEquals(FloatPreferenceValue(3.5f), values["float"])
        assertEquals(StringPreferenceValue("value"), values["string"])
        assertEquals(BooleanPreferenceValue(true), values["boolean"])
    }

    @Test
    fun `serializes string set preferences`() {
        val creator = PreferenceBackupCreator(
            preferenceStore = MapPreferenceStore(mapOf("languages" to setOf("en", "ja"))),
        )

        val preferences = creator.createApp(includePrivatePreferences = false)

        assertEquals(
            StringSetPreferenceValue(setOf("en", "ja")),
            preferences.single { it.key == "languages" }.value,
        )
    }

    @Test
    fun `includes the enabled live server notification default in backups`() {
        val preferences = PreferenceBackupCreator(MapPreferenceStore())
            .createApp(includePrivatePreferences = false)
            .associate { it.key to it.value }

        assertEquals(
            BooleanPreferenceValue(true),
            preferences["amatsubu_live_server_notifications"],
        )
        assertEquals(
            BooleanPreferenceValue(true),
            preferences["amatsubu_show_server_address_in_live_notification"],
        )
    }

    @Test
    fun `filters app state and private preferences unless private is selected`() {
        val privateKey = Preference.privateKey("server_password")
        val appStateKey = Preference.appStateKey("last_screen")
        val creator = PreferenceBackupCreator(
            preferenceStore = MapPreferenceStore(
                mapOf(
                    "theme" to "dark",
                    privateKey to "secret",
                    appStateKey to "Browse",
                ),
            ),
        )

        val publicOnly = creator.createApp(includePrivatePreferences = false)
        val withPrivate = creator.createApp(includePrivatePreferences = true)

        assertEquals(
            setOf("theme", "amatsubu_live_server_notifications", "amatsubu_show_server_address_in_live_notification"),
            publicOnly.map { it.key }.toSet(),
        )
        assertEquals(
            setOf(
                "theme",
                privateKey,
                "amatsubu_live_server_notifications",
                "amatsubu_show_server_address_in_live_notification",
            ),
            withPrivate.map { it.key }.toSet(),
        )
        assertFalse(withPrivate.any { it.key == appStateKey })
    }

    @Test
    fun `does not serialize local source preference stores`() {
        val creator = PreferenceBackupCreator(
            preferenceStore = MapPreferenceStore(),
        )

        assertEquals(emptyList<Any>(), creator.createSource(includePrivatePreferences = false))
        assertEquals(emptyList<Any>(), creator.createSource(includePrivatePreferences = true))
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(bytes)
            }
            output.toByteArray()
        }
    }

    private class MapPreferenceStore(
        private val preferences: Map<String, *> = emptyMap<String, Any>(),
    ) : PreferenceStore {
        override fun getString(key: String, defaultValue: String): Preference<String> = unsupported()
        override fun getLong(key: String, defaultValue: Long): Preference<Long> = unsupported()
        override fun getInt(key: String, defaultValue: Int): Preference<Int> = unsupported()
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = unsupported()
        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = unsupported()
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = unsupported()
        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = unsupported()

        override fun <T> getObjectFromInt(
            key: String,
            defaultValue: T,
            serializer: (T) -> Int,
            deserializer: (Int) -> T,
        ): Preference<T> = unsupported()

        override fun getAll(): Map<String, *> = preferences

        private fun <T> unsupported(): T = error("Only getAll is used by PreferenceBackupCreator")
    }
}
