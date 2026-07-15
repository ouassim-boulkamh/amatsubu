package eu.kanade.amatsubu.migration.migrations

import eu.kanade.amatsubu.migration.Migration
import eu.kanade.amatsubu.migration.MigrationContext

class VerticalNavigatorMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.preferenceStore ?: return false
        val oldNavigator = preferenceStore.getBoolean("pref_webtoon_vertical_navigator", true)
        val newNavigator = preferenceStore.getStringSet("pref_vertical_navigator", emptySet())
        val oldNavigatorOnLeft = preferenceStore.getBoolean("pref_webtoon_vertical_navigator_on_left", false)
        val newNavigatorOnLeft = preferenceStore.getBoolean("pref_vertical_navigator_on_left", false)

        if (!newNavigator.isSet()) {
            if (oldNavigator.get()) newNavigator.set(setOf("WEBTOON", "CONTINUOUS_VERTICAL"))
            if (oldNavigator.isSet()) oldNavigator.delete()
        }
        if (oldNavigatorOnLeft.isSet()) {
            newNavigatorOnLeft.set(oldNavigatorOnLeft.get())
            oldNavigatorOnLeft.delete()
        }
        return true
    }
}
