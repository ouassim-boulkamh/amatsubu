package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerWorkflowStatePropertyTest {

    @Test
    suspend fun `generated library update action sequences invalidate every affected visible family`() {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.list(Arb.int(1..10_000), 0..32),
        ) { mangaIds ->
            val distinctIds = mangaIds.distinct()

            assertEquals(
                setOf(ServerStateEntity.Library, ServerStateEntity.Downloads) +
                    distinctIds.flatMap { id -> listOf(ServerStateEntity.Manga(id), ServerStateEntity.Chapters(id)) },
                serverLibraryDownloadAffectedEntities(mangaIds),
            )
            assertEquals(
                setOf(ServerStateEntity.Library, ServerStateEntity.Categories) +
                    distinctIds.map(ServerStateEntity::Manga),
                serverLibraryCategoryAffectedEntities(mangaIds),
            )
            assertEquals(
                setOf(ServerStateEntity.Updates, ServerStateEntity.Downloads) +
                    distinctIds.flatMap { id -> listOf(ServerStateEntity.Manga(id), ServerStateEntity.Chapters(id)) },
                serverUpdatesDownloadAffectedEntities(mangaIds),
            )

            val readAffected = serverLibraryReadAffectedEntities(mangaIds)
            assertTrue(
                readAffected.containsAll(
                    setOf(ServerStateEntity.Library, ServerStateEntity.Updates, ServerStateEntity.History),
                ),
            )
            distinctIds.forEach { id ->
                assertTrue(readAffected.containsAll(readFamilies(id)))
            }
        }
    }

    @Test
    suspend fun `generated detail mutations preserve their manga identity and never invent another one`() {
        checkAll(FuzzTestConfig.caseCount(), Arb.int(1..10_000)) { mangaId ->
            assertEquals(
                setOf(ServerStateEntity.Library, ServerStateEntity.Categories, ServerStateEntity.Manga(mangaId)),
                serverMangaLibraryAffectedEntities(mangaId),
            )
            assertEquals(
                setOf(ServerStateEntity.Manga(mangaId), ServerStateEntity.Chapters(mangaId)),
                serverChapterBookmarkAffectedEntities(mangaId),
            )
            assertEquals(
                setOf(ServerStateEntity.Manga(mangaId), ServerStateEntity.Notes(mangaId)),
                serverMangaNotesAffectedEntities(mangaId),
            )
            assertEquals(
                setOf(ServerStateEntity.Manga(mangaId), ServerStateEntity.Trackers(mangaId)),
                serverTrackAffectedEntities(mangaId),
            )
        }
    }

    @Test
    fun `generated reader intent replays apply only unchanged server baselines`() = runTest {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.list(intentScenarioArb, 1..32),
        ) { scenarios ->
            val pending = scenarios.mapIndexed { index, scenario -> scenario.intent(index) }
            val pushed = mutableListOf<PendingServerReaderIntent>()
            val deleted = mutableListOf<PendingServerReaderIntent>()

            val result = replayPendingReaderIntents(
                pending = pending,
                currentBaseline = { intent -> scenarios[intent.chapterId].currentBaseline },
                updateProgress = { intent -> pushed += intent },
                updateBookmark = { intent -> pushed += intent },
                deletePendingIntent = { intent -> deleted += intent },
            )

            val accepted = pending.filterIndexed { index, intent ->
                val current = scenarios[index].currentBaseline
                when (intent.type) {
                    PendingServerReaderIntentType.PROGRESS -> {
                        current.isRead == intent.baseline.isRead &&
                            current.lastPageRead == intent.baseline.lastPageRead
                    }
                    PendingServerReaderIntentType.BOOKMARK -> current.isBookmarked == intent.baseline.isBookmarked
                }
            }
            val conflicted = pending.filter { it !in accepted }
            assertEquals(accepted.size, result.pushed)
            assertEquals(conflicted, result.conflicted)
            assertEquals(accepted, pushed)
            assertEquals(accepted, deleted)
        }
    }

    @Test
    suspend fun `generated workflow transition histories refresh affected families`() {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.list(workflowActionArb, 1..80),
        ) { history ->
            val watchedEntities = workflowWatchedEntities()
            val serverVersions = watchedEntities.associateWith { 0 }.toMutableMap()
            val visibleVersions = serverVersions.toMutableMap()

            history.forEachIndexed { index, action ->
                when (action.kind) {
                    WorkflowActionKind.EXTERNAL_CHANGE -> {
                        serverVersions[action.entity] = serverVersions.getValue(action.entity) + 1
                    }
                    WorkflowActionKind.REFRESH -> {
                        visibleVersions.putAll(serverVersions)
                    }
                    else -> {
                        val actual = action.actualAffectedEntities()
                        val expected = action.expectedAffectedEntities()
                        assertEquals(expected, actual, "step=$index action=$action history=$history")

                        val invalidation = ServerStateInvalidation(actual)
                        watchedEntities.forEach { watched ->
                            assertEquals(
                                watched in expected,
                                invalidation.affectsAny(watched),
                                "step=$index watched=$watched action=$action history=$history",
                            )
                        }
                        expected.forEach { entity ->
                            serverVersions[entity] = serverVersions.getValue(entity) + 1
                            visibleVersions[entity] = serverVersions.getValue(entity)
                        }
                    }
                }

                if (action.kind == WorkflowActionKind.REFRESH) {
                    assertEquals(serverVersions, visibleVersions, "step=$index history=$history")
                }
            }
        }
    }

    private fun readFamilies(mangaId: Int): Set<ServerStateEntity> {
        return setOf(
            ServerStateEntity.Library,
            ServerStateEntity.Updates,
            ServerStateEntity.History,
            ServerStateEntity.Manga(mangaId),
            ServerStateEntity.Chapters(mangaId),
            ServerStateEntity.Trackers(mangaId),
        )
    }
}

