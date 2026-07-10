package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.ServerBackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.toBackupMetadata
import eu.kanade.tachiyomi.data.suwayomi.ClientMangaMetadataStore
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupCreator(
    private val context: Context,
    private val parser: ProtoBuf,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val mangaMetadataStore: ClientMangaMetadataStore,
    private val currentServerKey: () -> String,
    private val validator: BackupFileValidator,
) {

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = UniFile.fromUri(context, uri)

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val backup = createBackup(options)
            val backupBytes = encodeBackup(backup, parser)

            file.openOutputStream()
                .also {
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use { output ->
                    output.write(backupBytes)
                }

            val fileUri = file.uri

            validator.validate(fileUri)

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    private suspend fun createBackup(options: BackupOptions): Backup {
        return Backup(
            backupManga = emptyList(),
            backupPreferences = if (options.appSettings) {
                preferenceBackupCreator.createApp(options.privateSettings)
            } else {
                emptyList()
            },
            backupSourcePreferences = if (options.sourceSettings) {
                preferenceBackupCreator.createSource(options.privateSettings)
            } else {
                emptyList()
            },
            // Metadata belongs to the client backup's app-settings scope, not
            // to server-owned library/category/chapter backup sections.
            clientMangaMetadata = if (options.appSettings) {
                mangaMetadataStore.forServer(currentServerKey()).map { it.toBackupMetadata() }
            } else {
                emptyList()
            },
        )
    }

    companion object {
        fun encodeBackup(backup: Backup, parser: ProtoBuf = ProtoBuf): ByteArray {
            return parser.encodeToByteArray(Backup.serializer(), backup)
        }

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}

internal class ServerBackupCreator(
    private val context: Context,
    private val suwayomiProvider: SuwayomiClientProvider,
    private val validator: ServerBackupFileValidator,
) {
    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = UniFile.fromUri(context, uri)

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val backupBytes = suwayomiProvider.httpClient
                .newCall(GET(suwayomiProvider.restUrl("/api/v1/backup/export/file")))
                .awaitSuccess()
                .use { response ->
                    response.body.bytes()
                }

            if (backupBytes.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }

            validator.validateBytes(backupBytes)

            file.openOutputStream()
                .also {
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .use { output ->
                    output.write(backupBytes)
                }

            val fileUri = file.uri

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    companion object {
        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
