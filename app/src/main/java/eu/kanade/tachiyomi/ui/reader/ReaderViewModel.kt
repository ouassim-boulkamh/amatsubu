package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.net.Uri
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.data.database.models.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyStore
import eu.kanade.tachiyomi.data.suwayomi.MangaStatus
import eu.kanade.tachiyomi.data.suwayomi.ServerReadStatePendingStore
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiMangaMetaDto
import eu.kanade.tachiyomi.data.suwayomi.normalizedGenre
import eu.kanade.tachiyomi.data.suwayomi.resolveServerUrl
import eu.kanade.tachiyomi.data.suwayomi.serverCoverLastModified
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.loader.ClientDeviceChapterCopyPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.loader.SuwayomiPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import mihon.core.common.extensions.EMPTY
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.data.suwayomi.UpdateStrategy as SuwayomiUpdateStrategy

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val imageSaver: ImageSaver = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ViewModel() {

    private val suwayomiProvider = SuwayomiClientProvider()
    private val suwayomiHttpClient = suwayomiProvider.httpClient
    private val suwayomiClient = suwayomiProvider.graphQlClient
    private val clientDeviceChapterCopyStore: ClientDeviceChapterCopyStore = Injekt.get()
    private val pendingReadStateStore: ServerReadStatePendingStore = Injekt.get()
    private var serverChapterList: List<ReaderChapter>? = null
    private var serverDownloadedChapterIds: Set<Long> = emptySet()

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }
    private var chapterPageIndexChapterId = chapterId

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(manga?.source) }
    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                if (chapterPageIndex >= 0 && chapterPageIndexChapterId == currentChapter.chapter.id) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                } else if (!currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.last_page_read
                }
                currentChapter.coerceRequestedPageToLoadedPages()
                chapterId = currentChapter.chapter.id!!
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        // No Android-owned reader download cleanup remains.
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with Suwayomi/server manga and chapter ids.
     */
    suspend fun init(mangaId: Long, initialChapterId: Long): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                initServerReader(mangaId, initialChapterId)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                initDeviceCopyReader(mangaId, initialChapterId) ?: Result.failure(e)
            }
        }
    }

    private suspend fun initDeviceCopyReader(mangaId: Long, initialChapterId: Long): Result<Boolean>? {
        val copy = clientDeviceChapterCopyStore.getCompleteFreshCopy(
            serverKey = suwayomiProvider.serverKey(),
            mangaId = mangaId.toInt(),
            chapterId = initialChapterId.toInt(),
        ) ?: return null
        val copies = clientDeviceChapterCopyStore.getCopiesForManga(
            serverKey = suwayomiProvider.serverKey(),
            mangaId = mangaId.toInt(),
        )
        val deviceCopyReaderChapters = buildServerReaderChaptersFromDeviceCopies(
            copies = copies,
            selectedCopy = copy,
            skipDupe = readerPreferences.skipDupe.get(),
        )
        val manga = deviceCopyReaderChapters.manga
        val readerChapters = deviceCopyReaderChapters.chapterList

        serverChapterList = readerChapters.chapters
        mutableState.update { it.copy(manga = manga) }
        if (chapterId == -1L) chapterId = readerChapters.selectedChapter.chapter.id!!

        loadServerChapter(readerChapters.selectedChapter)
        return Result.success(true)
    }

    private suspend fun initServerReader(mangaId: Long, initialChapterId: Long): Result<Boolean> {
        val manga = suwayomiClient.getManga(mangaId.toInt()).toDomainManga()
        val serverChapters = suwayomiClient.getChapters(mangaId.toInt())
        serverDownloadedChapterIds = serverChapters
            .filter { it.isDownloaded }
            .map { it.id.toLong() }
            .toSet()
        val localDownloadedChapterIds = clientDeviceChapterCopyStore
            .getCopiesForManga(suwayomiProvider.serverKey(), mangaId.toInt())
            .filter { it.isComplete && it.freshness == ClientChapterCopyFreshness.FRESH }
            .map { it.chapterId.toLong() }
            .toSet()
        val chapters = serverChapters.map { it.toDomainChapter() }
        val selectedChapter = chapters.firstOrNull { it.id == initialChapterId }
            ?: chapters.firstOrNull()
            ?: return Result.failure(IllegalStateException("No chapters found"))
        val serverReaderChapters = buildServerReaderChapters(
            chapters = chapters,
            manga = manga,
            selectedChapter = selectedChapter,
            skipRead = readerPreferences.skipRead.get(),
            skipFiltered = readerPreferences.skipFiltered.get(),
            skipDupe = readerPreferences.skipDupe.get(),
            downloadedOnly = basePreferences.downloadedOnly.get(),
            downloadedChapterIds = serverDownloadedChapterIds,
            localDownloadedChapterIds = localDownloadedChapterIds,
        )

        serverChapterList = serverReaderChapters.chapters
        mutableState.update { it.copy(manga = manga) }
        if (chapterId == -1L) chapterId = serverReaderChapters.selectedChapter.chapter.id!!

        loadServerChapter(serverReaderChapters.selectedChapter)
        return Result.success(true)
    }

    private suspend fun loadServerPages(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null) {
            return
        }

        val loader = getReaderPageLoader(chapter)

        try {
            chapter.state = ReaderChapter.State.Loading
            val pages = loader.getPages().onEach { it.chapter = chapter }
            if (pages.isEmpty()) {
                throw IllegalStateException("No pages found")
            }
            if (!chapter.chapter.read) {
                chapter.requestedPage = chapter.chapter.last_page_read
            }
            chapter.state = ReaderChapter.State.Loaded(pages)
            chapter.coerceRequestedPageToLoadedPages()
        } catch (e: Throwable) {
            chapter.state = ReaderChapter.State.Error(e)
            throw e
        }
    }

    private suspend fun getReaderPageLoader(chapter: ReaderChapter): PageLoader {
        val source = resolveReaderChapterSource(
            clientDeviceChapterCopyStore.getCopy(
                serverKey = suwayomiProvider.serverKey(),
                mangaId = chapter.chapter.manga_id!!.toInt(),
                chapterId = chapter.chapter.id!!.toInt(),
            ),
        )
        val loader = when (source) {
            is ReaderChapterSource.DeviceCopy -> {
                chapter.pageLoader as? ClientDeviceChapterCopyPageLoader
                    ?: ClientDeviceChapterCopyPageLoader(source.copy)
            }
            ReaderChapterSource.Suwayomi -> {
                chapter.pageLoader as? SuwayomiPageLoader ?: SuwayomiPageLoader(
                    chapter = chapter,
                    client = suwayomiClient,
                    baseUrl = suwayomiProvider::baseUrl,
                    httpClient = suwayomiHttpClient,
                )
            }
        }
        chapter.pageLoader = loader
        return loader
    }

    private suspend fun loadServerChapter(chapter: ReaderChapter): ViewerChapters {
        loadServerPages(chapter)

        val chapters = serverChapterList.orEmpty()
        val chapterPos = chapters.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapters.getOrNull(chapterPos - 1),
            chapters.getOrNull(chapterPos + 1),
        )
        newChapters.checkServerReaderWindowInvariants()

        withUIContext {
            mutableState.update {
                newChapters.ref()
                it.viewerChapters?.unref()
                it.copy(
                    viewerChapters = newChapters,
                    bookmarked = newChapters.currChapter.chapter.bookmark,
                    currentPage = newChapters.currChapter.requestedPage + 1,
                )
            }
        }
        return newChapters
    }

    private fun ViewerChapters.checkServerReaderWindowInvariants() {
        if (!isDebugBuildType) return

        val window = listOfNotNull(prevChapter, currChapter, nextChapter)
        val duplicateIds = window
            .groupBy { it.chapter.id }
            .filterKeys { it != null }
            .filterValues { it.size > 1 }
            .keys

        if (duplicateIds.isEmpty()) return

        val message = buildString {
            append("Invalid server reader ViewerChapters window: duplicate chapter ids=")
            append(duplicateIds.joinToString())
            append("; prev=")
            append(prevChapter.debugSummary())
            append("; curr=")
            append(currChapter.debugSummary())
            append("; next=")
            append(nextChapter.debugSummary())
        }
        logcat(LogPriority.ERROR) { message }
        throw IllegalStateException(message)
    }

    private fun ReaderChapter?.debugSummary(): String {
        if (this == null) return "null"
        val chapter = chapter
        return "id=${chapter.id}, number=${chapter.chapter_number}, url=${chapter.url}, " +
            "pages=${pages?.size ?: -1}, read=${chapter.read}"
    }

    private fun SuwayomiMangaDto.toDomainManga(): Manga {
        return Manga(
            id = id.toLong(),
            source = sourceId.toLongOrNull() ?: 0L,
            favorite = inLibrary,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = meta.toViewerFlags(),
            chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC,
            coverLastModified = serverCoverLastModified(),
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = normalizedGenre(),
            status = status.toDomainStatus(),
            thumbnailUrl = thumbnailUrl?.let { resolveServerUrl(suwayomiProvider.baseUrl(), it) },
            updateStrategy = updateStrategy.toDomainUpdateStrategy(),
            initialized = initialized,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            notes = "",
            memo = JsonObject.EMPTY,
        )
    }

    private fun SuwayomiChapterDto.toDomainChapter(): tachiyomi.domain.chapter.model.Chapter {
        return tachiyomi.domain.chapter.model.Chapter(
            id = id.toLong(),
            mangaId = mangaId.toLong(),
            read = isRead,
            bookmark = isBookmarked,
            lastPageRead = lastPageRead.toLong(),
            dateFetch = 0L,
            sourceOrder = sourceOrder.toLong(),
            url = realUrl ?: url,
            name = name,
            dateUpload = uploadDate,
            chapterNumber = chapterNumber.toDouble(),
            scanlator = scanlator,
            lastModifiedAt = 0L,
            version = 0L,
            memo = JsonObject.EMPTY,
        )
    }

    private fun MangaStatus.toDomainStatus(): Long {
        return when (this) {
            MangaStatus.UNKNOWN -> SManga.UNKNOWN
            MangaStatus.ONGOING -> SManga.ONGOING
            MangaStatus.COMPLETED -> SManga.COMPLETED
            MangaStatus.LICENSED -> SManga.LICENSED
            MangaStatus.PUBLISHING_FINISHED -> SManga.PUBLISHING_FINISHED
            MangaStatus.CANCELLED -> SManga.CANCELLED
            MangaStatus.ON_HIATUS -> SManga.ON_HIATUS
        }.toLong()
    }

    private fun SuwayomiUpdateStrategy.toDomainUpdateStrategy(): eu.kanade.tachiyomi.source.model.UpdateStrategy {
        return when (this) {
            SuwayomiUpdateStrategy.ALWAYS_UPDATE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE
            SuwayomiUpdateStrategy.ONLY_FETCH_ONCE -> eu.kanade.tachiyomi.source.model.UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private fun List<SuwayomiMangaMetaDto>.toViewerFlags(): Long {
        val readerMode = firstOrNull { it.key == SUWAYOMI_READER_MODE_META_KEY }
            ?.value
            ?.toReadingMode()
            ?: ReadingMode.DEFAULT
        val orientation = firstOrNull {
            it.key == SUWAYOMI_READER_ORIENTATION_META_KEY ||
                it.key == LEGACY_SUWAYOMI_READER_ORIENTATION_META_KEY
        }
            ?.value
            ?.toReaderOrientation()
            ?: ReaderOrientation.DEFAULT
        return 0L
            .setFlag(readerMode.flagValue.toLong(), ReadingMode.MASK.toLong())
            .setFlag(orientation.flagValue.toLong(), ReaderOrientation.MASK.toLong())
    }

    private fun String.toReadingMode(): ReadingMode? {
        return when (this) {
            "defaultReader" -> ReadingMode.DEFAULT
            "singleHorizontalLTR" -> ReadingMode.LEFT_TO_RIGHT
            "singleHorizontalRTL" -> ReadingMode.RIGHT_TO_LEFT
            "singleVertical" -> ReadingMode.VERTICAL
            "webtoon" -> ReadingMode.WEBTOON
            "continuousVertical" -> ReadingMode.CONTINUOUS_VERTICAL
            else -> null
        }
    }

    private fun String.toReaderOrientation(): ReaderOrientation? {
        return when (this) {
            "default" -> ReaderOrientation.DEFAULT
            "free" -> ReaderOrientation.FREE
            "portrait" -> ReaderOrientation.PORTRAIT
            "landscape" -> ReaderOrientation.LANDSCAPE
            "lockedPortrait" -> ReaderOrientation.LOCKED_PORTRAIT
            "lockedLandscape" -> ReaderOrientation.LOCKED_LANDSCAPE
            "reversePortrait" -> ReaderOrientation.REVERSE_PORTRAIT
            else -> null
        }
    }

    private fun ReadingMode.toSuwayomiReaderMode(): String {
        return when (this) {
            ReadingMode.DEFAULT -> "defaultReader"
            ReadingMode.LEFT_TO_RIGHT -> "singleHorizontalLTR"
            ReadingMode.RIGHT_TO_LEFT -> "singleHorizontalRTL"
            ReadingMode.VERTICAL -> "singleVertical"
            ReadingMode.WEBTOON -> "webtoon"
            ReadingMode.CONTINUOUS_VERTICAL -> "continuousVertical"
        }
    }

    private fun ReaderOrientation.toSuwayomiReaderOrientation(): String {
        return when (this) {
            ReaderOrientation.DEFAULT -> "default"
            ReaderOrientation.FREE -> "free"
            ReaderOrientation.PORTRAIT -> "portrait"
            ReaderOrientation.LANDSCAPE -> "landscape"
            ReaderOrientation.LOCKED_PORTRAIT -> "lockedPortrait"
            ReaderOrientation.LOCKED_LANDSCAPE -> "lockedLandscape"
            ReaderOrientation.REVERSE_PORTRAIT -> "reversePortrait"
        }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        viewModelScope.launchIO {
            logcat { "Loading ${chapter.chapter.url}" }

            updateHistory()
            restartReadTimer()

            try {
                loadServerChapter(chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(chapter: ReaderChapter) {
        logcat { "Loading adjacent ${chapter.chapter.url}" }

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                updateHistory()
                restartReadTimer()
                loadServerChapter(chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        try {
            logcat { "Preloading ${chapter.chapter.url}" }
            loadServerPages(chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        eventChannel.trySend(Event.ReloadViewerChapters)
    }

    fun isServerChapterDownloadedForReader(chapter: ReaderChapter?): Boolean {
        val dbChapter = chapter?.chapter ?: return false

        return dbChapter.id in serverDownloadedChapterIds
    }

    fun onViewerLoaded(viewer: Viewer?) {
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Save last page read and mark as read if needed
        viewModelScope.launchNonCancellable {
            updateChapterProgress(selectedChapter, page)
        }

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        eventChannel.trySend(Event.PageChanged)
    }

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     */
    private suspend fun updateChapterProgress(readerChapter: ReaderChapter, page: Page) {
        val pageIndex = page.index
        val readerChapterId = readerChapter.chapter.id!!

        updateActiveChapterPage(readerChapter, pageIndex)
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex
        chapterPageIndexChapterId = readerChapterId

        if (!incognitoMode && page.status !is Page.State.Error) {
            readerChapter.chapter.last_page_read = pageIndex
            val shouldTrackServerProgress = !readerChapter.chapter.read &&
                readerChapter.pages?.lastIndex == pageIndex

            if (readerChapter.pages?.lastIndex == pageIndex) {
                runCatching {
                    updateChapterProgressOnComplete(readerChapter)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    logcat(LogPriority.ERROR, error) { "Failed to sync completed chapter progress" }
                }
            }

            val updatedChapter = runCatching {
                suwayomiClient.updateChapterProgress(
                    chapterId = readerChapterId.toInt(),
                    isRead = readerChapter.chapter.read,
                    lastPageRead = readerChapter.chapter.last_page_read,
                )
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.ERROR, error) { "Failed to sync chapter progress" }
                if (readerChapter.chapter.read) {
                    pendingReadStateStore.upsert(
                        serverKey = suwayomiProvider.serverKey(),
                        mangaId = readerChapter.chapter.manga_id!!.toInt(),
                        chapterId = readerChapterId.toInt(),
                        isRead = true,
                    )
                }
                return
            }
            if (readerChapter.requestedPage == pageIndex && getCurrentChapter()?.chapter?.id == readerChapterId) {
                readerChapter.chapter.read = updatedChapter.isRead
                readerChapter.chapter.last_page_read = updatedChapter.lastPageRead
            }
            if (updatedChapter.isRead && shouldTrackServerProgress) {
                val mangaId = manga?.id?.toInt()
                if (mangaId != null) {
                    runCatching {
                        suwayomiClient.trackProgress(mangaId)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        logcat(LogPriority.ERROR, error) { "Failed to update server tracker progress" }
                    }
                }
            }
        }
    }

    private fun updateActiveChapterPage(readerChapter: ReaderChapter, pageIndex: Int) {
        val readerChapterId = readerChapter.chapter.id
        mutableState.update { state ->
            if (state.currentChapter?.chapter?.id != readerChapterId) {
                state
            } else {
                state.copy(currentPage = pageIndex + 1)
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        readerChapter.chapter.read = true

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        serverChapterList.orEmpty()
            .map { it.chapter }
            .filter { chapter ->
                chapter.id != readerChapter.chapter.id &&
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapter_number == readerChapter.chapter.chapter_number
            }
            .forEach { chapter ->
                val updatedChapter = suwayomiClient.updateChapterRead(
                    chapterId = chapter.id!!.toInt(),
                    isRead = true,
                )
                chapter.read = updatedChapter.isRead
                chapter.last_page_read = updatedChapter.lastPageRead
            }
    }

    fun restartReadTimer() {
        chapterReadStartTime = Instant.now().toEpochMilli()
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    suspend fun updateHistory() {
        getCurrentChapter()?.let { readerChapter ->
            if (incognitoMode) return@let
            chapterReadStartTime = null
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter() {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    private fun ReaderChapter.coerceRequestedPageToLoadedPages() {
        val lastPageIndex = pages?.lastIndex ?: return
        requestedPage = requestedPage.coerceIn(0, lastPageIndex)
    }

    fun getChapterUrl(): String? {
        val sChapter = getCurrentChapter()?.chapter ?: return null
        return sChapter.url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val chapter = getCurrentChapter()?.chapter ?: return
        val bookmarked = !chapter.bookmark
        chapter.bookmark = bookmarked

        viewModelScope.launchNonCancellable {
            try {
                val updatedChapter = suwayomiClient.updateChapterBookmark(
                    chapterId = chapter.id!!.toInt(),
                    isBookmarked = bookmarked,
                )
                chapter.bookmark = updatedChapter.isBookmarked
                mutableState.update {
                    it.copy(
                        bookmarked = updatedChapter.isBookmarked,
                    )
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to update server reader bookmark state" }
                chapter.bookmark = !bookmarked
                mutableState.update {
                    it.copy(
                        bookmarked = !bookmarked,
                    )
                }
            }
        }

        mutableState.update {
            it.copy(
                bookmarked = bookmarked,
            )
        }
    }

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode.get()
        val readingMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT -> default
            else -> manga?.readingMode?.toInt() ?: default
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        runBlocking(Dispatchers.IO) {
            suwayomiClient.setMangaMeta(
                mangaId = manga.id.toInt(),
                key = SUWAYOMI_READER_MODE_META_KEY,
                value = readingMode.toSuwayomiReaderMode(),
            )
            val updatedManga = manga.copy(
                viewerFlags = manga.viewerFlags.setFlag(readingMode.flagValue.toLong(), ReadingMode.MASK.toLong()),
            )
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                currChapters.currChapter.requestedPage = currChapters.currChapter.chapter.last_page_read
            }
            mutableState.update {
                it.copy(
                    manga = updatedManga,
                    viewerChapters = currChapters,
                )
            }
            eventChannel.send(Event.ReloadViewerChapters)
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType.get()
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            suwayomiClient.setMangaMeta(
                mangaId = manga.id.toInt(),
                key = SUWAYOMI_READER_ORIENTATION_META_KEY,
                value = orientation.toSuwayomiReaderOrientation(),
            )
            val updatedManga = manga.copy(
                viewerFlags = manga.viewerFlags.setFlag(
                    orientation.flagValue.toLong(),
                    ReaderOrientation.MASK.toLong(),
                ),
            )
            mutableState.update { it.copy(manga = updatedManga) }
            eventChannel.send(Event.SetOrientation(getMangaOrientation()))
            eventChannel.send(Event.ReloadViewerChapters)
        }
    }

    fun toggleCropBorders(): Boolean {
        val isPagerType = ReadingMode.isPagerType(getMangaReadingMode())
        return if (isPagerType) {
            readerPreferences.cropBorders.toggle()
        } else {
            readerPreferences.cropBordersWebtoon.toggle()
        }
    }

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage() {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga.get()) {
            DiskUtil.buildValidFilename(
                manga.title,
            )
        } else {
            ""
        }

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Pictures.create(relativePath),
                    ),
                )
                withUIContext {
                    notifier.onComplete(uri)
                    eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
                }
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(copyToClipboard: Boolean) {
        val page = (state.value.dialog as? Dialog.PageActions)?.page
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        try {
            viewModelScope.launchNonCancellable {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog
        data class PageActions(val page: ReaderPage) : Dialog
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(val uri: Uri, val page: ReaderPage) : Event
        data class CopyImage(val uri: Uri) : Event
    }

    private companion object {
        const val SUWAYOMI_READER_MODE_META_KEY = "flutter_readerMode"
        const val SUWAYOMI_READER_ORIENTATION_META_KEY = "amatsubu_readerOrientation"
        val LEGACY_SUWAYOMI_READER_ORIENTATION_META_KEY = "sorami" + "hon_readerOrientation"
    }
}
