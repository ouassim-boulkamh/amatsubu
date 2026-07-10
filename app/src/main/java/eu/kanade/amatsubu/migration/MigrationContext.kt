package eu.kanade.amatsubu.migration

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.library.service.LibraryPreferences

class MigrationContext(
    val dryrun: Boolean,
    val application: Application? = null,
    val basePreferences: BasePreferences? = null,
    val libraryPreferences: LibraryPreferences? = null,
) {
    fun copyWithDryRun(dryrun: Boolean) = MigrationContext(
        dryrun = dryrun,
        application = application,
        basePreferences = basePreferences,
        libraryPreferences = libraryPreferences,
    )
}
