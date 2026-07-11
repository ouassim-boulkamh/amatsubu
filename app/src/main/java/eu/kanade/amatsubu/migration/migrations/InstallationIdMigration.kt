package eu.kanade.amatsubu.migration.migrations

import eu.kanade.amatsubu.migration.Migration
import eu.kanade.amatsubu.migration.MigrationContext
import eu.kanade.domain.base.BasePreferences
import mihon.core.common.FeatureFlags
import kotlin.uuid.ExperimentalUuidApi

class InstallationIdMigration : Migration {
    override val version: Float = Migration.ALWAYS

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val installationId = migrationContext.basePreferences?.installationId ?: return false
        if (!installationId.isSet()) installationId.set(FeatureFlags.newInstallationId())
        return true
    }
}
