package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.decodeFromJsonResponse
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

internal class SuwayomiGraphQlClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoint: () -> String,
    private val snapshotCache: SuwayomiSnapshotCache? = null,
) {

    suspend fun testConnection(): SuwayomiConnectionCheck {
        val graphQlEndpoint = endpoint()
        val settings = runCatching { serverSettings() }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Could not load Suwayomi settings from $graphQlEndpoint",
                    error,
                )
            }
        val sources = runCatching { sourceList() }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Connected to $graphQlEndpoint, but could not load the source list",
                    error,
                )
            }
        check(sources.isNotEmpty()) { "Connected to $graphQlEndpoint, but the server returned no sources" }
        return SuwayomiConnectionCheck(
            endpoint = graphQlEndpoint,
            sourceCount = sources.size,
            serverPort = settings.port,
        )
    }

    suspend fun sourceList(): List<SuwayomiSourceDto> {
        val query = """
            query SourceList {
              sources {
                nodes {
                  baseUrl
                  contentWarning
                  displayName
                  homeUrl
                  iconUrl
                  id
                  isConfigurable
                  isNsfw
                  lang
                  name
                  supportsLatest
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(SourceListData.serializer()),
        ).sources.nodes
    }

    suspend fun trackerList(): List<SuwayomiTrackerDto> {
        val query = """
            query TrackerList {
              trackers {
                nodes {
                  ...AmatsubuTracker
                }
              }
            }

            $TRACKER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(TrackersData.serializer()),
        ).trackers.nodes
    }

    suspend fun loginTrackerCredentials(
        trackerId: Int,
        username: String,
        password: String,
    ): SuwayomiTrackerDto {
        val query = """
            mutation LoginTrackerCredentials(${'$'}input: LoginTrackerCredentialsInput!) {
              loginTrackerCredentials(input: ${'$'}input) {
                isLoggedIn
                tracker {
                  ...AmatsubuTracker
                }
              }
            }

            $TRACKER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("trackerId", trackerId)
                    put("username", username)
                    put("password", password)
                }
            },
            deserializer = GraphQlResponse.serializer(TrackerLoginData.serializer()),
        ).loginTrackerCredentials?.tracker ?: error("Suwayomi server returned no tracker")
    }

    suspend fun loginTrackerOAuth(
        trackerId: Int,
        callbackUrl: String,
    ): SuwayomiTrackerDto {
        val query = """
            mutation LoginTrackerOAuth(${'$'}input: LoginTrackerOAuthInput!) {
              loginTrackerOAuth(input: ${'$'}input) {
                isLoggedIn
                tracker {
                  ...AmatsubuTracker
                }
              }
            }

            $TRACKER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("trackerId", trackerId)
                    put("callbackUrl", callbackUrl)
                }
            },
            deserializer = GraphQlResponse.serializer(TrackerLoginData.serializer()),
        ).loginTrackerOAuth?.tracker ?: error("Suwayomi server returned no tracker")
    }

    suspend fun logoutTracker(trackerId: Int): SuwayomiTrackerDto {
        val query = """
            mutation LogoutTracker(${'$'}input: LogoutTrackerInput!) {
              logoutTracker(input: ${'$'}input) {
                isLoggedIn
                tracker {
                  ...AmatsubuTracker
                }
              }
            }

            $TRACKER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("trackerId", trackerId)
                }
            },
            deserializer = GraphQlResponse.serializer(TrackerLogoutData.serializer()),
        ).logoutTracker.tracker
    }

    suspend fun extensionList(): List<SuwayomiExtensionDto> {
        val query = """
            mutation FetchExtensionList {
              fetchExtensions(input: {}) {
                extensions {
                  apkName
                  apkUrl
                  contentWarning
                  extensionLib
                  hasUpdate
                  iconUrl
                  isInstalled
                  isNsfw
                  isObsolete
                  lang
                  name
                  pkgName
                  repo
                  source {
                    nodes {
                      baseUrl
                      contentWarning
                      displayName
                      homeUrl
                      iconUrl
                      id
                      isConfigurable
                      isNsfw
                      lang
                      name
                      supportsLatest
                    }
                  }
                  storeIndexUrl
                  versionCode
                  versionCodeLong
                  versionName
                  extensionStore {
                    name
                    badgeLabel
                    signingKey
                    contactWebsite
                    contactDiscord
                    indexUrl
                    isLegacy
                    extensionListUrl
                  }
                }
                extensionStores {
                  name
                  badgeLabel
                  signingKey
                  contactWebsite
                  contactDiscord
                  indexUrl
                  isLegacy
                  extensionListUrl
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(FetchExtensionsData.serializer()),
        ).fetchExtensions.extensions
    }

    suspend fun extensionStores(): List<SuwayomiExtensionStoreDto> {
        val query = """
            query ExtensionStores {
              extensionStores {
                nodes {
                  ...AmatsubuExtensionStore
                }
              }
            }

            $EXTENSION_STORE_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(ExtensionStoresData.serializer()),
        ).extensionStores.nodes
    }

    suspend fun addExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto {
        val query = """
            mutation AddExtensionStore(${'$'}indexUrl: String!) {
              addExtensionStore(input: { indexUrl: ${'$'}indexUrl }) {
                extensionStore {
                  ...AmatsubuExtensionStore
                }
              }
            }

            $EXTENSION_STORE_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("indexUrl", indexUrl)
            },
            deserializer = GraphQlResponse.serializer(AddExtensionStoreData.serializer()),
        ).addExtensionStore.extensionStore
    }

    suspend fun removeExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto? {
        val query = """
            mutation RemoveExtensionStore(${'$'}indexUrl: String!) {
              removeExtensionStore(input: { indexUrl: ${'$'}indexUrl }) {
                extensionStore {
                  ...AmatsubuExtensionStore
                }
              }
            }

            $EXTENSION_STORE_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("indexUrl", indexUrl)
            },
            deserializer = GraphQlResponse.serializer(RemoveExtensionStoreData.serializer()),
        ).removeExtensionStore.extensionStore
    }

    suspend fun updateExtension(
        pkgName: String,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): SuwayomiExtensionDto? {
        val query = """
            mutation UpdateExtension(${'$'}id: String!, ${'$'}install: Boolean!, ${'$'}uninstall: Boolean!, ${'$'}update: Boolean!) {
              updateExtension(input: { id: ${'$'}id, patch: { install: ${'$'}install, uninstall: ${'$'}uninstall, update: ${'$'}update } }) {
                extension {
                  apkName
                  apkUrl
                  contentWarning
                  extensionLib
                  hasUpdate
                  iconUrl
                  isInstalled
                  isNsfw
                  isObsolete
                  lang
                  name
                  pkgName
                  repo
                  source {
                    nodes {
                      baseUrl
                      contentWarning
                      displayName
                      homeUrl
                      iconUrl
                      id
                      isConfigurable
                      isNsfw
                      lang
                      name
                      supportsLatest
                    }
                  }
                  storeIndexUrl
                  versionCode
                  versionCodeLong
                  versionName
                  extensionStore {
                    name
                    badgeLabel
                    signingKey
                    contactWebsite
                    contactDiscord
                    indexUrl
                    isLegacy
                    extensionListUrl
                  }
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("id", pkgName)
                put("install", install)
                put("uninstall", uninstall)
                put("update", update)
            },
            deserializer = GraphQlResponse.serializer(UpdateExtensionData.serializer()),
        ).updateExtension.extension
    }

    suspend fun updateExtensions(
        pkgNames: List<String>,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): List<SuwayomiExtensionDto> {
        val ids = pkgNames.distinct()
        if (ids.isEmpty()) return emptyList()

        val query = """
            mutation UpdateExtensions(${'$'}input: UpdateExtensionsInput!) {
              updateExtensions(input: ${'$'}input) {
                extensions {
                  apkName
                  apkUrl
                  contentWarning
                  extensionLib
                  hasUpdate
                  iconUrl
                  isInstalled
                  isNsfw
                  isObsolete
                  lang
                  name
                  pkgName
                  repo
                  source {
                    nodes {
                      baseUrl
                      contentWarning
                      displayName
                      homeUrl
                      iconUrl
                      id
                      isConfigurable
                      isNsfw
                      lang
                      name
                      supportsLatest
                    }
                  }
                  storeIndexUrl
                  versionCode
                  versionCodeLong
                  versionName
                  extensionStore {
                    name
                    badgeLabel
                    signingKey
                    contactWebsite
                    contactDiscord
                    indexUrl
                    isLegacy
                    extensionListUrl
                  }
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        ids.forEach { add(it) }
                    }
                    putJsonObject("patch") {
                        put("install", install)
                        put("uninstall", uninstall)
                        put("update", update)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateExtensionsData.serializer()),
        ).updateExtensions.extensions
    }

    suspend fun getCategories(): List<SuwayomiCategoryDto> {
        val query = """
            query AllCategories {
              categories(orderBy: ORDER, orderByType: ASC) {
                nodes {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(AllCategoriesData.serializer()),
        ).categories.nodes
    }

    suspend fun createCategory(name: String, order: Int? = null): SuwayomiCategoryDto {
        val query = """
            mutation CreateCategory(${'$'}input: CreateCategoryInput!) {
              createCategory(input: ${'$'}input) {
                category {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("name", name)
                    if (order != null) {
                        put("order", order)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(CreateCategoryData.serializer()),
        ).createCategory?.category ?: error("Suwayomi server returned no category")
    }

    suspend fun updateCategoryName(categoryId: Int, name: String): SuwayomiCategoryDto {
        val query = """
            mutation UpdateCategory(${'$'}input: UpdateCategoryInput!) {
              updateCategory(input: ${'$'}input) {
                category {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", categoryId)
                    putJsonObject("patch") {
                        put("name", name)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateCategoryData.serializer()),
        ).updateCategory?.category ?: error("Suwayomi server returned no category")
    }

    suspend fun updateCategoryFlags(
        categoryId: Int,
        includeInUpdate: SuwayomiCategoryFlag,
        includeInDownload: SuwayomiCategoryFlag,
    ): SuwayomiCategoryDto {
        val query = """
            mutation UpdateCategoryFlags(${'$'}input: UpdateCategoryInput!) {
              updateCategory(input: ${'$'}input) {
                category {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", categoryId)
                    putJsonObject("patch") {
                        put("includeInUpdate", includeInUpdate.name)
                        put("includeInDownload", includeInDownload.name)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateCategoryData.serializer()),
        ).updateCategory?.category ?: error("Suwayomi server returned no category")
    }

    suspend fun updateCategoryOrder(categoryId: Int, position: Int): List<SuwayomiCategoryDto> {
        val query = """
            mutation UpdateCategoryOrder(${'$'}input: UpdateCategoryOrderInput!) {
              updateCategoryOrder(input: ${'$'}input) {
                categories {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", categoryId)
                    put("position", position)
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateCategoryOrderData.serializer()),
        ).updateCategoryOrder?.categories ?: error("Suwayomi server returned no categories")
    }

    suspend fun deleteCategory(categoryId: Int): SuwayomiCategoryDto? {
        val query = """
            mutation DeleteCategory(${'$'}input: DeleteCategoryInput!) {
              deleteCategory(input: ${'$'}input) {
                category {
                  ...AmatsubuCategory
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("categoryId", categoryId)
                }
            },
            deserializer = GraphQlResponse.serializer(DeleteCategoryData.serializer()),
        ).deleteCategory?.category
    }

    suspend fun getCategoryMangas(categoryId: Int): List<SuwayomiMangaDto> {
        val query = """
            query GetCategoryMangas(${'$'}id: Int!) {
              category(id: ${'$'}id) {
                id
                mangas {
                  nodes {
                    ...AmatsubuManga
                  }
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        val mangas = execute(
            query = query,
            variables = buildJsonObject {
                put("id", categoryId)
            },
            deserializer = GraphQlResponse.serializer(GetCategoryMangasData.serializer()),
        ).category.mangas.nodes
        snapshotCache?.storeCategoryMangas(
            serverKey = serverKey(),
            categoryId = categoryId,
            mangas = mangas,
            mirrorToLibrary = categoryId == 0,
        )
        return mangas
    }

    suspend fun getCategoryMangasSnapshot(categoryId: Int): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getCategoryMangas(serverKey(), categoryId)
    }

    suspend fun getMangaCategories(mangaId: Int): List<SuwayomiCategoryDto> {
        val query = """
            query GetMangaCategories(${'$'}id: Int!) {
              manga(id: ${'$'}id) {
                categories {
                  nodes {
                    ...AmatsubuCategory
                  }
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("id", mangaId)
            },
            deserializer = GraphQlResponse.serializer(GetMangaCategoriesData.serializer()),
        ).manga.categories.nodes
    }

    suspend fun getLibraryMangas(): List<SuwayomiMangaDto> {
        val query = """
            query GetLibraryMangas {
              mangas(
                filter: { inLibrary: { equalTo: true } }
              ) {
                nodes {
                  ...AmatsubuManga
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        val mangas = execute(
            query = query,
            deserializer = GraphQlResponse.serializer(LibraryMangaListData.serializer()),
        ).mangas.nodes
        snapshotCache?.storeLibraryMangas(serverKey(), mangas)
        return mangas
    }

    suspend fun getLibraryMangasSnapshot(): SuwayomiSnapshot<List<SuwayomiMangaDto>>? {
        return snapshotCache?.getLibraryMangas(serverKey())
    }

    suspend fun getLibraryChapters(): List<SuwayomiChapterDto> {
        val query = """
            query GetLibraryChapters {
              chapters(
                filter: { inLibrary: { equalTo: true } }
                first: 10000
              ) {
                nodes {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        val chapters = execute(
            query = query,
            deserializer = GraphQlResponse.serializer(ChaptersData.serializer()),
        ).chapters.nodes
        snapshotCache?.storeLibraryChapters(serverKey(), chapters)
        return chapters
    }

    suspend fun getLibraryTrackingData(): LibraryTrackingData {
        val query = """
            query GetLibraryTrackingData {
              libraryTrackingMangas: mangas(
                filter: { inLibrary: { equalTo: true } }
                first: 10000
              ) {
                nodes {
                  id
                  trackRecords {
                    nodes {
                      trackerId
                      score
                    }
                  }
                }
              }
              trackers {
                nodes {
                  id
                  name
                  isLoggedIn
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(LibraryTrackingData.serializer()),
        )
    }

    suspend fun getServerStats(): ServerStatsData {
        val query = """
            query GetServerStats {
              statisticsMangas: mangas(
                filter: { inLibrary: { equalTo: true } }
                first: 10000
              ) {
                totalCount
                nodes {
                  id
                  status
                  unreadCount
                  downloadCount
                  initialized
                  latestReadChapter {
                    lastReadAt
                  }
                  sourceId
                  updateStrategy
                  categories {
                    nodes {
                      id
                      includeInUpdate
                    }
                  }
                  trackRecords {
                    nodes {
                      trackerId
                      score
                    }
                  }
                }
              }
              totalChapters: chapters(filter: { inLibrary: { equalTo: true } }) {
                totalCount
              }
              readChapters: chapters(
                filter: {
                  inLibrary: { equalTo: true }
                  isRead: { equalTo: true }
                }
              ) {
                totalCount
              }
              downloadedChapters: chapters(
                filter: {
                  inLibrary: { equalTo: true }
                  isDownloaded: { equalTo: true }
                }
              ) {
                totalCount
              }
              settings {
                excludeCompleted
                excludeEntryWithUnreadChapters
                excludeNotStarted
                excludeUnreadChapters
              }
              trackers {
                nodes {
                  id
                  name
                  isLoggedIn
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(ServerStatsData.serializer()),
        )
    }

    suspend fun updateMangaCategories(
        mangaId: Int,
        categoryIds: List<Int>,
    ): List<SuwayomiCategoryDto> {
        val currentCategoryIds = getMangaCategories(mangaId).map { it.id }.toSet()
        val targetCategoryIds = categoryIds.distinct()
        val addCategoryIds = targetCategoryIds.filterNot { it in currentCategoryIds }
        val removeCategoryIds = currentCategoryIds.filterNot { it in targetCategoryIds.toSet() }

        if (addCategoryIds.isEmpty() && removeCategoryIds.isEmpty()) {
            return getMangaCategories(mangaId)
        }

        val query = """
            mutation UpdateMangaCategories(${'$'}input: UpdateMangaCategoriesInput!) {
              updateMangaCategories(input: ${'$'}input) {
                manga {
                  categories {
                    nodes {
                      ...AmatsubuCategory
                    }
                  }
                }
              }
            }

            $CATEGORY_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", mangaId)
                    putJsonObject("patch") {
                        if (targetCategoryIds.isEmpty()) {
                            put("clearCategories", true)
                        } else {
                            if (addCategoryIds.isNotEmpty()) {
                                putJsonArray("addToCategories") {
                                    addCategoryIds.forEach { add(it) }
                                }
                            }
                            if (removeCategoryIds.isNotEmpty()) {
                                putJsonArray("removeFromCategories") {
                                    removeCategoryIds.forEach { add(it) }
                                }
                            }
                        }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateMangaCategoriesData.serializer()),
        ).updateMangaCategories.manga.categories.nodes
    }

    suspend fun updateMangasCategories(
        mangaIds: List<Int>,
        addCategoryIds: List<Int> = emptyList(),
        removeCategoryIds: List<Int> = emptyList(),
        clearCategories: Boolean = false,
    ): List<SuwayomiMangaDto> {
        val ids = mangaIds.distinct()
        val addIds = addCategoryIds.distinct()
        val removeIds = removeCategoryIds.distinct()
        if (ids.isEmpty()) return emptyList()
        if (!clearCategories && addIds.isEmpty() && removeIds.isEmpty()) return emptyList()

        val query = """
            mutation UpdateMangasCategories(${'$'}input: UpdateMangasCategoriesInput!) {
              updateMangasCategories(input: ${'$'}input) {
                mangas {
                  ...AmatsubuManga
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        ids.forEach { add(it) }
                    }
                    putJsonObject("patch") {
                        if (clearCategories) {
                            put("clearCategories", true)
                        } else {
                            if (addIds.isNotEmpty()) {
                                putJsonArray("addToCategories") {
                                    addIds.forEach { add(it) }
                                }
                            }
                            if (removeIds.isNotEmpty()) {
                                putJsonArray("removeFromCategories") {
                                    removeIds.forEach { add(it) }
                                }
                            }
                        }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateMangasCategoriesData.serializer()),
        ).updateMangasCategories.mangas
    }

    suspend fun fetchSourceManga(
        sourceId: String,
        type: FetchSourceMangaType,
        page: Int,
        queryText: String? = null,
        filters: List<SuwayomiSourceFilterChange> = emptyList(),
    ): SourceMangaPageDto {
        val query = """
            mutation FetchSourceManga(${'$'}input: FetchSourceMangaInput!) {
              fetchSourceManga(input: ${'$'}input) {
                hasNextPage
                mangas {
                  ...AmatsubuManga
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("source", sourceId)
                    put("type", type.name)
                    put("page", page)
                    if (queryText != null) {
                        put("query", queryText)
                    }
                    if (filters.isNotEmpty()) {
                        putJsonArray("filters") {
                            filters.forEach { change ->
                                add(buildJsonObject { putFilterChange(change) })
                            }
                        }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(FetchSourceMangaData.serializer()),
        ).fetchSourceManga ?: error("Suwayomi server returned no source manga page")
    }

    suspend fun sourceDetails(sourceId: String): SuwayomiSourceDetailsDto {
        val query = """
            query SourceDetails(${'$'}id: LongString!) {
              source(id: ${'$'}id) {
                baseUrl
                contentWarning
                displayName
                homeUrl
                iconUrl
                id
                isConfigurable
                isNsfw
                lang
                name
                supportsLatest
                filters {
                  ...AmatsubuSourceFilter
                }
                preferences {
                  ...AmatsubuSourcePreference
                }
              }
            }

            $SOURCE_FILTER_FRAGMENT
            $SOURCE_FILTER_CHILD_FRAGMENT
            $SOURCE_PREFERENCE_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("id", sourceId)
            },
            deserializer = GraphQlResponse.serializer(SourceDetailsData.serializer()),
        ).source
    }

    suspend fun sourceFilters(sourceId: String): List<SuwayomiSourceFilterDto> {
        return sourceDetails(sourceId).filters
    }

    suspend fun sourcePreferences(sourceId: String): List<SuwayomiSourcePreferenceDto> {
        return sourceDetails(sourceId).preferences
    }

    suspend fun updateSourcePreference(
        sourceId: String,
        change: SuwayomiSourcePreferenceChange,
    ): List<SuwayomiSourcePreferenceDto> {
        val query = """
            mutation UpdateSourcePreference(${'$'}input: UpdateSourcePreferenceInput!) {
              updateSourcePreference(input: ${'$'}input) {
                preferences {
                  ...AmatsubuSourcePreference
                }
              }
            }

            $SOURCE_PREFERENCE_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("source", sourceId)
                    putJsonObject("change") {
                        putPreferenceChange(change)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateSourcePreferenceData.serializer()),
        ).updateSourcePreference.preferences
    }

    suspend fun getManga(mangaId: Int): SuwayomiMangaDto {
        val query = """
            query GetManga(${'$'}id: Int!) {
              manga(id: ${'$'}id) {
                ...AmatsubuManga
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        val manga = execute(
            query = query,
            variables = buildJsonObject {
                put("id", mangaId)
            },
            deserializer = GraphQlResponse.serializer(GetMangaData.serializer()),
        ).manga
        snapshotCache?.storeManga(serverKey(), manga)
        return manga
    }

    suspend fun getMangaSnapshot(mangaId: Int): SuwayomiSnapshot<SuwayomiMangaDto>? {
        return snapshotCache?.getManga(serverKey(), mangaId)
    }

    suspend fun fetchMangaAndChaptersPartial(
        mangaId: Int,
        fetchManga: Boolean,
        fetchChapters: Boolean,
    ): GraphQlPartialResponse<FetchMangaAndChaptersPayload> {
        val query = """
            mutation FetchMangaAndChapters(${'$'}input: FetchMangaAndChaptersInput!) {
              fetchMangaAndChapters(input: ${'$'}input) {
                manga {
                  ...AmatsubuManga
                }
                chapters {
                  ...AmatsubuChapter
                }
              }
            }

            $MANGA_FRAGMENT
            $CHAPTER_FRAGMENT
        """.trimIndent()
        val response = executePartial(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", mangaId)
                    put("fetchManga", fetchManga)
                    put("fetchChapters", fetchChapters)
                }
            },
            deserializer = GraphQlResponse.serializer(FetchMangaAndChaptersData.serializer()),
        )
        val payload = response.data?.fetchMangaAndChapters
        if (payload == null && response.errors.isNotEmpty()) {
            error(response.errors.joinToString("; ") { it.message })
        }
        return GraphQlPartialResponse(
            data = payload ?: error("Suwayomi server returned no manga or chapters"),
            errors = response.errors,
        )
    }

    suspend fun getMangaTrackSummary(mangaId: Int): SuwayomiMangaTrackSummaryDto {
        val query = """
            query GetMangaTrackSummary(${'$'}id: Int!) {
              manga(id: ${'$'}id) {
                description
                id
                status
                thumbnailUrl
                title
                chapters {
                  totalCount
                }
                latestReadChapter {
                  chapterNumber
                }
                unreadCount
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("id", mangaId)
            },
            deserializer = GraphQlResponse.serializer(GetMangaTrackSummaryData.serializer()),
        ).manga
    }

    suspend fun getRecentChapters(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val query = """
            query GetRecentChapters(${'$'}first: Int!) {
              chapters(
                filter: { inLibrary: { equalTo: true } }
                first: ${'$'}first
                order: [
                  { by: FETCHED_AT, byType: DESC }
                  { by: SOURCE_ORDER, byType: DESC }
                ]
              ) {
                nodes {
                  chapterNumber
                  fetchedAt
                  id
                  isDownloaded
                  isBookmarked
                  isRead
                  lastPageRead
                  lastReadAt
                  mangaId
                  name
                  scanlator
                  sourceOrder
                  uploadDate
                  url
                  manga {
                    ...AmatsubuManga
                  }
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("first", limit)
            },
            deserializer = GraphQlResponse.serializer(RecentChaptersData.serializer()),
        ).chapters.nodes
    }

    suspend fun getReadingHistory(limit: Int = 500): List<SuwayomiChapterWithMangaDto> {
        val query = """
            query GetReadingHistory(${'$'}first: Int!) {
              chapters(
                filter: {
                  lastReadAt: { greaterThan: "0" }
                  or: [
                    { isRead: { equalTo: true } }
                    { lastPageRead: { greaterThan: 0 } }
                  ]
                }
                first: ${'$'}first
                order: [
                  { by: LAST_READ_AT, byType: DESC }
                  { by: SOURCE_ORDER, byType: DESC }
                ]
              ) {
                nodes {
                  chapterNumber
                  fetchedAt
                  id
                  isDownloaded
                  isBookmarked
                  isRead
                  lastPageRead
                  lastReadAt
                  mangaId
                  name
                  scanlator
                  sourceOrder
                  uploadDate
                  url
                  manga {
                    ...AmatsubuManga
                  }
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("first", limit)
            },
            deserializer = GraphQlResponse.serializer(RecentChaptersData.serializer()),
        ).chapters.nodes.filter { it.isRead || it.lastPageRead > 0 }
    }

    suspend fun getReadingHistoryChapterIds(pageSize: Int = 500): List<Int> {
        val query = """
            query GetReadingHistoryIds(${'$'}first: Int!, ${'$'}offset: Int!) {
              chapters(
                filter: {
                  lastReadAt: { greaterThan: "0" }
                  or: [
                    { isRead: { equalTo: true } }
                    { lastPageRead: { greaterThan: 0 } }
                  ]
                }
                first: ${'$'}first
                offset: ${'$'}offset
                order: [
                  { by: LAST_READ_AT, byType: DESC }
                  { by: SOURCE_ORDER, byType: DESC }
                ]
              ) {
                nodes {
                  id
                }
                totalCount
              }
            }
        """.trimIndent()

        val ids = mutableListOf<Int>()
        var offset = 0
        do {
            val page = execute(
                query = query,
                variables = buildJsonObject {
                    put("first", pageSize)
                    put("offset", offset)
                },
                deserializer = GraphQlResponse.serializer(ReadingHistoryIdsData.serializer()),
            ).chapters

            ids += page.nodes.map { it.id }
            offset += page.nodes.size
        } while (page.nodes.isNotEmpty() && offset < page.totalCount)

        return ids
    }

    suspend fun updateMangaLibrary(
        mangaId: Int,
        inLibrary: Boolean,
    ): SuwayomiMangaDto {
        val query = """
            mutation UpdateManga(${'$'}input: UpdateMangaInput!) {
              updateManga(input: ${'$'}input) {
                manga {
                  ...AmatsubuManga
                }
              }
            }

            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", mangaId)
                    putJsonObject("patch") {
                        put("inLibrary", inLibrary)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateMangaData.serializer()),
        ).updateManga.manga
    }

    suspend fun updateLibraryMangas(): Boolean {
        val query = """
            mutation UpdateLibraryMangas(${'$'}input: UpdateLibraryMangaInput!) {
              updateLibraryManga(input: ${'$'}input) {
                updateStatus {
                  isRunning
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {}
            },
            deserializer = GraphQlResponse.serializer(UpdateLibraryMangaData.serializer()),
        ).updateLibraryManga.updateStatus.isRunning
    }

    suspend fun updateCategoryMangas(categoryId: Int): Boolean {
        val query = """
            mutation UpdateCategoryMangas(${'$'}input: UpdateCategoryMangaInput!) {
              updateCategoryManga(input: ${'$'}input) {
                updateStatus {
                  isRunning
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("categories") {
                        add(categoryId)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateCategoryMangaData.serializer()),
        ).updateCategoryManga.updateStatus.isRunning
    }

    suspend fun getLibraryUpdateStatus(): SuwayomiLibraryUpdateStatusDto {
        val query = """
            query LibraryUpdateStatus {
              libraryUpdateStatus {
                categoryUpdates {
                  category {
                    ...AmatsubuCategory
                  }
                  status
                }
                mangaUpdates {
                  manga {
                    ...AmatsubuManga
                  }
                  status
                }
                jobsInfo {
                  isRunning
                  totalJobs
                  finishedJobs
                  skippedCategoriesCount
                  skippedMangasCount
                }
              }
            }

            $CATEGORY_FRAGMENT
            $MANGA_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(LibraryUpdateStatusData.serializer()),
        ).libraryUpdateStatus
    }

    suspend fun stopLibraryUpdate() {
        val query = """
            mutation StopLibraryUpdate(${'$'}input: UpdateStopInput!) {
              updateStop(input: ${'$'}input) {
                __typename
              }
            }
        """.trimIndent()
        execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {}
            },
            deserializer = GraphQlResponse.serializer(EmptyMutationData.serializer()),
        )
    }

    suspend fun lastSyncStatus(): SuwayomiSyncStatusDto? {
        val query = """
            query LastSyncStatus {
              lastSyncStatus {
                ...AmatsubuSyncStatus
              }
            }

            $SYNC_STATUS_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(LastSyncStatusData.serializer()),
        ).lastSyncStatus
    }

    suspend fun startSync(): StartSyncPayload {
        val query = """
            mutation StartSync(${'$'}input: StartSyncInput!) {
              startSync(input: ${'$'}input) {
                result
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {}
            },
            deserializer = GraphQlResponse.serializer(StartSyncData.serializer()),
        ).startSync
    }

    suspend fun setMangaMeta(
        mangaId: Int,
        key: String,
        value: String,
    ): SuwayomiMangaMetaDto {
        val query = """
            mutation SetMangaMeta(${'$'}input: SetMangaMetaInput!) {
              setMangaMeta(input: ${'$'}input) {
                meta {
                  key
                  mangaId
                  value
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonObject("meta") {
                        put("key", key)
                        put("mangaId", mangaId)
                        put("value", value)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(SetMangaMetaData.serializer()),
        ).setMangaMeta.meta
    }

    suspend fun getChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val query = """
            mutation GetChaptersByMangaId(${'$'}input: FetchChaptersInput!) {
              fetchChapters(input: ${'$'}input) {
                chapters {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        val chapters = runCatching {
            execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("mangaId", mangaId)
                }
            },
            deserializer = GraphQlResponse.serializer(FetchChaptersData.serializer()),
            ).fetchChapters?.chapters.orEmpty()
        }.getOrElse { error ->
            if (error.message?.contains("No chapters found", ignoreCase = true) == true) {
                emptyList()
            } else {
                throw error
            }
        }
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getCachedChapters(mangaId: Int): List<SuwayomiChapterDto> {
        val query = """
            query GetCachedChaptersByMangaId(${'$'}mangaId: Int!) {
              chapters(
                filter: { mangaId: { equalTo: ${'$'}mangaId } }
                first: 10000
                order: [{ by: SOURCE_ORDER, byType: ASC }]
              ) {
                nodes {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        val chapters = execute(
            query = query,
            variables = buildJsonObject {
                put("mangaId", mangaId)
            },
            deserializer = GraphQlResponse.serializer(ChaptersData.serializer()),
        ).chapters.nodes
        snapshotCache?.storeChapters(serverKey(), mangaId, chapters)
        return chapters
    }

    suspend fun getChaptersSnapshot(mangaId: Int): SuwayomiSnapshot<List<SuwayomiChapterDto>>? {
        return snapshotCache?.getChapters(serverKey(), mangaId)
    }

    suspend fun getChapterPages(chapterId: Int): List<String> {
        val query = """
            mutation GetChapterPages(${'$'}input: FetchChapterPagesInput!) {
              fetchChapterPages(input: ${'$'}input) {
                pages
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("chapterId", chapterId)
                }
            },
            deserializer = GraphQlResponse.serializer(FetchChapterPagesData.serializer()),
        ).fetchChapterPages?.pages ?: error("Suwayomi server returned no chapter pages")
    }

    suspend fun getChapterPageManifest(chapterId: Int): SuwayomiChapterPageManifest {
        val query = """
            mutation GetChapterPageManifest(${'$'}input: FetchChapterPagesInput!) {
              fetchChapterPages(input: ${'$'}input) {
                pages
                chapter {
                  ...AmatsubuChapter
                  pageCount
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("chapterId", chapterId)
                }
            },
            deserializer = GraphQlResponse.serializer(FetchChapterPagesData.serializer()),
        ).fetchChapterPages
            ?.let { payload ->
                SuwayomiChapterPageManifest(
                    pages = payload.pages,
                    chapter = payload.chapter ?: error("Suwayomi server returned no manifest chapter"),
                )
            }
            ?: error("Suwayomi server returned no chapter page manifest")
    }

    suspend fun updateChapterProgress(
        chapterId: Int,
        isRead: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        val query = """
            mutation UpdateChapter(${'$'}input: UpdateChapterInput!) {
              updateChapter(input: ${'$'}input) {
                chapter {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", chapterId)
                    putJsonObject("patch") {
                        put("isRead", isRead)
                        put("lastPageRead", lastPageRead)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateChapterData.serializer()),
        ).updateChapter.chapter
    }

    suspend fun updateChapterRead(
        chapterId: Int,
        isRead: Boolean,
    ): SuwayomiChapterDto {
        val query = """
            mutation UpdateChapter(${'$'}input: UpdateChapterInput!) {
              updateChapter(input: ${'$'}input) {
                chapter {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", chapterId)
                    putJsonObject("patch") {
                        put("isRead", isRead)
                        if (!isRead) {
                            put("lastPageRead", 0)
                        }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateChapterData.serializer()),
        ).updateChapter.chapter
    }

    suspend fun updateChapterBookmark(
        chapterId: Int,
        isBookmarked: Boolean,
    ): SuwayomiChapterDto {
        val query = """
            mutation UpdateChapter(${'$'}input: UpdateChapterInput!) {
              updateChapter(input: ${'$'}input) {
                chapter {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", chapterId)
                    putJsonObject("patch") {
                        put("isBookmarked", isBookmarked)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateChapterData.serializer()),
        ).updateChapter.chapter
    }

    suspend fun updateChapterMigrationState(
        chapterId: Int,
        isRead: Boolean,
        isBookmarked: Boolean,
        lastPageRead: Int,
    ): SuwayomiChapterDto {
        val query = """
            mutation UpdateChapter(${'$'}input: UpdateChapterInput!) {
              updateChapter(input: ${'$'}input) {
                chapter {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", chapterId)
                    putJsonObject("patch") {
                        put("isRead", isRead)
                        put("isBookmarked", isBookmarked)
                        put("lastPageRead", lastPageRead)
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateChapterData.serializer()),
        ).updateChapter.chapter
    }

    suspend fun updateChaptersRead(
        chapterIds: List<Int>,
        isRead: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isRead = isRead,
            lastPageRead = 0.takeIf { !isRead },
        )
    }

    suspend fun updateChaptersBookmark(
        chapterIds: List<Int>,
        isBookmarked: Boolean,
    ): List<SuwayomiChapterDto> {
        return updateChapters(
            chapterIds = chapterIds,
            isBookmarked = isBookmarked,
        )
    }

    private suspend fun updateChapters(
        chapterIds: List<Int>,
        isRead: Boolean? = null,
        isBookmarked: Boolean? = null,
        lastPageRead: Int? = null,
    ): List<SuwayomiChapterDto> {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return emptyList()

        val query = """
            mutation UpdateChapters(${'$'}input: UpdateChaptersInput!) {
              updateChapters(input: ${'$'}input) {
                chapters {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        ids.forEach { add(it) }
                    }
                    putJsonObject("patch") {
                        isRead?.let { put("isRead", it) }
                        isBookmarked?.let { put("isBookmarked", it) }
                        lastPageRead?.let { put("lastPageRead", it) }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateChaptersData.serializer()),
        ).updateChapters.chapters
    }

    suspend fun trackProgress(mangaId: Int) {
        val query = """
            mutation TrackProgress(${'$'}input: TrackProgressInput!) {
              trackProgress(input: ${'$'}input) {
                __typename
              }
            }
        """.trimIndent()
        execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("mangaId", mangaId)
                }
            },
            deserializer = GraphQlResponse.serializer(EmptyMutationData.serializer()),
        )
    }

    suspend fun getTrackRecords(mangaId: Int): List<SuwayomiTrackRecordDto> {
        val query = """
            query TrackRecords(${'$'}mangaId: Int!) {
              trackRecords(condition: { mangaId: ${'$'}mangaId }) {
                nodes {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("mangaId", mangaId)
            },
            deserializer = GraphQlResponse.serializer(TrackRecordsData.serializer()),
        ).trackRecords.nodes
    }

    suspend fun searchTracker(
        trackerId: Int,
        queryText: String,
    ): List<SuwayomiTrackSearchDto> {
        val query = """
            query SearchTracker(${'$'}input: SearchTrackerInput!) {
              searchTracker(input: ${'$'}input) {
                trackSearches {
                  id
                  trackerId
                  remoteId
                  libraryId
                  title
                  lastChapterRead
                  totalChapters
                  trackingUrl
                  coverUrl
                  summary
                  publishingStatus
                  publishingType
                  startDate
                  status
                  score
                  startedReadingDate
                  finishedReadingDate
                  private
                }
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("trackerId", trackerId)
                    put("query", queryText)
                }
            },
            deserializer = GraphQlResponse.serializer(SearchTrackerData.serializer()),
        ).searchTracker.trackSearches
    }

    suspend fun bindTrack(
        mangaId: Int,
        trackerId: Int,
        remoteId: Long,
        private: Boolean = false,
    ): SuwayomiTrackRecordDto {
        val query = """
            mutation BindTrack(${'$'}input: BindTrackInput!) {
              bindTrack(input: ${'$'}input) {
                trackRecord {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("mangaId", mangaId)
                    put("trackerId", trackerId)
                    put("remoteId", remoteId.toString())
                    put("private", private)
                }
            },
            deserializer = GraphQlResponse.serializer(BindTrackData.serializer()),
        ).bindTrack.trackRecord
    }

    suspend fun bindTrackRecord(
        mangaId: Int,
        trackRecordId: Int,
    ): SuwayomiTrackRecordDto {
        val query = """
            mutation BindTrackRecord(${'$'}input: BindTrackRecordInput!) {
              bindTrackRecord(input: ${'$'}input) {
                trackRecord {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("mangaId", mangaId)
                    put("trackRecordId", trackRecordId)
                }
            },
            deserializer = GraphQlResponse.serializer(BindTrackRecordData.serializer()),
        ).bindTrackRecord.trackRecord
    }

    suspend fun fetchTrack(recordId: Int): SuwayomiTrackRecordDto {
        val query = """
            mutation FetchTrack(${'$'}input: FetchTrackInput!) {
              fetchTrack(input: ${'$'}input) {
                trackRecord {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("recordId", recordId)
                }
            },
            deserializer = GraphQlResponse.serializer(FetchTrackData.serializer()),
        ).fetchTrack.trackRecord
    }

    suspend fun updateTrack(
        recordId: Int,
        status: Int? = null,
        lastChapterRead: Double? = null,
        scoreString: String? = null,
        startDate: Long? = null,
        finishDate: Long? = null,
        private: Boolean? = null,
    ): SuwayomiTrackRecordDto? {
        val query = """
            mutation UpdateTrack(${'$'}input: UpdateTrackInput!) {
              updateTrack(input: ${'$'}input) {
                trackRecord {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("recordId", recordId)
                    if (status != null) put("status", status)
                    if (lastChapterRead != null) put("lastChapterRead", lastChapterRead)
                    if (scoreString != null) put("scoreString", scoreString)
                    if (startDate != null) put("startDate", startDate.toString())
                    if (finishDate != null) put("finishDate", finishDate.toString())
                    if (private != null) put("private", private)
                }
            },
            deserializer = GraphQlResponse.serializer(UpdateTrackData.serializer()),
        ).updateTrack.trackRecord
    }

    suspend fun unbindTrack(
        recordId: Int,
        deleteRemoteTrack: Boolean = false,
    ): SuwayomiTrackRecordDto? {
        val query = """
            mutation UnbindTrack(${'$'}input: UnbindTrackInput!) {
              unbindTrack(input: ${'$'}input) {
                trackRecord {
                  ...AmatsubuTrackRecord
                }
              }
            }

            $TRACKER_FRAGMENT
            $TRACK_RECORD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("recordId", recordId)
                    put("deleteRemoteTrack", deleteRemoteTrack)
                }
            },
            deserializer = GraphQlResponse.serializer(UnbindTrackData.serializer()),
        ).unbindTrack.trackRecord
    }

    suspend fun getDownloadStatus(): SuwayomiDownloadStatusDto {
        val query = """
            query GetDownloadStatus {
              downloadStatus {
                ...AmatsubuDownloadStatus
              }
            }

            $DOWNLOAD_STATUS_FRAGMENT
            $DOWNLOAD_FRAGMENT
        """.trimIndent()
        val status = execute(
            query = query,
            deserializer = GraphQlResponse.serializer(DownloadStatusData.serializer()),
        ).downloadStatus
        snapshotCache?.storeDownloadStatus(serverKey(), status)
        return status
    }

    suspend fun getDownloadStatusSnapshot(): SuwayomiSnapshot<SuwayomiDownloadStatusDto>? {
        return snapshotCache?.getDownloadStatus(serverKey())
    }

    suspend fun enqueueChapterDownloads(chapterIds: List<Int>) {
        val query = """
            mutation EnqueueChapterDownloads(${'$'}input: EnqueueChapterDownloadsInput!) {
              enqueueChapterDownloads(input: ${'$'}input) {
                __typename
              }
            }
        """.trimIndent()
        execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        chapterIds.forEach { add(it) }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(EmptyMutationData.serializer()),
        )
    }

    suspend fun dequeueChapterDownload(chapterId: Int) {
        val query = """
            mutation DequeueChapterDownloads(${'$'}input: DequeueChapterDownloadInput!) {
              dequeueChapterDownload(input: ${'$'}input) {
                __typename
              }
            }
        """.trimIndent()
        execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("id", chapterId)
                }
            },
            deserializer = GraphQlResponse.serializer(EmptyMutationData.serializer()),
        )
    }

    suspend fun dequeueChapterDownloads(chapterIds: List<Int>): SuwayomiDownloadStatusDto {
        val ids = chapterIds.distinct()
        if (ids.isEmpty()) return getDownloadStatus()

        val query = """
            mutation DequeueChapterDownloads(${'$'}input: DequeueChapterDownloadsInput!) {
              dequeueChapterDownloads(input: ${'$'}input) {
                downloadStatus {
                  ...AmatsubuDownloadStatus
                }
              }
            }

            $DOWNLOAD_STATUS_FRAGMENT
            $DOWNLOAD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        ids.forEach { add(it) }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(DequeueChapterDownloadsData.serializer()),
        ).dequeueChapterDownloads.downloadStatus
    }

    suspend fun startDownloader() {
        executeDownloaderMutation(
            """
                mutation StartDownloader(${'$'}input: StartDownloaderInput!) {
                  startDownloader(input: ${'$'}input) {
                    __typename
                  }
                }
            """.trimIndent(),
        )
    }

    suspend fun stopDownloader() {
        executeDownloaderMutation(
            """
                mutation StopDownloader(${'$'}input: StopDownloaderInput!) {
                  stopDownloader(input: ${'$'}input) {
                    __typename
                  }
                }
            """.trimIndent(),
        )
    }

    suspend fun clearDownloader() {
        executeDownloaderMutation(
            """
                mutation ClearDownloader(${'$'}input: ClearDownloaderInput!) {
                  clearDownloader(input: ${'$'}input) {
                    __typename
                  }
                }
            """.trimIndent(),
        )
    }

    suspend fun reorderChapterDownload(chapterId: Int, to: Int): SuwayomiDownloadStatusDto {
        val query = """
            mutation ReorderChapterDownload(${'$'}input: ReorderChapterDownloadInput!) {
              reorderChapterDownload(input: ${'$'}input) {
                downloadStatus {
                  ...AmatsubuDownloadStatus
                }
              }
            }

            $DOWNLOAD_STATUS_FRAGMENT
            $DOWNLOAD_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    put("chapterId", chapterId)
                    put("to", to)
                }
            },
            deserializer = GraphQlResponse.serializer(ReorderChapterDownloadData.serializer()),
        ).reorderChapterDownload.downloadStatus
    }

    suspend fun deleteDownloadedChapters(chapterIds: List<Int>): List<SuwayomiChapterDto> {
        val query = """
            mutation DeleteDownloadedChapters(${'$'}input: DeleteDownloadedChaptersInput!) {
              deleteDownloadedChapters(input: ${'$'}input) {
                chapters {
                  ...AmatsubuChapter
                }
              }
            }

            $CHAPTER_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {
                    putJsonArray("ids") {
                        chapterIds.forEach { add(it) }
                    }
                }
            },
            deserializer = GraphQlResponse.serializer(DeleteDownloadedChaptersData.serializer()),
        ).deleteDownloadedChapters.chapters
    }

    suspend fun serverAbout(): SuwayomiServerAboutDto {
        val query = """
            query ServerAbout {
              aboutServer {
                name
                version
                revision
                buildType
                buildTime
                github
                discord
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(ServerAboutData.serializer()),
        ).aboutServer
    }

    suspend fun webUiAbout(): SuwayomiWebUiAboutDto {
        val query = """
            query WebUiAbout {
              aboutWebUI {
                channel
                tag
                updateTimestamp
              }
            }
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(WebUiAboutData.serializer()),
        ).aboutWebUI
    }

    suspend fun serverSettings(): SuwayomiServerSettingsDto {
        val query = """
            query ServerSettings {
              settings {
                ...AmatsubuSettings
              }
            }

            $SETTINGS_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            deserializer = GraphQlResponse.serializer(ServerSettingsData.serializer()),
        ).settings
    }

    suspend fun setSettings(settings: JsonObject): SuwayomiServerSettingsDto {
        val query = """
            mutation SetSettings(${'$'}settings: PartialSettingsTypeInput!) {
              setSettings(input: { settings: ${'$'}settings }) {
                settings {
                  ...AmatsubuSettings
                }
              }
            }

            $SETTINGS_FRAGMENT
        """.trimIndent()
        return execute(
            query = query,
            variables = buildJsonObject {
                put("settings", settings)
            },
            deserializer = GraphQlResponse.serializer(SetSettingsData.serializer()),
        ).setSettings.settings
    }

    suspend fun setExtensionRepos(extensionRepos: List<String>): SuwayomiServerSettingsDto {
        return setSettings(
            buildJsonObject {
                putJsonArray("extensionRepos") {
                    extensionRepos.forEach { add(it) }
                }
            },
        )
    }

    private suspend fun <T> execute(
        query: String,
        variables: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        deserializer: DeserializationStrategy<GraphQlResponse<T>>,
    ): T {
        val response = executeResponse(
            query = query,
            variables = variables,
            deserializer = deserializer,
        )

        if (response.errors.isNotEmpty()) {
            error(response.errors.joinToString("; ") { it.message })
        }

        return response.data ?: error("Suwayomi server returned no data")
    }

    private fun serverKey(): String = endpoint().trimEnd('/')

    private suspend fun <T> executePartial(
        query: String,
        variables: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        deserializer: DeserializationStrategy<GraphQlResponse<T>>,
    ): GraphQlPartialResponse<T> {
        val response = executeResponse(
            query = query,
            variables = variables,
            deserializer = deserializer,
        )
        return GraphQlPartialResponse(
            data = response.data,
            errors = response.errors,
        )
    }

    private suspend fun <T> executeResponse(
        query: String,
        variables: kotlinx.serialization.json.JsonObject = buildJsonObject { },
        deserializer: DeserializationStrategy<GraphQlResponse<T>>,
    ): GraphQlResponse<T> {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        return client.newCall(
            POST(
                endpoint(),
                body = payload.toString().toRequestBody(jsonMime),
            ),
        )
            .awaitSuccess()
            .use {
                withContext(Dispatchers.IO) {
                    with(json) {
                        decodeFromJsonResponse(deserializer, it)
                    }
                }
            }
    }

    private suspend fun executeDownloaderMutation(query: String) {
        execute(
            query = query,
            variables = buildJsonObject {
                putJsonObject("input") {}
            },
            deserializer = GraphQlResponse.serializer(EmptyMutationData.serializer()),
        )
    }

    private fun JsonObjectBuilder.putFilterChange(change: SuwayomiSourceFilterChange) {
        put("position", change.position)
        change.checkBoxState?.let { put("checkBoxState", it) }
        change.groupChange?.let { nestedChange ->
            putJsonObject("groupChange") {
                putFilterChange(nestedChange)
            }
        }
        change.selectState?.let { put("selectState", it) }
        change.sortState?.let { selection ->
            putJsonObject("sortState") {
                put("index", selection.index)
                put("ascending", selection.ascending)
            }
        }
        change.textState?.let { put("textState", it) }
        change.triState?.let { put("triState", it.name) }
    }

    private fun JsonObjectBuilder.putPreferenceChange(change: SuwayomiSourcePreferenceChange) {
        put("position", change.position)
        change.checkBoxState?.let { put("checkBoxState", it) }
        change.editTextState?.let { put("editTextState", it) }
        change.listState?.let { put("listState", it) }
        change.multiSelectState?.let { values ->
            putJsonArray("multiSelectState") {
                values.forEach { add(it) }
            }
        }
        change.switchState?.let { put("switchState", it) }
    }

    private companion object {
        private val EXTENSION_STORE_FRAGMENT = """
            fragment AmatsubuExtensionStore on ExtensionStoreType {
              name
              badgeLabel
              signingKey
              contactWebsite
              contactDiscord
              indexUrl
              isLegacy
              extensionListUrl
            }
        """.trimIndent()

        private val CATEGORY_FRAGMENT = """
            fragment AmatsubuCategory on CategoryType {
              id
              name
              order
              includeInDownload
              includeInUpdate
            }
        """.trimIndent()

        private val TRACKER_FRAGMENT = """
            fragment AmatsubuTracker on TrackerType {
              id
              name
              icon
              isLoggedIn
              authUrl
              supportsTrackDeletion
              supportsReadingDates
              supportsPrivateTracking
              statuses {
                value
                name
              }
              scores
            }
        """.trimIndent()

        private val TRACK_RECORD_FRAGMENT = """
            fragment AmatsubuTrackRecord on TrackRecordType {
              id
              mangaId
              trackerId
              remoteId
              libraryId
              title
              lastChapterRead
              totalChapters
              status
              score
              displayScore
              remoteUrl
              startDate
              finishDate
              private
              tracker {
                ...AmatsubuTracker
              }
            }
        """.trimIndent()

        private val MANGA_FRAGMENT = """
            fragment AmatsubuManga on MangaType {
              artist
              author
              description
              downloadCount
              genre
              id
              inLibrary
              inLibraryAt
              initialized
              chaptersLastFetchedAt
              lastFetchedAt
              latestFetchedChapter {
                fetchedAt
              }
              latestReadChapter {
                lastReadAt
              }
              latestUploadedChapter {
                uploadDate
              }
              meta {
                key
                mangaId
                value
              }
              realUrl
              sourceId
              status
              thumbnailUrl
              title
              unreadCount
              updateStrategy
              url
            }
        """.trimIndent()

        private val CHAPTER_FRAGMENT = """
            fragment AmatsubuChapter on ChapterType {
              chapterNumber
              fetchedAt
              id
              isDownloaded
              isBookmarked
              isRead
              lastPageRead
              lastReadAt
              mangaId
              name
              realUrl
              scanlator
              sourceOrder
              uploadDate
              url
            }
        """.trimIndent()

        private val SETTINGS_FRAGMENT = """
            fragment AmatsubuSettings on SettingsType {
              authMode
              authPassword
              authUsername
              autoDownloadIgnoreReUploads
              autoDownloadNewChapters
              autoDownloadNewChaptersLimit
              autoBackupIncludeCategories
              autoBackupIncludeChapters
              autoBackupIncludeClientData
              autoBackupIncludeHistory
              autoBackupIncludeManga
              autoBackupIncludeServerSettings
              autoBackupIncludeTracking
              backupInterval
              backupPath
              backupTTL
              backupTime
              basicAuthEnabled
              basicAuthPassword
              basicAuthUsername
              ip
              port
              downloadAsCbz
              downloadsPath
              electronPath
              excludeCompleted
              excludeEntryWithUnreadChapters
              excludeNotStarted
              excludeUnreadChapters
              extensionRepos
              flareSolverrAsResponseFallback
              globalUpdateInterval
              initialOpenInBrowserEnabled
              localSourcePath
              maxLogFileSize
              maxLogFiles
              maxLogFolderSize
              maxSourcesInParallel
              socksProxyEnabled
              socksProxyHost
              socksProxyPassword
              socksProxyPort
              socksProxyUsername
              socksProxyVersion
              flareSolverrEnabled
              flareSolverrSessionName
              flareSolverrSessionTtl
              flareSolverrTimeout
              flareSolverrUrl
              debugLogsEnabled
              systemTrayEnabled
              updateMangas
              webUIChannel
              webUIFlavor
              webUIInterface
              webUIUpdateCheckInterval
            }
        """.trimIndent()

        private val DOWNLOAD_FRAGMENT = """
            fragment AmatsubuDownload on DownloadType {
              chapter {
                id
                name
                chapterNumber
                uploadDate
                sourceOrder
                isDownloaded
              }
              manga {
                id
                title
                downloadCount
                thumbnailUrl
              }
              progress
              state
              tries
              position
            }
        """.trimIndent()

        private val DOWNLOAD_STATUS_FRAGMENT = """
            fragment AmatsubuDownloadStatus on DownloadStatus {
              state
              queue {
                ...AmatsubuDownload
              }
            }
        """.trimIndent()

        private val SYNC_STATUS_FRAGMENT = """
            fragment AmatsubuSyncStatus on SyncStatus {
              state
              startDate
              endDate
              backupRestoreId
              errorMessage
            }
        """.trimIndent()

        private val SOURCE_FILTER_FRAGMENT = """
            fragment AmatsubuSourceFilter on Filter {
              __typename
              ... on CheckBoxFilter {
                name
                defaultBoolean: default
              }
              ... on HeaderFilter {
                name
              }
              ... on SeparatorFilter {
                name
              }
              ... on SelectFilter {
                name
                defaultInt: default
                values
              }
              ... on SortFilter {
                name
                values
                defaultSort: default {
                  index
                  ascending
                }
              }
              ... on TextFilter {
                name
                defaultString: default
              }
              ... on TriStateFilter {
                name
                defaultTriState: default
              }
              ... on GroupFilter {
                name
                filters {
                  ...AmatsubuSourceFilterChild
                }
              }
            }
        """.trimIndent()

        private val SOURCE_FILTER_CHILD_FRAGMENT = """
            fragment AmatsubuSourceFilterChild on Filter {
              __typename
              ... on CheckBoxFilter {
                name
                defaultBoolean: default
              }
              ... on HeaderFilter {
                name
              }
              ... on SeparatorFilter {
                name
              }
              ... on SelectFilter {
                name
                defaultInt: default
                values
              }
              ... on SortFilter {
                name
                values
                defaultSort: default {
                  index
                  ascending
                }
              }
              ... on TextFilter {
                name
                defaultString: default
              }
              ... on TriStateFilter {
                name
                defaultTriState: default
              }
            }
        """.trimIndent()

        private val SOURCE_PREFERENCE_FRAGMENT = """
            fragment AmatsubuSourcePreference on Preference {
              __typename
              ... on CheckBoxPreference {
                key
                title
                summary
                enabled
                visible
                currentBoolean: currentValue
                defaultBoolean: default
              }
              ... on SwitchPreference {
                key
                title
                summary
                enabled
                visible
                currentBoolean: currentValue
                defaultBoolean: default
              }
              ... on EditTextPreference {
                key
                title
                summary
                enabled
                visible
                currentString: currentValue
                defaultString: default
                text
                dialogTitle
                dialogMessage
              }
              ... on ListPreference {
                key
                title
                summary
                enabled
                visible
                currentString: currentValue
                defaultString: default
                entries
                entryValues
              }
              ... on MultiSelectListPreference {
                key
                title
                summary
                enabled
                visible
                currentStringList: currentValue
                defaultStringList: default
                entries
                entryValues
                dialogTitle
                dialogMessage
              }
            }
        """.trimIndent()
    }
}

enum class FetchSourceMangaType {
    SEARCH,
    POPULAR,
    LATEST,
}
