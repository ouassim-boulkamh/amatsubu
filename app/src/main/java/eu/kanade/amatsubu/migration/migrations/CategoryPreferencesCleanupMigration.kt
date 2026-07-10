package eu.kanade.amatsubu.migration.migrations

import eu.kanade.amatsubu.migration.Migration
import eu.kanade.amatsubu.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class CategoryPreferencesCleanupMigration : Migration {
    override val version: Float = 10f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        migrationContext.libraryPreferences ?: return@withIOContext false
        return@withIOContext true
    }
}
