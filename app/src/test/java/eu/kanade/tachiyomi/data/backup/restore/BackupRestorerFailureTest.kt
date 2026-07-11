package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupClientMangaMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestoreResult
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class BackupRestorerFailureTest {

    @Test
    fun `metadata import failure stops before preference restore writes`() {
        val preferenceRestorer = RecordingPreferenceRestorer()
        val failure = IllegalStateException("metadata import failed")
        val applier = ClientBackupRestoreApplier(
            preferenceStore = MapPreferenceStore(),
            preferenceRestorer = preferenceRestorer,
            mangaMetadataRestorer = ThrowingMetadataRestorer(failure),
            currentServerKey = { "server-a" },
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest {
                applier.apply(
                    backup = Backup(
                        backupManga = emptyList(),
                        backupPreferences = listOf(
                            BackupPreference("display_download_badge", BooleanPreferenceValue(true)),
                        ),
                        clientMangaMetadata = listOf(
                            BackupClientMangaMetadata(
                                serverKey = "server-a",
                                sourceId = 1,
                                mangaId = 2,
                                notes = "",
                                memo = "{}",
                                updatedAt = 3,
                            ),
                        ),
                    ),
                    options = RestoreOptions(appSettings = true),
                )
            }
        }

        assertEquals(failure, thrown)
        assertEquals(0, preferenceRestorer.appRestoreCalls)
        assertEquals(0, preferenceRestorer.sourceRestoreCalls)
    }

    private class RecordingPreferenceRestorer : ClientBackupPreferenceRestorer {
        var appRestoreCalls = 0
        var sourceRestoreCalls = 0

        override fun restoreApp(
            preferences: List<BackupPreference>,
            defaultValues: Map<String, Any>,
        ): PreferenceRestoreResult {
            appRestoreCalls++
            return PreferenceRestoreResult(restored = preferences.size)
        }

        override fun restoreSource(preferences: List<BackupSourcePreferences>): PreferenceRestoreResult {
            sourceRestoreCalls++
            return PreferenceRestoreResult()
        }
    }

    private class ThrowingMetadataRestorer(
        private val failure: RuntimeException,
    ) : ClientBackupMangaMetadataRestorer {
        override suspend fun restoreForServer(serverKey: String, metadata: List<ClientMangaMetadata>) {
            throw failure
        }
    }

    private class MapPreferenceStore : PreferenceStore {
        override fun getString(key: String, defaultValue: String): Preference<String> =
            MapPreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            MapPreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            MapPreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            MapPreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            MapPreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            MapPreference(key, defaultValue)

        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = MapPreference(key, defaultValue)

        override fun <T> getObjectFromInt(
            key: String,
            defaultValue: T,
            serializer: (T) -> Int,
            deserializer: (Int) -> T,
        ): Preference<T> = MapPreference(key, defaultValue)

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }

    private class MapPreference<T>(
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {
        override fun key(): String = key

        override fun get(): T = defaultValue

        override fun set(value: T) = Unit

        override fun isSet(): Boolean = false

        override fun delete() = Unit

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = MutableStateFlow(defaultValue)

        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): StateFlow<T> =
            MutableStateFlow(defaultValue)
    }
}
