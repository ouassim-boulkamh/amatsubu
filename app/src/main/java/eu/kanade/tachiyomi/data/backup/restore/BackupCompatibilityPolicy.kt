package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.PreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import tachiyomi.core.common.preference.Preference

class BackupCompatibilityPolicy(
    private val appPreferences: Map<String, *>,
    private val sourcePreferences: Map<String, Map<String, *>>,
) {

    fun evaluate(backup: Backup, options: RestoreOptions): BackupCompatibilityResult {
        val decisions = mutableListOf<BackupCompatibilityDecision>()

        recordServerOwnedSections(backup, decisions)

        val appPrefs = if (options.appSettings) {
            backup.backupPreferences.filterRestorablePreferences(
                existingPreferences = appPreferences,
                section = SECTION_APP_PREFERENCES,
                includePrivatePreferences = options.privateSettings,
                decisions = decisions,
            )
        } else {
            if (backup.backupPreferences.isNotEmpty()) {
                decisions += BackupCompatibilityDecision(
                    section = SECTION_APP_PREFERENCES,
                    decision = BackupCompatibilityDecisionType.IGNORE_RECORDED,
                    reason = "App settings restore option is disabled.",
                    count = backup.backupPreferences.size,
                )
            }
            emptyList()
        }

        val sourcePrefs = if (options.sourceSettings) {
            backup.backupSourcePreferences.mapNotNull { sourcePreferenceBackup ->
                val existingPreferences = sourcePreferences[sourcePreferenceBackup.sourceKey]
                if (existingPreferences == null) {
                    decisions += BackupCompatibilityDecision(
                        section = "$SECTION_SOURCE_PREFERENCES:${sourcePreferenceBackup.sourceKey}",
                        decision = BackupCompatibilityDecisionType.IGNORE_RECORDED,
                        reason = "No matching local ConfigurableSource preference store exists in Amatsubu.",
                        count = sourcePreferenceBackup.prefs.size,
                    )
                    return@mapNotNull null
                }

                val restorablePreferences = sourcePreferenceBackup.prefs.filterRestorablePreferences(
                    existingPreferences = existingPreferences,
                    section = "$SECTION_SOURCE_PREFERENCES:${sourcePreferenceBackup.sourceKey}",
                    includePrivatePreferences = options.privateSettings,
                    decisions = decisions,
                )

                if (restorablePreferences.isEmpty()) {
                    null
                } else {
                    sourcePreferenceBackup.copy(prefs = restorablePreferences)
                }
            }
        } else {
            val count = backup.backupSourcePreferences.sumOf { it.prefs.size }
            if (count > 0) {
                decisions += BackupCompatibilityDecision(
                    section = SECTION_SOURCE_PREFERENCES,
                    decision = BackupCompatibilityDecisionType.IGNORE_RECORDED,
                    reason = "Source settings restore option is disabled.",
                    count = count,
                )
            }
            emptyList()
        }

        return BackupCompatibilityResult(
            restorable = RestorableBackupSections(
                appPreferences = appPrefs,
                sourcePreferences = sourcePrefs,
            ),
            summary = BackupCompatibilitySummary(decisions),
        )
    }

    private fun List<BackupPreference>.filterRestorablePreferences(
        existingPreferences: Map<String, *>,
        section: String,
        includePrivatePreferences: Boolean,
        decisions: MutableList<BackupCompatibilityDecision>,
    ): List<BackupPreference> {
        return mapNotNull { preference ->
            val translatedKey = KEY_TRANSLATIONS[preference.key] ?: preference.key
            val translatedPreference = if (translatedKey == preference.key) {
                preference
            } else {
                preference.copy(key = translatedKey)
            }
            val existingValue = existingPreferences[translatedKey]
            when {
                preference.key.isDeniedRestoreKey() || translatedKey.isDeniedRestoreKey() -> {
                    decisions += preference.ignoredDecision(
                        section = section,
                        reason = "Preference key is server-owned, app-state, or has changed meaning in Amatsubu.",
                    )
                    null
                }
                !includePrivatePreferences &&
                    (Preference.isPrivate(preference.key) || Preference.isPrivate(translatedKey)) -> {
                    decisions += preference.ignoredDecision(
                        section = section,
                        reason = "Private settings restore option is disabled.",
                    )
                    null
                }
                !existingPreferences.containsKey(translatedKey) -> {
                    decisions += preference.unsupportedDecision(
                        section = section,
                        reason = "Preference key is not present in Amatsubu.",
                    )
                    null
                }
                !translatedPreference.value.matchesType(existingValue) -> {
                    decisions += preference.unsupportedDecision(
                        section = section,
                        reason = "Preference value type does not match the existing Amatsubu value.",
                    )
                    null
                }
                translatedKey != preference.key -> {
                    decisions += BackupCompatibilityDecision(
                        section = "$section:${preference.key}->$translatedKey",
                        decision = BackupCompatibilityDecisionType.TRANSLATE_THEN_RESTORE,
                        reason = "Preference key has the same meaning under the Amatsubu key.",
                    )
                    translatedPreference
                }
                else -> {
                    decisions += BackupCompatibilityDecision(
                        section = "$section:$translatedKey",
                        decision = BackupCompatibilityDecisionType.RESTORE_DIRECT,
                        reason = "Preference key exists with the same value type.",
                    )
                    translatedPreference
                }
            }
        }
    }

    private fun recordServerOwnedSections(
        backup: Backup,
        decisions: MutableList<BackupCompatibilityDecision>,
    ) {
        decisions.recordIgnored(
            section = "backupManga",
            count = backup.backupManga.size,
            reason = "Suwayomi owns library manga data; client restore does not import library entries.",
        )
        decisions.recordIgnored(
            section = "backupCategories",
            count = backup.backupCategories.size,
            reason = "Suwayomi owns library categories; client restore does not import categories.",
        )
        decisions.recordIgnored(
            section = "backupSources",
            count = backup.backupSources.size,
            reason = "Source catalog metadata is server/cache-derived in Amatsubu.",
        )
        decisions.recordIgnored(
            section = "backupManga.chapters",
            count = backup.backupManga.sumOf { it.chapters.size },
            reason = "Suwayomi owns chapter rows and read progress for server-backed manga.",
        )
        decisions.recordIgnored(
            section = "backupManga.categories",
            count = backup.backupManga.sumOf { it.categories.size },
            reason = "Manga category membership belongs to the Suwayomi library.",
        )
        decisions.recordIgnored(
            section = "backupManga.history",
            count = backup.backupManga.sumOf { it.history.size },
            reason = "Suwayomi owns reading history for server-backed manga.",
        )
        decisions.recordIgnored(
            section = "backupManga.tracking",
            count = backup.backupManga.sumOf { it.tracking.size },
            reason = "Tracking records are server-owned in Amatsubu.",
        )
    }

    private fun MutableList<BackupCompatibilityDecision>.recordIgnored(
        section: String,
        count: Int,
        reason: String,
    ) {
        if (count > 0) {
            this += BackupCompatibilityDecision(
                section = section,
                decision = BackupCompatibilityDecisionType.IGNORE_RECORDED,
                reason = reason,
                count = count,
            )
        }
    }

    private fun BackupPreference.unsupportedDecision(
        section: String,
        reason: String,
    ) = BackupCompatibilityDecision(
        section = "$section:$key",
        decision = BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED,
        reason = reason,
    )

    private fun BackupPreference.ignoredDecision(
        section: String,
        reason: String,
    ) = BackupCompatibilityDecision(
        section = "$section:$key",
        decision = BackupCompatibilityDecisionType.IGNORE_RECORDED,
        reason = reason,
    )

    private fun PreferenceValue.matchesType(existingValue: Any?): Boolean {
        return when (this) {
            is IntPreferenceValue -> existingValue is Int
            is LongPreferenceValue -> existingValue is Long
            is FloatPreferenceValue -> existingValue is Float
            is StringPreferenceValue -> existingValue is String
            is BooleanPreferenceValue -> existingValue is Boolean
            is StringSetPreferenceValue -> existingValue is Set<*> && existingValue.all { it is String }
        }
    }

    companion object {
        private const val SECTION_APP_PREFERENCES = "backupPreferences"
        private const val SECTION_SOURCE_PREFERENCES = "backupSourcePreferences"

        private val KEY_TRANSLATIONS = emptyMap<String, String>()

        private val deniedKeys = setOf(
            "backup_interval",
            "backup_slots",
            "backup_location",
            "download_new",
            "download_new_categories",
            "download_new_categories_exclude",
            "download_only_over_wifi",
            "remove_after_marked_as_read",
            "remove_after_read_slots",
            "save_chapter_as_cbz",
            "split_tall_images",
        )

        private val deniedPrefixes = listOf(
            "track_",
            "download_queue",
        )

        private fun String.isDeniedRestoreKey(): Boolean {
            return this in deniedKeys ||
                deniedPrefixes.any { startsWith(it) } ||
                Preference.isAppState(this)
        }
    }
}

