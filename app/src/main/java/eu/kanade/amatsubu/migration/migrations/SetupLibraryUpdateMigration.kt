package eu.kanade.amatsubu.migration.migrations

import eu.kanade.amatsubu.migration.Migration
import eu.kanade.amatsubu.migration.MigrationContext

class SetupLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        return migrationContext.application != null
    }
}
