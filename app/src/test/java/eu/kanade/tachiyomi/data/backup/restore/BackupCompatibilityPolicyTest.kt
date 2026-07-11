package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class BackupCompatibilityPolicyTest {

    @Test
    fun `duplicate compatible preferences retain the first value and record later conflicts`() {
        val result = BackupCompatibilityPolicy(
            appPreferences = mapOf("theme_mode" to "system"),
            sourcePreferences = mapOf("source_1" to mapOf("enabled" to false)),
        ).evaluate(
            Backup(
                backupManga = emptyList(),
                backupPreferences = listOf(
                    BackupPreference("theme_mode", StringPreferenceValue("dark")),
                    BackupPreference("theme_mode", StringPreferenceValue("light")),
                ),
                backupSourcePreferences = listOf(
                    BackupSourcePreferences(
                        sourceKey = "source_1",
                        prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(true))),
                    ),
                    BackupSourcePreferences(
                        sourceKey = "source_1",
                        prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(false))),
                    ),
                ),
            ),
            RestoreOptions(appSettings = true, sourceSettings = true),
        )

        assertEquals(listOf(StringPreferenceValue("dark")), result.restorable.appPreferences.map { it.value })
        assertEquals(listOf("source_1"), result.restorable.sourcePreferences.map { it.sourceKey })
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:theme_mode" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED &&
                    it.reason.startsWith("Duplicate compatible preference key")
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupSourcePreferences:source_1" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED &&
                    it.reason.startsWith("Duplicate source preference block")
            },
        )
    }

    @Test
    fun `restores app preferences only when key and type match`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(
                BackupPreference("theme_mode", StringPreferenceValue("dark")),
                BackupPreference("reader_mode", IntPreferenceValue(2)),
                BackupPreference("type_changed", StringPreferenceValue("wrong")),
                BackupPreference("mihon_only", BooleanPreferenceValue(true)),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = mapOf(
                "theme_mode" to "system",
                "reader_mode" to 1,
                "type_changed" to 1,
            ),
            sourcePreferences = emptyMap(),
        )

        val result = policy.evaluate(backup, RestoreOptions(appSettings = true))

        assertEquals(listOf("theme_mode", "reader_mode"), result.restorable.appPreferences.map { it.key })
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:type_changed" &&
                    it.decision == BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:mihon_only" &&
                    it.decision == BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED
            },
        )
    }

    @Test
    fun `restores source preferences only for matching local configurable source stores`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupSourcePreferences = listOf(
                BackupSourcePreferences(
                    sourceKey = "source_1",
                    prefs = listOf(
                        BackupPreference("enabled", BooleanPreferenceValue(true)),
                        BackupPreference("languages", StringSetPreferenceValue(setOf("en", "ja"))),
                        BackupPreference("bad_type", IntPreferenceValue(1)),
                    ),
                ),
                BackupSourcePreferences(
                    sourceKey = "source_2",
                    prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(true))),
                ),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = emptyMap<String, Any>(),
            sourcePreferences = mapOf(
                "source_1" to mapOf(
                    "enabled" to false,
                    "languages" to setOf("en"),
                    "bad_type" to "1",
                ),
            ),
        )

        val result = policy.evaluate(backup, RestoreOptions(sourceSettings = true))

        assertEquals(listOf("source_1"), result.restorable.sourcePreferences.map { it.sourceKey })
        assertEquals(listOf("enabled", "languages"), result.restorable.sourcePreferences.single().prefs.map { it.key })
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupSourcePreferences:source_2" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupSourcePreferences:source_1:bad_type" &&
                    it.decision == BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED
            },
        )
    }

    @Test
    fun `records Mihon library and server owned sections as ignored`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 1,
                    url = "/manga",
                    title = "Manga",
                    chapters = listOf(BackupChapter(url = "/chapter", name = "Chapter")),
                    categories = listOf(1),
                    tracking = listOf(BackupTracking(syncId = 1, libraryId = 1)),
                    history = listOf(BackupHistory(url = "/chapter", lastRead = 1)),
                ),
            ),
            backupCategories = listOf(BackupCategory(name = "Default")),
            backupSources = listOf(BackupSource(name = "Source", sourceId = 1)),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = emptyMap<String, Any>(),
            sourcePreferences = emptyMap<String, Map<String, Any>>(),
        )

        val result = policy.evaluate(backup, RestoreOptions())

        val ignoredSections = result.summary.decisions
            .filter { it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED }
            .map { it.section }

        assertTrue("backupManga" in ignoredSections)
        assertTrue("backupCategories" in ignoredSections)
        assertTrue("backupSources" in ignoredSections)
        assertTrue("backupManga.chapters" in ignoredSections)
        assertTrue("backupManga.categories" in ignoredSections)
        assertTrue("backupManga.history" in ignoredSections)
        assertTrue("backupManga.tracking" in ignoredSections)
        assertEquals(7, result.summary.ignoredCount)
    }

    @Test
    fun `records disabled preference restore options as ignored`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(BackupPreference("theme_mode", StringPreferenceValue("dark"))),
            backupSourcePreferences = listOf(
                BackupSourcePreferences(
                    sourceKey = "source_1",
                    prefs = listOf(BackupPreference("enabled", BooleanPreferenceValue(true))),
                ),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = mapOf("theme_mode" to "system"),
            sourcePreferences = mapOf("source_1" to mapOf("enabled" to false)),
        )

        val result = policy.evaluate(backup, RestoreOptions())

        assertTrue(result.restorable.appPreferences.isEmpty())
        assertTrue(result.restorable.sourcePreferences.isEmpty())
        assertEquals(2, result.summary.ignoredCount)
    }

    @Test
    fun `records denied Mihon preference keys as ignored`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(
                BackupPreference("download_new", BooleanPreferenceValue(true)),
                BackupPreference("__APP_STATE_last_used_source", LongPreferenceValue(1)),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = mapOf(
                "download_new" to false,
                "__APP_STATE_last_used_source" to 0L,
            ),
            sourcePreferences = emptyMap(),
        )

        val result = policy.evaluate(backup, RestoreOptions(appSettings = true))

        assertTrue(result.restorable.appPreferences.isEmpty())
        assertEquals(2, result.summary.ignoredCount)
        assertTrue(
            result.summary.decisions.all {
                it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
    }

    @Test
    fun `restores private preference keys only when private restore option is selected`() {
        val privateKey = Preference.privateKey("amatsubu_server_password")
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(
                BackupPreference(privateKey, StringPreferenceValue("secret")),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = mapOf(privateKey to ""),
            sourcePreferences = emptyMap(),
        )

        val publicOnly = policy.evaluate(backup, RestoreOptions(appSettings = true))
        val includePrivate = policy.evaluate(backup, RestoreOptions(appSettings = true, privateSettings = true))

        assertTrue(publicOnly.restorable.appPreferences.isEmpty())
        assertEquals(listOf(privateKey), includePrivate.restorable.appPreferences.map { it.key })
    }

    @Test
    fun `restores known app preferences that are absent from stored SharedPreferences`() {
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(
                BackupPreference("display_download_badge", BooleanPreferenceValue(true)),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = ClientPreferenceRestoreSchema.defaultsWith(backup.backupPreferences.map { it.key }),
            sourcePreferences = emptyMap(),
        )

        val result = policy.evaluate(backup, RestoreOptions(appSettings = true))

        assertEquals(listOf("display_download_badge"), result.restorable.appPreferences.map { it.key })
        assertEquals(BackupCompatibilityDecisionType.RESTORE_DIRECT, result.summary.decisions.single().decision)
    }

    @Test
    fun `restore scope keeps client owned settings and rejects server owned legacy settings`() {
        val appStateKey = "__APP_STATE_last_used_source"
        val backup = Backup(
            backupManga = emptyList(),
            backupPreferences = listOf(
                BackupPreference("amatsubu_server_url", StringPreferenceValue("http://127.0.0.1:4567")),
                BackupPreference("show_nsfw_source", BooleanPreferenceValue(false)),
                BackupPreference("migration_flags", IntPreferenceValue(3)),
                BackupPreference("download_new", BooleanPreferenceValue(true)),
                BackupPreference("track_1_token", StringPreferenceValue("token")),
                BackupPreference(appStateKey, LongPreferenceValue(1L)),
                BackupPreference("legacy_local_library_authority", BooleanPreferenceValue(true)),
            ),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = ClientPreferenceRestoreSchema.defaultsWith(backup.backupPreferences.map { it.key }),
            sourcePreferences = emptyMap(),
        )

        val result = policy.evaluate(backup, RestoreOptions(appSettings = true))

        assertEquals(
            listOf("amatsubu_server_url", "show_nsfw_source", "migration_flags"),
            result.restorable.appPreferences.map { it.key },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:download_new" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:track_1_token" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:$appStateKey" &&
                    it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED
            },
        )
        assertTrue(
            result.summary.decisions.any {
                it.section == "backupPreferences:legacy_local_library_authority" &&
                    it.decision == BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED
            },
        )
    }

    @Test
    fun `legacy library and category restore options do not make server owned sections restorable`() {
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 1,
                    url = "/manga",
                    title = "Manga",
                    chapters = listOf(BackupChapter(url = "/chapter", name = "Chapter")),
                    categories = listOf(1),
                    tracking = listOf(BackupTracking(syncId = 1, libraryId = 1)),
                    history = listOf(BackupHistory(url = "/chapter", lastRead = 1)),
                ),
            ),
            backupCategories = listOf(BackupCategory(name = "Default")),
            backupSources = listOf(BackupSource(name = "Source", sourceId = 1)),
        )
        val policy = BackupCompatibilityPolicy(
            appPreferences = emptyMap<String, Any>(),
            sourcePreferences = emptyMap<String, Map<String, Any>>(),
        )

        val result = policy.evaluate(
            backup,
            RestoreOptions(
                libraryEntries = true,
                categories = true,
                appSettings = false,
                sourceSettings = false,
            ),
        )

        assertTrue(result.restorable.appPreferences.isEmpty())
        assertTrue(result.restorable.sourcePreferences.isEmpty())
        assertEquals(7, result.summary.ignoredCount)
        assertTrue(result.summary.decisions.all { it.decision == BackupCompatibilityDecisionType.IGNORE_RECORDED })
    }
}
