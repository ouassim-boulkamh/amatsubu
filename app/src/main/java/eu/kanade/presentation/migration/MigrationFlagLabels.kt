package eu.kanade.presentation.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.migration.model.MigrationFlag
import tachiyomi.i18n.MR

fun MigrationFlag.getLabel(): StringResource {
    return when (this) {
        MigrationFlag.CHAPTER -> MR.strings.chapters
        MigrationFlag.CATEGORY -> MR.strings.categories
        MigrationFlag.NOTES -> MR.strings.action_notes
    }
}
