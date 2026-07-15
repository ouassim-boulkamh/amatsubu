package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class PreferenceBackupCreator(
    private val preferenceStore: PreferenceStore,
) {

    fun createApp(includePrivatePreferences: Boolean): List<BackupPreference> {
        val storedPreferences = preferenceStore.getAll()
        val preferencesWithLiveNotificationDefaults = LIVE_SERVER_NOTIFICATION_DEFAULTS + storedPreferences

        return preferencesWithLiveNotificationDefaults
            .toBackupPreferences()
            .withPrivatePreferences(includePrivatePreferences)
    }

    fun createSource(includePrivatePreferences: Boolean): List<BackupSourcePreferences> {
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return filterKeys { !Preference.isAppState(it) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> -> (value as? Set<String>)?.let {
                        BackupPreference(key, StringSetPreferenceValue(it))
                    }
                    else -> null
                }
            }
    }

    private fun List<BackupPreference>.withPrivatePreferences(include: Boolean): List<BackupPreference> {
        return if (include) {
            this
        } else {
            filter { !Preference.isPrivate(it.key) }
        }
    }

    private companion object {
        // Defaults are not written to SharedPreferences, but this user-facing
        // setting must round-trip through backups even before it is toggled.
        val LIVE_SERVER_NOTIFICATION_DEFAULTS = mapOf(
            LIVE_SERVER_NOTIFICATIONS_KEY to true,
            SHOW_SERVER_ADDRESS_IN_LIVE_NOTIFICATION_KEY to true,
        )
        const val LIVE_SERVER_NOTIFICATIONS_KEY = "amatsubu_live_server_notifications"
        const val SHOW_SERVER_ADDRESS_IN_LIVE_NOTIFICATION_KEY = "amatsubu_show_server_address_in_live_notification"
    }
}
