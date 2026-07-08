package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences

class CategoryPreferencesCleanupMigration : Migration {
    override val version: Float = 10f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        return@withIOContext true
    }
}
