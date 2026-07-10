package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ServerStateSync {
    private val _invalidations = MutableSharedFlow<ServerStateInvalidation>(extraBufferCapacity = 1)

    val invalidations: Flow<ServerStateInvalidation> = _invalidations.asSharedFlow()

    fun requestRefresh(vararg affected: ServerStateEntity) {
        if (affected.isNotEmpty()) {
            _invalidations.tryEmit(ServerStateInvalidation(affected.toSet()))
        }
    }
}

data class ServerStateInvalidation(
    val affected: Set<ServerStateEntity>,
) {
    fun affectsAny(vararg entities: ServerStateEntity): Boolean {
        return entities.any { it in affected }
    }
}

sealed interface ServerStateEntity {
    data object Library : ServerStateEntity
    data object Categories : ServerStateEntity
    data object Downloads : ServerStateEntity
    data object Updates : ServerStateEntity
    data object History : ServerStateEntity
    data object Notifications : ServerStateEntity
    data object ServerSettings : ServerStateEntity
    data object ServerBackup : ServerStateEntity
    data object Sources : ServerStateEntity
    data object Extensions : ServerStateEntity
    data object ExtensionStores : ServerStateEntity
    data class Manga(val id: Int) : ServerStateEntity
    data class Chapters(val mangaId: Int) : ServerStateEntity
    data class Trackers(val mangaId: Int) : ServerStateEntity
    data class SourcePreferences(val sourceId: String) : ServerStateEntity
    data class Notes(val mangaId: Int) : ServerStateEntity
}

fun serverDownloadQueueAffectedEntities(): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Downloads)
}

fun serverMangaLibraryAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.Manga(mangaId),
    )
}

fun serverMangaCategoryAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.Manga(mangaId),
    )
}

fun serverChapterReadAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Chapters(mangaId),
        ServerStateEntity.Trackers(mangaId),
    )
}

fun serverChapterBookmarkAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Chapters(mangaId),
    )
}

fun serverChapterDownloadAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Downloads,
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Chapters(mangaId),
    )
}

fun serverMangaSettingsAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Manga(mangaId))
}

fun serverLibraryDownloadAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Library, ServerStateEntity.Downloads) +
        mangaIds.flatMap { mangaId ->
            listOf(
                ServerStateEntity.Manga(mangaId),
                ServerStateEntity.Chapters(mangaId),
            )
        }
}

fun serverLibraryReadAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
    ) + mangaIds.flatMap { serverChapterReadAffectedEntities(it) }
}

fun serverLibraryMangaRemovalAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Library, ServerStateEntity.Categories) +
        mangaIds.map { ServerStateEntity.Manga(it) }
}

fun serverLibraryCategoryAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Library, ServerStateEntity.Categories) +
        mangaIds.map { ServerStateEntity.Manga(it) }
}

fun serverLibraryUpdateAffectedEntities(): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Library, ServerStateEntity.Updates)
}

fun serverHistoryRefreshAffectedEntities(): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.History)
}

fun serverNotificationLibraryUpdateAffectedEntities(): Set<ServerStateEntity> {
    return serverLibraryUpdateAffectedEntities() + ServerStateEntity.Notifications
}

fun serverNotificationDownloadAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Downloads,
        ServerStateEntity.Notifications,
    )
}

fun serverNotificationSyncAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.Downloads,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
        ServerStateEntity.Notifications,
    )
}

fun serverBackupRestoreAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.Downloads,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
        ServerStateEntity.ServerBackup,
        ServerStateEntity.ServerSettings,
    )
}

fun serverSettingsAffectedEntities(): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.ServerSettings)
}

fun serverBrowseRefreshAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Sources,
        ServerStateEntity.Extensions,
        ServerStateEntity.ExtensionStores,
    )
}

fun serverExtensionActionAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Sources,
        ServerStateEntity.Extensions,
        ServerStateEntity.ExtensionStores,
    )
}

fun serverExtensionStoreAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Sources,
        ServerStateEntity.Extensions,
        ServerStateEntity.ExtensionStores,
    )
}

fun serverSourcePreferenceAffectedEntities(sourceId: String): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Sources,
        ServerStateEntity.SourcePreferences(sourceId),
    )
}

fun serverMangaNotesAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Notes(mangaId),
    )
}

fun serverTrackAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Trackers(mangaId),
    )
}

fun serverUpdatesReadAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Updates) + serverLibraryReadAffectedEntities(mangaIds)
}

fun serverUpdatesBookmarkAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Updates) +
        mangaIds.flatMap { serverChapterBookmarkAffectedEntities(it) }
}

fun serverUpdatesDownloadAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Updates, ServerStateEntity.Downloads) +
        mangaIds.flatMap { mangaId ->
            listOf(
                ServerStateEntity.Manga(mangaId),
                ServerStateEntity.Chapters(mangaId),
            )
        }
}

fun serverHistoryReadAffectedEntities(mangaIds: Collection<Int>): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.History) + serverLibraryReadAffectedEntities(mangaIds)
}

fun serverHistoryClearAffectedEntities(): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
    )
}

fun serverHistoryLibraryAffectedEntities(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Categories,
        ServerStateEntity.History,
        ServerStateEntity.Manga(mangaId),
    )
}
