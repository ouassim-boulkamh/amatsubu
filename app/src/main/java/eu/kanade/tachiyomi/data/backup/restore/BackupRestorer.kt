package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.suwayomi.ServerStateSync
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import eu.kanade.tachiyomi.source.sourcePreferences
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ServerBackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,
) {
    private val suwayomiProvider = SuwayomiClientProvider()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.restoring_backup),
            0,
            1,
            isSync,
        )

        restoreOnServer(uri)
        ServerStateSync.requestRefresh()

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
    private val decoder: BackupDecoder = BackupDecoder(context),
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context, preferenceStore),
) {

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        notifier.showRestoreProgress(
            context.stringResource(MR.strings.restoring_backup),
            0,
            1,
            isSync,
        )

        val backup = decoder.decode(uri)
        val appPreferenceDefaults = ClientPreferenceRestoreSchema.defaultsWith(
            backup.backupPreferences.map { it.key },
        )
        val compatibility = BackupCompatibilityPolicy(
            appPreferences = appPreferenceDefaults + preferenceStore.getAll(),
            sourcePreferences = sourceManager.getLocalConfigurableSourcePreferences(),
        ).evaluate(backup, options)

        compatibility.summary.log()
        val appRestoreResult = preferenceRestorer.restoreApp(
            compatibility.restorable.appPreferences,
            defaultValues = appPreferenceDefaults,
        )
        val sourceRestoreResult = preferenceRestorer.restoreSource(compatibility.restorable.sourcePreferences)
        val restoreResult = appRestoreResult + sourceRestoreResult

        logcat(LogPriority.INFO) {
            "Client backup preference restore complete: " +
                "restored=${restoreResult.restored} failed=${restoreResult.failed}"
        }

        notifier.showRestoreComplete(
            time = System.currentTimeMillis() - startTime,
            errorCount = restoreResult.failed + compatibility.summary.unsupportedCount,
            path = null,
            file = null,
            sync = isSync,
        )
    }

    private fun SourceManager.getLocalConfigurableSourcePreferences(): Map<String, Map<String, *>> {
        return getAll()
            .filterIsInstance<ConfigurableSource>()
            .associate { source ->
                source.preferenceKey() to AndroidPreferenceStore(
                    context = context,
                    sharedPreferences = source.sourcePreferences(),
                ).getAll()
            }
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

private val PROTOBUF_BACKUP_MEDIA_TYPE = "application/octet-stream".toMediaType()