private enum class WorkflowActionKind {
    LIBRARY_READ,
    UPDATES_BOOKMARK,
    UPDATES_DOWNLOAD,
    MANGA_LIBRARY,
    CHAPTER_READ,
    CHAPTER_BOOKMARK,
    NOTES,
    TRACK,
    EXTERNAL_CHANGE,
    REFRESH,
}

private data class WorkflowAction(
    val kind: WorkflowActionKind,
    val mangaIds: List<Int>,
    val entity: ServerStateEntity,
) {
    fun actualAffectedEntities(): Set<ServerStateEntity> = when (kind) {
        WorkflowActionKind.LIBRARY_READ -> serverLibraryReadAffectedEntities(mangaIds)
        WorkflowActionKind.UPDATES_BOOKMARK -> serverUpdatesBookmarkAffectedEntities(mangaIds)
        WorkflowActionKind.UPDATES_DOWNLOAD -> serverUpdatesDownloadAffectedEntities(mangaIds)
        WorkflowActionKind.MANGA_LIBRARY -> serverMangaLibraryAffectedEntities(mangaIds.first())
        WorkflowActionKind.CHAPTER_READ -> serverChapterReadAffectedEntities(mangaIds.first())
        WorkflowActionKind.CHAPTER_BOOKMARK -> serverChapterBookmarkAffectedEntities(mangaIds.first())
        WorkflowActionKind.NOTES -> serverMangaNotesAffectedEntities(mangaIds.first())
        WorkflowActionKind.TRACK -> serverTrackAffectedEntities(mangaIds.first())
        WorkflowActionKind.EXTERNAL_CHANGE,
        WorkflowActionKind.REFRESH,
        -> emptySet()
    }

    fun expectedAffectedEntities(): Set<ServerStateEntity> = when (kind) {
        WorkflowActionKind.LIBRARY_READ -> mangaIds.flatMap(::readFamilies).toSet()
        WorkflowActionKind.UPDATES_BOOKMARK -> setOf(ServerStateEntity.Updates) + mangaIds.flatMap(::bookmarkFamilies)
        WorkflowActionKind.UPDATES_DOWNLOAD -> setOf(ServerStateEntity.Updates, ServerStateEntity.Downloads) +
            mangaIds.flatMap(::downloadFamilies)
        WorkflowActionKind.MANGA_LIBRARY -> setOf(
            ServerStateEntity.Library,
            ServerStateEntity.Categories,
            ServerStateEntity.Manga(mangaIds.first()),
        )
        WorkflowActionKind.CHAPTER_READ -> readFamilies(mangaIds.first())
        WorkflowActionKind.CHAPTER_BOOKMARK -> bookmarkFamilies(mangaIds.first())
        WorkflowActionKind.NOTES -> setOf(
            ServerStateEntity.Manga(mangaIds.first()),
            ServerStateEntity.Notes(mangaIds.first()),
        )
        WorkflowActionKind.TRACK -> setOf(
            ServerStateEntity.Manga(mangaIds.first()),
            ServerStateEntity.Trackers(mangaIds.first()),
        )
        WorkflowActionKind.EXTERNAL_CHANGE,
        WorkflowActionKind.REFRESH,
        -> emptySet()
    }
}

