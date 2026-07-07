package eu.kanade.tachiyomi.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiChapterWithMangaDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiDownloadStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiExtensionDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSyncStatusDto
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiUpdaterJobsInfoDto
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class ServerNotificationRenderer(
    private val context: Context,
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {
    fun showLibraryProgress(jobs: SuwayomiUpdaterJobsInfoDto) {
        val total = jobs.totalJobs.coerceAtLeast(0)
        val finished = jobs.finishedJobs.coerceIn(0, total.coerceAtLeast(jobs.finishedJobs))
        val progressLabel = if (total > 0) "$finished/$total" else finished.toString()
        val notification = context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.label_library))
            setContentText(context.stringResource(MR.strings.notification_updating_progress, progressLabel))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
            setContentIntent(openAppPendingIntent())
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_stop_library_update),
                NotificationReceiver.stopLibraryUpdatePendingBroadcast(context),
            )
            if (total > 0) {
                setProgress(total, finished, false)
            } else {
                setProgress(0, 0, true)
            }
        }.build()

        context.notify(Notifications.ID_LIBRARY_PROGRESS, notification)
    }

    fun cancelLibraryProgress() {
        context.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
    }

    fun showNewChapters(chapters: List<SuwayomiChapterWithMangaDto>) {
        if (chapters.isEmpty()) return

        val mangaIds = chapters.map { it.mangaId }.distinct()
        val notificationId = if (mangaIds.size == 1) {
            mangaIds.first().hashCode()
        } else {
            Notifications.ID_NEW_CHAPTERS
        }
        val contentIntent = if (mangaIds.size == 1) {
            NotificationHandler.openMangaPendingActivity(context, mangaIds.first().toLong())
        } else {
            NotificationHandler.openUpdatesPendingActivity(context)
        }
        val chapterCount = chapters.size
        val mangaCount = mangaIds.size
        val title = context.stringResource(MR.strings.notification_new_chapters)
        val text = context.pluralStringResource(
            MR.plurals.notification_chapters_generic,
            chapterCount,
            chapterCount,
        ) + " - " + context.pluralStringResource(
            MR.plurals.notification_new_chapters_summary,
            mangaCount,
            mangaCount,
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(text)
        ServerNotificationContent.newChapterLines(chapters, hideContent, MAX_NOTIFICATION_LINES)
            .forEach(style::addLine)

        val notification = context.notificationBuilder(Notifications.CHANNEL_NEW_CHAPTERS) {
            setContentTitle(title)
            setContentText(text)
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setStyle(style)
            setAutoCancel(true)
            setContentIntent(contentIntent)
            setGroup(Notifications.GROUP_NEW_CHAPTERS)
            setOnlyAlertOnce(false)
        }.build()

        context.notify(notificationId, notification)
    }

    fun showDownloadNotifications(
        status: SuwayomiDownloadStatusDto,
        visibleQueue: List<SuwayomiDownloadDto>,
        errorDownloads: List<SuwayomiDownloadDto>,
    ) {
        updateDownloadErrorNotification(errorDownloads)
        showDownloadProgress(status, visibleQueue)
    }

    fun cancelDownloadError() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_ERROR)
    }

    fun cancelDownloadNotifications() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_PROGRESS)
        context.cancelNotification(Notifications.ID_DOWNLOAD_ERROR)
    }

    fun showSyncYomiProgress(status: SuwayomiSyncStatusDto) {
        val notification = context.notificationBuilder(Notifications.CHANNEL_SYNCYOMI_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.action_syncyomi))
            setContentText(status.syncYomiDisplayText())
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
            setContentIntent(openAppPendingIntent())
            setProgress(0, 0, true)
        }.build()

        context.notify(Notifications.ID_SYNCYOMI_PROGRESS, notification)
    }

    fun showSyncYomiTerminal(status: SuwayomiSyncStatusDto) {
        val isError = status.state.equals("ERROR", ignoreCase = true)
        val contentText = when {
            isError && !hideContent -> status.errorMessage?.takeIf(String::isNotBlank)
                ?: context.stringResource(MR.strings.syncyomi_failed)
            isError -> context.stringResource(MR.strings.syncyomi_failed)
            else -> context.stringResource(MR.strings.syncyomi_state_success)
        }
        val notification = context.notificationBuilder(Notifications.CHANNEL_SYNCYOMI_COMPLETE) {
            setContentTitle(context.stringResource(MR.strings.action_syncyomi))
            setContentText(contentText)
            setSmallIcon(if (isError) R.drawable.ic_warning_white_24dp else R.drawable.ic_refresh_24dp)
            setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            setContentIntent(openAppPendingIntent())
            setAutoCancel(true)
            setOnlyAlertOnce(false)
            setCategory(if (isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
        }.build()

        context.notify(Notifications.ID_SYNCYOMI_COMPLETE, notification)
    }

    fun cancelSyncYomiProgress() {
        context.cancelNotification(Notifications.ID_SYNCYOMI_PROGRESS)
    }

    fun showExtensionUpdates(extensions: List<SuwayomiExtensionDto>) {
        if (extensions.isEmpty()) return

        val count = extensions.size
        val title = context.pluralStringResource(
            MR.plurals.update_check_notification_ext_updates,
            count,
            count,
        )
        val lines = ServerNotificationContent.extensionUpdateLines(
            extensions = extensions,
            hideContent = hideContent,
            maxLines = MAX_NOTIFICATION_LINES,
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
        lines.forEach(style::addLine)

        val notification = context.notificationBuilder(Notifications.CHANNEL_EXTENSION_UPDATES) {
            setContentTitle(title)
            setContentText(context.stringResource(MR.strings.label_extensions))
            setSmallIcon(R.drawable.ic_extension_24dp)
            setStyle(style)
            setContentIntent(NotificationHandler.openExtensionsPendingActivity(context))
            setAutoCancel(true)
            setOnlyAlertOnce(false)
            setCategory(NotificationCompat.CATEGORY_STATUS)
        }.build()

        context.notify(Notifications.ID_EXTENSION_UPDATES, notification)
    }

    fun cancelExtensionUpdates() {
        context.cancelNotification(Notifications.ID_EXTENSION_UPDATES)
    }

    private fun updateDownloadErrorNotification(errorDownloads: List<SuwayomiDownloadDto>) {
        if (errorDownloads.isEmpty()) {
            return
        }

        val firstError = errorDownloads.first()
        val text = ServerNotificationContent.downloadDetail(
            mangaTitle = firstError.manga.title,
            chapterName = firstError.chapter.name,
            hideContent = hideContent,
            redactedText = context.stringResource(MR.strings.download_notifier_unknown_error),
        )
        val notification = context.notificationBuilder(Notifications.CHANNEL_DOWNLOAD_ERROR) {
            setContentTitle(context.stringResource(MR.strings.download_notifier_title_error))
            setContentText(text)
            setSmallIcon(R.drawable.ic_download_chapter_24dp)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setContentIntent(NotificationHandler.openDownloadsPendingActivity(context))
            setAutoCancel(true)
            setOnlyAlertOnce(true)
        }.build()
        context.notify(Notifications.ID_DOWNLOAD_ERROR, notification)
    }

    private fun showDownloadProgress(
        status: SuwayomiDownloadStatusDto,
        visibleQueue: List<SuwayomiDownloadDto>,
    ) {
        val active = visibleQueue.first()
        val progress = (active.progress.coerceIn(0.0, 1.0) * 100).toInt()
        val queueCount = visibleQueue.size
        val contentText = when {
            status.state.isStoppedState() -> {
                context.stringResource(MR.strings.download_notifier_download_paused)
            }
            else -> ServerNotificationContent.downloadDetail(
                mangaTitle = active.manga.title,
                chapterName = active.chapter.name,
                hideContent = hideContent,
                redactedText = context.stringResource(MR.strings.label_download_queue),
            )
        }
        val subText = context.pluralStringResource(
            MR.plurals.download_queue_summary,
            queueCount,
            queueCount,
        )
        val notification = context.notificationBuilder(Notifications.CHANNEL_DOWNLOAD_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.label_download_queue))
            setContentText(contentText)
            setSubText(subText)
            setSmallIcon(R.drawable.ic_download_chapter_24dp)
            setOnlyAlertOnce(true)
            setOngoing(!status.state.isStoppedState())
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
            setContentIntent(NotificationHandler.openDownloadsPendingActivity(context))
            if (status.state.isStoppedState()) {
                addAction(
                    R.drawable.ic_play_arrow_24dp,
                    context.stringResource(MR.strings.action_resume),
                    NotificationReceiver.startDownloaderPendingBroadcast(context),
                )
                addAction(
                    R.drawable.ic_close_24dp,
                    context.stringResource(MR.strings.action_cancel_all),
                    NotificationReceiver.clearDownloaderPendingBroadcast(context),
                )
            } else {
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.stringResource(MR.strings.action_pause),
                    NotificationReceiver.stopDownloaderPendingBroadcast(context),
                )
            }
            if (active.state.isQueuedState() || progress == 0) {
                setProgress(0, 0, true)
            } else {
                setProgress(100, progress, false)
            }
        }.build()

        context.notify(Notifications.ID_DOWNLOAD_PROGRESS, notification)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val hideContent: Boolean
        get() = securityPreferences.hideNotificationContent.get()

    private fun String.isQueuedState(): Boolean {
        return equals("QUEUED", ignoreCase = true) || equals("QUEUE", ignoreCase = true)
    }

    private fun String.isStoppedState(): Boolean {
        return equals("STOPPED", ignoreCase = true)
    }

    private fun SuwayomiSyncStatusDto.syncYomiDisplayText(): String {
        return when (state.uppercase()) {
            "STARTED" -> context.stringResource(MR.strings.syncyomi_state_started)
            "CREATING_BACKUP" -> context.stringResource(MR.strings.syncyomi_state_creating_backup)
            "DOWNLOADING" -> context.stringResource(MR.strings.syncyomi_state_downloading)
            "MERGING" -> context.stringResource(MR.strings.syncyomi_state_merging)
            "UPLOADING" -> context.stringResource(MR.strings.syncyomi_state_uploading)
            "RESTORING" -> context.stringResource(MR.strings.syncyomi_state_restoring)
            "SUCCESS" -> context.stringResource(MR.strings.syncyomi_state_success)
            "ERROR" -> context.stringResource(MR.strings.syncyomi_state_error)
            else -> state
        }
    }

    private companion object {
        const val MAX_NOTIFICATION_LINES = 5
    }
}
