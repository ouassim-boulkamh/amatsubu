package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = false,
    val history: Boolean = false,
    val readEntries: Boolean = false,
    val appSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        readEntries,
        appSettings,
        sourceSettings,
        privateSettings,
    )

    fun canCreate() = appSettings || sourceSettings

    companion object {
        val libraryOptions = emptyList<Entry>()

        val settingsOptions = listOf(
            Entry(
                label = MR.strings.app_settings,
                getter = { it.appSettings },
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = { it.sourceSettings },
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = { it.privateSettings },
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array[0],
            categories = array[1],
            chapters = array[2],
            tracking = array[3],
            history = array[4],
            readEntries = array[5],
            appSettings = array[6],
            sourceSettings = array[7],
            privateSettings = array[8],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