data class BackupCompatibilityResult(
    val restorable: RestorableBackupSections,
    val summary: BackupCompatibilitySummary,
)

data class RestorableBackupSections(
    val appPreferences: List<BackupPreference>,
    val sourcePreferences: List<BackupSourcePreferences>,
)

data class BackupCompatibilitySummary(
    val decisions: List<BackupCompatibilityDecision>,
) {
    val restoredCount: Int
        get() = decisions.count {
            it.decision == BackupCompatibilityDecisionType.RESTORE_DIRECT ||
                it.decision == BackupCompatibilityDecisionType.TRANSLATE_THEN_RESTORE
        }

    val ignoredCount: Int
        get() = decisions
            .filter {
                it.decision != BackupCompatibilityDecisionType.RESTORE_DIRECT &&
                    it.decision != BackupCompatibilityDecisionType.TRANSLATE_THEN_RESTORE
            }
            .sumOf { it.count }

    val unsupportedCount: Int
        get() = decisions
            .filter { it.decision == BackupCompatibilityDecisionType.UNSUPPORTED_RECORDED }
            .sumOf { it.count }
}

data class BackupCompatibilityDecision(
    val section: String,
    val decision: BackupCompatibilityDecisionType,
    val reason: String,
    val count: Int = 1,
)

enum class BackupCompatibilityDecisionType {
    RESTORE_DIRECT,
    TRANSLATE_THEN_RESTORE,
    IGNORE_RECORDED,
    UNSUPPORTED_RECORDED,
}
