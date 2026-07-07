package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val appSettings: Boolean = false,
    val sourceSettings: Boolean = false,
    val privateSettings: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        appSettings,
        sourceSettings,
        privateSettings,
    )

    fun canRestore() = appSettings || sourceSettings

    companion object {
        val options = listOf(
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

        fun fromBooleanArray(array: BooleanArray) = RestoreOptions(
            libraryEntries = array[0],
            categories = array[1],
            appSettings = array[2],
            sourceSettings = array[3],
            privateSettings = array.getOrElse(4) { false },
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
        val enabled: (RestoreOptions) -> Boolean = { true },
    )
}
