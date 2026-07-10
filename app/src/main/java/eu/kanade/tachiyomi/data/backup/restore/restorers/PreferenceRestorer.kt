package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import logcat.LogPriority
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat

class PreferenceRestorer(
    context: Context?,
    private val preferenceStore: PreferenceStore,
    private val sourcePreferenceStoreFactory: (String) -> PreferenceStore = { sourceKey ->
        val appContext = checkNotNull(context)
        AndroidPreferenceStore(
            appContext,
            appContext.getSharedPreferences(restoredSourcePreferenceFileName(sourceKey), Context.MODE_PRIVATE),
        )
    },
) {

    fun restoreApp(
        preferences: List<BackupPreference>,
        defaultValues: Map<String, Any> = emptyMap(),
    ): PreferenceRestoreResult {
        return restorePreferences(preferences, preferenceStore, defaultValues)
    }

    fun restoreSource(preferences: List<BackupSourcePreferences>): PreferenceRestoreResult {
        return preferences
            .map { restorePreferences(it.prefs, sourcePreferenceStoreFactory(it.sourceKey)) }
            .fold(PreferenceRestoreResult()) { total, result -> total + result }
    }

    private fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        defaultValues: Map<String, Any> = emptyMap(),
    ): PreferenceRestoreResult {
        val existingPreferences = defaultValues + preferenceStore.getAll()
        var restored = 0
        var failed = 0

        toRestore.forEach { preference ->
            try {
                if (preference.restoreTo(preferenceStore, existingPreferences)) {
                    restored++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.ERROR, e) { "Failed to restore preference <${preference.key}>" }
            }
        }

        return PreferenceRestoreResult(restored = restored, failed = failed)
    }

    private fun BackupPreference.restoreTo(
        preferenceStore: PreferenceStore,
        existingPreferences: Map<String, *>,
    ): Boolean {
        if (!existingPreferences.containsKey(key)) return false

        return when (val restoreValue = value) {
            is IntPreferenceValue -> {
                if (existingPreferences[key] !is Int) return false
                preferenceStore.getInt(key).set(restoreValue.value)
                true
            }
            is LongPreferenceValue -> {
                if (existingPreferences[key] !is Long) return false
                preferenceStore.getLong(key).set(restoreValue.value)
                true
            }
            is FloatPreferenceValue -> {
                if (existingPreferences[key] !is Float) return false
                preferenceStore.getFloat(key).set(restoreValue.value)
                true
            }
            is StringPreferenceValue -> {
                if (existingPreferences[key] !is String) return false
                preferenceStore.getString(key).set(restoreValue.value)
                true
            }
            is BooleanPreferenceValue -> {
                if (existingPreferences[key] !is Boolean) return false
                preferenceStore.getBoolean(key).set(restoreValue.value)
                true
            }
            is StringSetPreferenceValue -> {
                val existingValue = existingPreferences[key]
                if (existingValue !is Set<*> || !existingValue.all { it is String }) return false
                preferenceStore.getStringSet(key).set(restoreValue.value)
                true
            }
        }
    }
}

internal fun restoredSourcePreferenceFileName(sourceKey: String): String = sourceKey

data class PreferenceRestoreResult(
    val restored: Int = 0,
    val failed: Int = 0,
) {
    operator fun plus(other: PreferenceRestoreResult): PreferenceRestoreResult {
        return PreferenceRestoreResult(
            restored = restored + other.restored,
            failed = failed + other.failed,
        )
    }
}
