package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class PreferenceRestorerTest {

    @Test
    fun `restores compatible app preference values`() {
        val store = MutablePreferenceStore(
            "int" to 1,
            "long" to 1L,
            "float" to 1f,
            "string" to "old",
            "boolean" to false,
            "set" to setOf("old"),
        )
        val restorer = PreferenceRestorer(
            context = null,
            preferenceStore = store,
            sourcePreferenceStoreFactory = { error("Source restore is not used") },
        )

        val result = restorer.restoreApp(
            listOf(
                BackupPreference("int", IntPreferenceValue(2)),
                BackupPreference("long", LongPreferenceValue(3L)),
                BackupPreference("float", FloatPreferenceValue(4f)),
                BackupPreference("string", StringPreferenceValue("new")),
                BackupPreference("boolean", BooleanPreferenceValue(true)),
                BackupPreference("set", StringSetPreferenceValue(setOf("new", "other"))),
            ),
        )

        assertEquals(PreferenceRestoreResult(restored = 6), result)
        assertEquals(2, store.value("int"))
        assertEquals(3L, store.value("long"))
        assertEquals(4f, store.value("float"))
        assertEquals("new", store.value("string"))
        assertEquals(true, store.value("boolean"))
        assertEquals(setOf("new", "other"), store.value("set"))
    }

    @Test
    fun `skips incompatible app preference values`() {
        val store = MutablePreferenceStore("int" to 1)
        val restorer = PreferenceRestorer(
            context = null,
            preferenceStore = store,
            sourcePreferenceStoreFactory = { error("Source restore is not used") },
        )

        val result = restorer.restoreApp(
            listOf(
                BackupPreference("int", StringPreferenceValue("wrong")),
                BackupPreference("missing", BooleanPreferenceValue(true)),
            ),
        )

        assertEquals(PreferenceRestoreResult(restored = 0, failed = 2), result)
        assertEquals(1, store.value("int"))
    }

    @Test
    fun `restores known app preference values that are currently unset`() {
        val store = MutablePreferenceStore()
        val restorer = PreferenceRestorer(
            context = null,
            preferenceStore = store,
            sourcePreferenceStoreFactory = { error("Source restore is not used") },
        )

        val result = restorer.restoreApp(
            listOf(BackupPreference("display_download_badge", BooleanPreferenceValue(true))),
            defaultValues = mapOf("display_download_badge" to false),
        )

        assertEquals(PreferenceRestoreResult(restored = 1), result)
        assertEquals(true, store.value("display_download_badge"))
    }

    @Test
    fun `restores source preferences into matching source stores`() {
        val appStore = MutablePreferenceStore()
        val sourceStore = MutablePreferenceStore("enabled" to false)
        val restorer = PreferenceRestorer(
            context = null,
            preferenceStore = appStore,
            sourcePreferenceStoreFactory = { key ->
                check(key == "source_1")
                sourceStore
            },
        )

        val result = restorer.restoreSource(
            listOf(
                BackupSourcePreferences(
                    sourceKey = "source_1",
                    prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(true))),
                ),
            ),
        )

        assertEquals(PreferenceRestoreResult(restored = 1), result)
        assertEquals(true, sourceStore.value("enabled"))
    }

    @Test
    fun `source preference restore keeps historical file names`() {
        assertEquals("source_1", restoredSourcePreferenceFileName("source_1"))
        assertEquals("local", restoredSourcePreferenceFileName("local"))
    }

    private class MutablePreferenceStore(
        vararg initialValues: Pair<String, Any>,
    ) : PreferenceStore {
        private val values = initialValues.toMap().toMutableMap()

        fun value(key: String): Any? = values[key]

        override fun getString(key: String, defaultValue: String): Preference<String> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun <T> getObjectFromInt(
            key: String,
            defaultValue: T,
            serializer: (T) -> Int,
            deserializer: (Int) -> T,
        ): Preference<T> {
            return MutablePreference(key, values, defaultValue)
        }

        override fun getAll(): Map<String, *> = values.toMap()
    }

    private class MutablePreference<T>(
        private val key: String,
        private val values: MutableMap<String, Any>,
        private val defaultValue: T,
    ) : Preference<T> {

        override fun key(): String = key

        @Suppress("UNCHECKED_CAST")
        override fun get(): T = values[key] as? T ?: defaultValue

        override fun set(value: T) {
            values[key] = value as Any
        }

        override fun isSet(): Boolean = key in values

        override fun delete() {
            values.remove(key)
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = MutableStateFlow(get())

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
    }
}
