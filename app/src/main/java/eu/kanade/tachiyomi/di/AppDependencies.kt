package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.await
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.storage.service.StoragePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.updates.service.UpdatesPreferences
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.suwayomi.AndroidSuwayomiTokenStore
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopyStore
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadataStore
import eu.kanade.tachiyomi.data.suwayomi.ServerReadStatePendingStore
import eu.kanade.tachiyomi.data.suwayomi.ServerReaderIntentPendingStore
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSnapshotCache
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiUpdatesWidgetDataSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.Database
import tachiyomi.presentation.widget.UpdatesWidgetDataSource

internal class AppDependencies(
    val application: Application,
    val basePreferences: BasePreferences,
    val chapterCache: ChapterCache,
    val clientDeviceChapterCopyStore: ClientDeviceChapterCopyStore,
    val clientMangaMetadataStore: ClientMangaMetadataStore,
    val coverCache: CoverCache,
    val getIncognitoState: GetIncognitoState,
    val imageSaver: ImageSaver,
    val libraryPreferences: LibraryPreferences,
    val networkHelper: NetworkHelper,
    val networkPreferences: NetworkPreferences,
    val preferenceStore: PreferenceStore,
    val protoBuf: ProtoBuf,
    val json: Json,
    val readerPreferences: ReaderPreferences,
    val serverReadStatePendingStore: ServerReadStatePendingStore,
    val serverReaderIntentPendingStore: ServerReaderIntentPendingStore,
    val securityPreferences: SecurityPreferences,
    val sourcePreferences: SourcePreferences,
    val storagePreferences: StoragePreferences,
    val suwayomiClientProvider: SuwayomiClientProvider,
    val updatesPreferences: UpdatesPreferences,
    val updatesWidgetDataSource: UpdatesWidgetDataSource,
    val uiPreferences: UiPreferences,
)

internal val Context.appDependencies: AppDependencies
    get() = (applicationContext as App).dependencies

internal fun createAppDependencies(application: Application): AppDependencies {
    val preferenceStore = AndroidPreferenceStore(application)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    val databaseDriver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.FileProvider(application, "tachiyomi.db"),
        schema = Database.Schema,
        configuration = AndroidxSqliteConfiguration(
            isForeignKeyConstraintsEnabled = true,
        ),
    )
    val database = Database(driver = databaseDriver)
    ensureClientMangaMetadataTable(databaseDriver)
    val basePreferences = BasePreferences(application, preferenceStore)
    val sourcePreferences = SourcePreferences(preferenceStore)
    val networkPreferences = NetworkPreferences(
        preferenceStore = preferenceStore,
        verboseLoggingDefault = isDebugBuildType,
    )
    val networkHelper = NetworkHelper(application, networkPreferences)
    val snapshotCache = SuwayomiSnapshotCache(database, json)
    val suwayomiClientProvider = SuwayomiClientProvider(
        preferenceStore = preferenceStore,
        networkHelper = networkHelper,
        json = json,
        snapshotCache = snapshotCache,
        tokenStore = AndroidSuwayomiTokenStore(application),
    )

    return AppDependencies(
        application = application,
        basePreferences = basePreferences,
        chapterCache = ChapterCache(application, json),
        clientDeviceChapterCopyStore = ClientDeviceChapterCopyStore(database),
        clientMangaMetadataStore = ClientMangaMetadataStore(database, json),
        coverCache = CoverCache(application),
        getIncognitoState = GetIncognitoState(basePreferences, sourcePreferences),
        imageSaver = ImageSaver(application),
        libraryPreferences = LibraryPreferences(preferenceStore),
        networkHelper = networkHelper,
        networkPreferences = networkPreferences,
        preferenceStore = preferenceStore,
        protoBuf = ProtoBuf,
        json = json,
        readerPreferences = ReaderPreferences(preferenceStore),
        serverReadStatePendingStore = ServerReadStatePendingStore(database),
        serverReaderIntentPendingStore = ServerReaderIntentPendingStore(database),
        securityPreferences = SecurityPreferences(preferenceStore),
        sourcePreferences = sourcePreferences,
        storagePreferences = StoragePreferences(
            folderProvider = AndroidStorageFolderProvider(application),
            preferenceStore = preferenceStore,
        ),
        suwayomiClientProvider = suwayomiClientProvider,
        updatesPreferences = UpdatesPreferences(preferenceStore),
        updatesWidgetDataSource = SuwayomiUpdatesWidgetDataSource(suwayomiClientProvider),
        uiPreferences = UiPreferences(preferenceStore),
    )
}

/**
 * Some existing installs reached schema version 18 before the generated
 * metadata migration was present. Keep this idempotent safeguard until all
 * supported installs have crossed that version boundary.
 */
private fun ensureClientMangaMetadataTable(driver: AndroidxSqliteDriver) {
    runBlocking {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS client_manga_metadata(
                    server_key TEXT NOT NULL,
                    source_id INTEGER NOT NULL,
                    manga_id INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    memo TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(server_key, source_id, manga_id)
                )
            """.trimIndent(),
            parameters = 0,
        ).await()
        driver.execute(
            identifier = null,
            sql = """
                CREATE INDEX IF NOT EXISTS client_manga_metadata_server_manga_index
                ON client_manga_metadata(server_key, manga_id)
            """.trimIndent(),
            parameters = 0,
        ).await()
    }
}
