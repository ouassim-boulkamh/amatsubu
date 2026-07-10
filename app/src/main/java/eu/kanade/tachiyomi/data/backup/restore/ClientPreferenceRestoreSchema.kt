package eu.kanade.tachiyomi.data.backup.restore

/**
 * SharedPreferences only reports keys that have been explicitly written. Backup
 * restore still needs to accept known Amatsubu preferences that are currently at
 * their default value and therefore absent from getAll().
 */
object ClientPreferenceRestoreSchema {

    /**
     * Restore allowlist for client-owned app settings. Server-owned backup
     * sections are handled by [BackupCompatibilityPolicy] and must stay ignored
     * instead of gaining Android-local restore authority.
     */
    val defaultValues: Map<String, Any> = mapOf(
        // Library display and filters
        "display_download_badge" to false,
        "display_local_download_badge" to false,
        "display_unread_badge" to true,
        "display_local_badge" to true,
        "display_language_badge" to false,
        "library_show_updates_count" to true,
        "library_sorting_mode" to "ALPHABETICAL,ASCENDING",
        "pref_display_mode_library" to "COMPACT_GRID",
        "pref_filter_library_downloaded_v2" to "DISABLED",
        "pref_filter_library_unread_v2" to "DISABLED",
        "pref_filter_library_started_v2" to "DISABLED",
        "pref_filter_library_bookmarked_v2" to "DISABLED",
        "pref_filter_library_completed_v2" to "DISABLED",
        "pref_filter_library_interval_custom" to "DISABLED",
        "display_continue_reading_button" to false,
        "display_category_tabs" to true,
        "display_number_of_items" to false,
        "categorized_display" to false,

        // Browse/source presentation
        "browse_hide_in_library_items" to false,
        "incognito_extensions" to emptySet<String>(),
        "pinned_catalogues" to emptySet<String>(),
        "source_languages" to emptySet<String>(),
        "show_nsfw_source" to true,

        // Reader and security/privacy options seen in Mihon/Amatsubu backups
        "reader_navigation_overlay_on_start" to false,
        "reader_navigation_overlay_new_user" to true,
        "landscape_zoom" to false,
        "use_biometric_lock" to false,
        "hide_notification_content" to false,

        // Suwayomi connection settings
        "amatsubu_server_url" to "http://127.0.0.1:4567",
        "amatsubu_server_port_enabled" to false,
        "amatsubu_server_port" to 4567,
        "amatsubu_server_auth_type" to "none",
        "amatsubu_server_username" to "",
        "amatsubu_server_password" to "",
        "amatsubu_server_timeout_seconds" to 30,

        // Misc client settings retained by Amatsubu
        "pref_hardware_bitmap_threshold" to 0,
        "ext_updates_count" to 0,
        "migration_flags" to 0,
    )

    fun defaultsWith(dynamicKeys: Iterable<String>): Map<String, Any> {
        val dynamicDefaults = dynamicKeys
            .filter { it.startsWith("pref_filter_library_tracked_") && it.endsWith("_v2") }
            .associateWith { "DISABLED" }

        return defaultValues + dynamicDefaults
    }
}
