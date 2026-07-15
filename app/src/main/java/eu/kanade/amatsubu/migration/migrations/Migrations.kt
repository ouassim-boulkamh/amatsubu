package eu.kanade.amatsubu.migration.migrations

import eu.kanade.amatsubu.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupLibraryUpdateMigration(),
        CategoryPreferencesCleanupMigration(),
        InstallationIdMigration(),
        VerticalNavigatorMigration(),
    )