private val workflowActionArb = Arb.bind(
    Arb.enum<WorkflowActionKind>(),
    Arb.list(Arb.int(1..10), 1..5),
    Arb.int(1..10),
) { kind, mangaIds, externalMangaId ->
    WorkflowAction(
        kind = kind,
        mangaIds = mangaIds.distinct(),
        entity = ServerStateEntity.Manga(externalMangaId),
    )
}

private fun workflowWatchedEntities(): Set<ServerStateEntity> {
    return buildSet {
        add(ServerStateEntity.Library)
        add(ServerStateEntity.Categories)
        add(ServerStateEntity.Downloads)
        add(ServerStateEntity.Updates)
        add(ServerStateEntity.History)
        (1..10).forEach { mangaId ->
            add(ServerStateEntity.Manga(mangaId))
            add(ServerStateEntity.Chapters(mangaId))
            add(ServerStateEntity.Trackers(mangaId))
            add(ServerStateEntity.Notes(mangaId))
        }
    }
}

private fun readFamilies(mangaId: Int): Set<ServerStateEntity> {
    return setOf(
        ServerStateEntity.Library,
        ServerStateEntity.Updates,
        ServerStateEntity.History,
        ServerStateEntity.Manga(mangaId),
        ServerStateEntity.Chapters(mangaId),
        ServerStateEntity.Trackers(mangaId),
    )
}

private fun bookmarkFamilies(mangaId: Int): Set<ServerStateEntity> {
    return setOf(ServerStateEntity.Manga(mangaId), ServerStateEntity.Chapters(mangaId))
}

private fun downloadFamilies(mangaId: Int): Set<ServerStateEntity> {
    return bookmarkFamilies(mangaId) + ServerStateEntity.Downloads
}

private data class IntentScenario(
    val baseline: ServerReaderIntentBaseline,
    val currentBaseline: ServerReaderIntentBaseline,
    val type: PendingServerReaderIntentType,
) {
    fun intent(chapterId: Int): PendingServerReaderIntent {
        return PendingServerReaderIntent(
            serverKey = "server-${chapterId % 3}",
            mangaId = chapterId % 5 + 1,
            chapterId = chapterId,
            type = type,
            baseline = baseline,
            desiredIsRead = if (type == PendingServerReaderIntentType.PROGRESS) !baseline.isRead else null,
            desiredLastPageRead = if (type ==
                PendingServerReaderIntentType.PROGRESS
            ) {
                baseline.lastPageRead + 1
            } else {
                null
            },
            desiredIsBookmarked = if (type == PendingServerReaderIntentType.BOOKMARK) !baseline.isBookmarked else null,
            createdAt = chapterId.toLong(),
            updatedAt = chapterId.toLong(),
        )
    }
}

private val intentScenarioArb = Arb.bind(
    Arb.boolean(),
    Arb.int(0..500),
    Arb.boolean(),
    Arb.boolean(),
    Arb.int(0..500),
    Arb.boolean(),
    Arb.boolean(),
) { baselineRead, baselinePage, baselineBookmark, currentRead, currentPage, currentBookmark, isBookmark ->
    IntentScenario(
        baseline = ServerReaderIntentBaseline(baselineRead, baselinePage, baselineBookmark),
        currentBaseline = ServerReaderIntentBaseline(currentRead, currentPage, currentBookmark),
        type = if (isBookmark) PendingServerReaderIntentType.BOOKMARK else PendingServerReaderIntentType.PROGRESS,
    )
}
