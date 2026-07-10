package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ServerStateSyncTest {

    @Test
    fun `typed refresh emits typed invalidation`() = runTest {
        val invalidation = async {
            withTimeout(1_000) { ServerStateSync.invalidations.first() }
        }
        yield()

        ServerStateSync.requestRefresh(
            ServerStateEntity.Library,
            ServerStateEntity.Manga(10),
        )

        assertEquals(
            ServerStateInvalidation(
                setOf(
                    ServerStateEntity.Library,
                    ServerStateEntity.Manga(10),
                ),
            ),
            invalidation.await(),
        )
    }

    @Test
    fun `legacy refresh does not emit typed invalidation`() = runTest {
        ServerStateSync.requestRefresh()

        val timeout = runCatching {
            withTimeout(100) { ServerStateSync.invalidations.first() }
        }.exceptionOrNull()
        assertInstanceOf(TimeoutCancellationException::class.java, timeout)
    }

    @Test
    fun `typed invalidation matches watched entity families`() {
        val invalidation = ServerStateInvalidation(
            serverUpdatesDownloadAffectedEntities(listOf(12)),
        )

        assertTrue(invalidation.affectsAny(ServerStateEntity.Updates))
        assertTrue(invalidation.affectsAny(ServerStateEntity.Downloads))
        assertFalse(invalidation.affectsAny(ServerStateEntity.History))
        assertFalse(invalidation.affectsAny(ServerStateEntity.Manga(13)))
    }

    @Test
    fun `production refresh call sites do not use legacy no argument refresh`() {
        val sourceRoot = projectRoot().resolve("app/src/main/java")
        val legacyCall = Regex("""ServerStateSync\.requestRefresh\(\s*\)""")
        val offenders = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .forEach { path ->
                    val text = Files.readString(path)
                    legacyCall.findAll(text).forEach { match ->
                        offenders += "${sourceRoot.relativize(path)}:${lineNumber(text, match.range.first)}"
                    }
                }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `production refresh producers use named affected entity helpers`() {
        val sourceRoot = projectRoot().resolve("app/src/main/java")
        val literalEntityCall = Regex("""ServerStateSync\.requestRefresh\(\s*ServerStateEntity\.""")
        val offenders = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .forEach { path ->
                    val text = Files.readString(path)
                    literalEntityCall.findAll(text).forEach { match ->
                        offenders += "${sourceRoot.relativize(path)}:${lineNumber(text, match.range.first)}"
                    }
                }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `visible consumers collect typed invalidations`() {
        val sourceRoot = projectRoot().resolve("app/src/main/java")
        val migratedConsumers = listOf(
            "eu/kanade/tachiyomi/ui/updates/UpdatesScreenModel.kt",
            "eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt",
            "eu/kanade/tachiyomi/ui/history/HistoryScreenModel.kt",
            "eu/kanade/tachiyomi/ui/download/DownloadQueueScreenModel.kt",
            "eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt",
            "eu/kanade/tachiyomi/ui/stats/StatsScreenModel.kt",
            "eu/kanade/tachiyomi/ui/browse/ServerSourcePreferencesScreen.kt",
            "eu/kanade/tachiyomi/ui/browse/BrowseTab.kt",
            "eu/kanade/tachiyomi/ui/manga/track/ServerTrackInfoDialogScreen.kt",
            "eu/kanade/presentation/more/settings/screen/SettingsServerScreen.kt",
            "eu/kanade/tachiyomi/data/suwayomi/SuwayomiUpdatesWidgetDataSource.kt",
        )

        migratedConsumers.forEach { relativePath ->
            val text = Files.readString(sourceRoot.resolve(relativePath))

            assertTrue(
                text.contains("ServerStateSync.invalidations"),
                "$relativePath should consume typed invalidations",
            )
            assertFalse(
                text.contains("ServerStateSync.refreshes"),
                "$relativePath should not depend on the retired broad refresh stream",
            )
        }
    }

    @Test
    fun `download queue mutations invalidate downloads`() {
        assertEquals(
            setOf(ServerStateEntity.Downloads),
            serverDownloadQueueAffectedEntities(),
        )
    }

    @Test
    fun `manga library and category mutations invalidate library category and manga state`() {
        val affected = setOf(
            ServerStateEntity.Library,
            ServerStateEntity.Categories,
            ServerStateEntity.Manga(12),
        )

        assertEquals(affected, serverMangaLibraryAffectedEntities(12))
        assertEquals(affected, serverMangaCategoryAffectedEntities(12))
    }

    @Test
    fun `chapter read mutations invalidate visible read progress families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
                ServerStateEntity.Trackers(12),
            ),
            serverChapterReadAffectedEntities(12),
        )
    }

    @Test
    fun `chapter bookmark mutations invalidate manga and chapter state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
            ),
            serverChapterBookmarkAffectedEntities(12),
        )
    }

    @Test
    fun `chapter download mutations invalidate downloads manga and chapter state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Downloads,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
            ),
            serverChapterDownloadAffectedEntities(12),
        )
    }

    @Test
    fun `manga settings mutations invalidate manga state`() {
        assertEquals(
            setOf(ServerStateEntity.Manga(12)),
            serverMangaSettingsAffectedEntities(12),
        )
    }

    @Test
    fun `library download mutations invalidate library downloads manga and chapter state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Downloads,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
                ServerStateEntity.Manga(13),
                ServerStateEntity.Chapters(13),
            ),
            serverLibraryDownloadAffectedEntities(listOf(12, 13)),
        )
    }

    @Test
    fun `library read mutations invalidate visible read progress families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
                ServerStateEntity.Trackers(12),
                ServerStateEntity.Manga(13),
                ServerStateEntity.Chapters(13),
                ServerStateEntity.Trackers(13),
            ),
            serverLibraryReadAffectedEntities(listOf(12, 13)),
        )
    }

    @Test
    fun `library read mutations still invalidate read progress families without manga ids`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
            ),
            serverLibraryReadAffectedEntities(emptyList()),
        )
    }

    @Test
    fun `library removal and category mutations invalidate library categories and manga state`() {
        val affected = setOf(
            ServerStateEntity.Library,
            ServerStateEntity.Categories,
            ServerStateEntity.Manga(12),
            ServerStateEntity.Manga(13),
        )

        assertEquals(affected, serverLibraryMangaRemovalAffectedEntities(listOf(12, 13)))
        assertEquals(affected, serverLibraryCategoryAffectedEntities(listOf(12, 13)))
    }

    @Test
    fun `library update mutations invalidate library and updates state`() {
        assertEquals(
            setOf(ServerStateEntity.Library, ServerStateEntity.Updates),
            serverLibraryUpdateAffectedEntities(),
        )
    }

    @Test
    fun `manual history refresh invalidates history state`() {
        assertEquals(
            setOf(ServerStateEntity.History),
            serverHistoryRefreshAffectedEntities(),
        )
    }

    @Test
    fun `notification mutations invalidate notification and affected server families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.Notifications,
            ),
            serverNotificationLibraryUpdateAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Downloads,
                ServerStateEntity.Notifications,
            ),
            serverNotificationDownloadAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Categories,
                ServerStateEntity.Downloads,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
                ServerStateEntity.Notifications,
            ),
            serverNotificationSyncAffectedEntities(),
        )
    }

    @Test
    fun `backup restore invalidates broad server restore families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Categories,
                ServerStateEntity.Downloads,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
                ServerStateEntity.ServerBackup,
                ServerStateEntity.ServerSettings,
            ),
            serverBackupRestoreAffectedEntities(),
        )
    }

    @Test
    fun `settings and browse mutations invalidate contract-specific families`() {
        assertEquals(
            setOf(ServerStateEntity.ServerSettings),
            serverSettingsAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Sources,
                ServerStateEntity.Extensions,
                ServerStateEntity.ExtensionStores,
            ),
            serverBrowseRefreshAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Sources,
                ServerStateEntity.Extensions,
                ServerStateEntity.ExtensionStores,
            ),
            serverExtensionActionAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Sources,
                ServerStateEntity.Extensions,
                ServerStateEntity.ExtensionStores,
            ),
            serverExtensionStoreAffectedEntities(),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Sources,
                ServerStateEntity.SourcePreferences("source-a"),
            ),
            serverSourcePreferenceAffectedEntities("source-a"),
        )
    }

    @Test
    fun `notes and tracker mutations invalidate manga-scoped families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Manga(12),
                ServerStateEntity.Notes(12),
            ),
            serverMangaNotesAffectedEntities(12),
        )
        assertEquals(
            setOf(
                ServerStateEntity.Manga(12),
                ServerStateEntity.Trackers(12),
            ),
            serverTrackAffectedEntities(12),
        )
    }

    @Test
    fun `updates read mutations include updates and visible read progress families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Updates,
                ServerStateEntity.Library,
                ServerStateEntity.History,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
                ServerStateEntity.Trackers(12),
            ),
            serverUpdatesReadAffectedEntities(listOf(12)),
        )
    }

    @Test
    fun `updates bookmark mutations include updates manga and chapter state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Updates,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
            ),
            serverUpdatesBookmarkAffectedEntities(listOf(12)),
        )
    }

    @Test
    fun `updates download mutations include updates downloads manga and chapter state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Updates,
                ServerStateEntity.Downloads,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
            ),
            serverUpdatesDownloadAffectedEntities(listOf(12)),
        )
    }

    @Test
    fun `history read mutations include history and visible read progress families`() {
        assertEquals(
            setOf(
                ServerStateEntity.History,
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.Manga(12),
                ServerStateEntity.Chapters(12),
                ServerStateEntity.Trackers(12),
            ),
            serverHistoryReadAffectedEntities(listOf(12)),
        )
    }

    @Test
    fun `history clear mutations invalidate global read progress families`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Updates,
                ServerStateEntity.History,
            ),
            serverHistoryClearAffectedEntities(),
        )
    }

    @Test
    fun `history library mutations invalidate library categories history and manga state`() {
        assertEquals(
            setOf(
                ServerStateEntity.Library,
                ServerStateEntity.Categories,
                ServerStateEntity.History,
                ServerStateEntity.Manga(12),
            ),
            serverHistoryLibraryAffectedEntities(12),
        )
    }

    private fun projectRoot(): Path {
        var current: Path? = Paths.get("").toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Could not find project root")
    }

    private fun lineNumber(text: String, offset: Int): Int {
        return text.substring(0, offset).count { it == '\n' } + 1
    }
}
