package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestoreResult
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadata
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadataStore
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.data.suwayomi.serverBackupRestoreAffectedEntities
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

internal class ServerBackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,
    private val suwayomiProvider: SuwayomiClientProvider,
) {
    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.restoring_backup),
            0,
            1,
            isSync,
        )

        restoreOnServer(uri)
        ServerStateSync.requestRefresh(*serverBackupRestoreAffectedEntities().toTypedArray())

        notifier.showRestoreComplete(
            time = System.currentTimeMillis() - startTime,
            errorCount = 0,
            path = null,
            file = null,
            sync = isSync,
        )
    }

    private suspend fun restoreOnServer(uri: Uri) {
        val request = POST(
            url = suwayomiProvider.restUrl("/api/v1/backup/import"),
            body = readBackupBytes(uri).toRequestBody(PROTOBUF_BACKUP_MEDIA_TYPE),
        )

        suwayomiProvider.httpClient.newCall(request).awaitSuccess().close()
    }

    private fun readBackupBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read backup file")
    }
}

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,
    private val decoder: BackupDecoder,
    private val preferenceStore: PreferenceStore,
    private val preferenceRestorer: PreferenceRestorer,
    private val mangaMetadataStore: ClientMangaMetadataStore,
    private val currentServerKey: () -> String,
) {

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.restoring_backup),
            0,
            1,
            isSync,
        )

        val restoreOutcome = ClientBackupRestoreApplier(
            preferenceStore = preferenceStore,
            preferenceRestorer = PreferenceRestorerAdapter(preferenceRestorer),
            mangaMetadataRestorer = ClientMangaMetadataStoreAdapter(mangaMetadataStore),
            currentServerKey = currentServerKey,
        ).apply(decoder.decode(uri), options)

        logcat(LogPriority.INFO) {
            "Client backup preference restore complete: " +
                "restored=${restoreOutcome.restoreResult.restored} " +
                "failed=${restoreOutcome.restoreResult.failed}"
        }

        notifier.showRestoreComplete(
            time = System.currentTimeMillis() - startTime,
            errorCount = restoreOutcome.restoreResult.failed +
                restoreOutcome.compatibility.summary.unsupportedCount,
            path = null,
            file = null,
            sync = isSync,
        )
    }
}

internal class ClientBackupRestoreApplier(
    private val preferenceStore: PreferenceStore,
    private val preferenceRestorer: ClientBackupPreferenceRestorer,
    private val mangaMetadataRestorer: ClientBackupMangaMetadataRestorer,
    private val currentServerKey: () -> String,
) {

    suspend fun apply(backup: Backup, options: RestoreOptions): ClientBackupRestoreOutcome {
        val appPreferenceDefaults = ClientPreferenceRestoreSchema.defaultsWith(
            backup.backupPreferences.map { it.key },
        )
        val compatibility = BackupCompatibilityPolicy(
            appPreferences = appPreferenceDefaults + preferenceStore.getAll(),
            sourcePreferences = emptyMap(),
        ).evaluate(backup, options)

        compatibility.summary.log()
        if (options.appSettings) {
            mangaMetadataRestorer.restoreForServer(
                serverKey = currentServerKey(),
                metadata = backup.clientMangaMetadata.map { it.toClientMetadata() },
            )
        }

        val appRestoreResult = preferenceRestorer.restoreApp(
            compatibility.restorable.appPreferences,
            defaultValues = appPreferenceDefaults,
        )
        val sourceRestoreResult = preferenceRestorer.restoreSource(compatibility.restorable.sourcePreferences)

        return ClientBackupRestoreOutcome(
            compatibility = compatibility,
            restoreResult = appRestoreResult + sourceRestoreResult,
        )
    }

    private fun BackupCompatibilitySummary.log() {
        decisions.forEach { decision ->
            logcat(LogPriority.INFO) {
                "Client backup restore compatibility: " +
                    "${decision.decision} ${decision.section} " +
                    "count=${decision.count} reason=${decision.reason}"
            }
        }
    }
}

internal data class ClientBackupRestoreOutcome(
    val compatibility: BackupCompatibilityResult,
    val restoreResult: PreferenceRestoreResult,
)

internal interface ClientBackupPreferenceRestorer {
    fun restoreApp(
        preferences: List<BackupPreference>,
        defaultValues: Map<String, Any>,
    ): PreferenceRestoreResult

    fun restoreSource(preferences: List<BackupSourcePreferences>): PreferenceRestoreResult
}

internal interface ClientBackupMangaMetadataRestorer {
    suspend fun restoreForServer(
        serverKey: String,
        metadata: List<ClientMangaMetadata>,
    )
}

private class PreferenceRestorerAdapter(
    private val delegate: PreferenceRestorer,
) : ClientBackupPreferenceRestorer {
    override fun restoreApp(
        preferences: List<BackupPreference>,
        defaultValues: Map<String, Any>,
    ): PreferenceRestoreResult {
        return delegate.restoreApp(preferences, defaultValues)
    }

    override fun restoreSource(preferences: List<BackupSourcePreferences>): PreferenceRestoreResult {
        return delegate.restoreSource(preferences)
    }
}

private class ClientMangaMetadataStoreAdapter(
    private val delegate: ClientMangaMetadataStore,
) : ClientBackupMangaMetadataRestorer {
    override suspend fun restoreForServer(
        serverKey: String,
        metadata: List<ClientMangaMetadata>,
    ) {
        delegate.restoreForServer(serverKey, metadata)
    }
}

private val PROTOBUF_BACKUP_MEDIA_TYPE = "application/octet-stream".toMediaType()
