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
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.PreferenceScreen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PreferenceBackupCreatorTest {

    @Test
    fun `serializes primitive app preferences and round trips through backup protobuf`() {
        val creator = PreferenceBackupCreator(
            sourceManager = FakeSourceManager(),
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
            sourceManager = FakeSourceManager(),
            preferenceStore = MapPreferenceStore(mapOf("languages" to setOf("en", "ja"))),
        )

        val preferences = creator.createApp(includePrivatePreferences = false)

        assertEquals(
            StringSetPreferenceValue(setOf("en", "ja")),
            preferences.single { it.key == "languages" }.value,
        )
    }

    @Test
    fun `filters app state and private preferences unless private is selected`() {
        val privateKey = Preference.privateKey("server_password")
        val appStateKey = Preference.appStateKey("last_screen")
        val creator = PreferenceBackupCreator(
            sourceManager = FakeSourceManager(),
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

        assertEquals(listOf("theme"), publicOnly.map { it.key })
        assertEquals(setOf("theme", privateKey), withPrivate.map { it.key }.toSet())
        assertFalse(withPrivate.any { it.key == appStateKey })
    }

    @Test
    fun `serializes only local configurable source preference stores`() {
        val configurableSource = FakeConfigurableSource(id = 1, name = "Configurable")
        val nonConfigurableSource = FakeSource(id = 2, name = "Plain")
        val creator = PreferenceBackupCreator(
            sourceManager = FakeSourceManager(listOf(configurableSource, nonConfigurableSource)),
            preferenceStore = MapPreferenceStore(),
            sourcePreferenceReader = { source ->
                when (source.id) {
                    1L -> mapOf(
                        "enabled" to true,
                        Preference.privateKey("token") to "private-token",
                        Preference.appStateKey("cache") to "drop",
                    )
                    else -> emptyMap<String, Any>()
                }
            },
        )

        val publicSourcePreferences = creator.createSource(includePrivatePreferences = false)
        val privateSourcePreferences = creator.createSource(includePrivatePreferences = true)

        assertEquals(listOf("source_1"), publicSourcePreferences.map { it.sourceKey })
        assertEquals(listOf("enabled"), publicSourcePreferences.single().prefs.map { it.key })
        assertEquals(
            setOf("enabled", Preference.privateKey("token")),
            privateSourcePreferences.single().prefs.map { it.key }.toSet(),
        )
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

    private class FakeSourceManager(
        private val allSources: List<Source> = emptyList(),
    ) : SourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val sources: Flow<List<Source>> = MutableStateFlow(allSources)
        override fun get(sourceKey: Long): Source? = allSources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: error("No source $sourceKey")
        override fun getAll(): List<Source> = allSources
        override fun getOnlineSources() = emptyList<eu.kanade.tachiyomi.source.online.HttpSource>()
        override fun getStubSources() = emptyList<StubSource>()
    }

    private open class FakeSource(
        override val id: Long,
        override val name: String,
    ) : Source {
        override val supportsLatest = false
        override fun getFilterList(): FilterList = FilterList()
        override suspend fun getPopularManga(page: Int): MangasPage = unsupported()
        override suspend fun getLatestUpdates(page: Int): MangasPage = unsupported()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = unsupported()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = unsupported()

        override suspend fun getPageList(chapter: SChapter): List<Page> = unsupported()
        protected fun <T> unsupported(): T = error("Not used in PreferenceBackupCreator tests")
    }

    private class FakeConfigurableSource(
        id: Long,
        name: String,
    ) : FakeSource(id, name), ConfigurableSource {
        override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit
    }
}
