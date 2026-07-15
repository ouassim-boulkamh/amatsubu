package eu.kanade.tachiyomi.e2e

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class AppUpdateDownloadJobSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun controlledApkDownloadWritesCacheFileAndPostsInstallNotification() {
        grantNotificationPermission()
        val payload = "controlled-apk-payload".toByteArray()
        val downloadedApk = File(context.externalCacheDir, "update.apk")
        downloadedApk.delete()

        SingleResponseHttpServer(statusCode = 200, payload = payload).use { server ->
            resetUpdaterWork()
            AppUpdateDownloadJob.start(context, server.url, "v0.1.1")

            val state = waitForUpdaterState { it.isFinished }
            assertEquals(WorkInfo.State.SUCCEEDED, state)
        }

        assertTrue("Downloaded update APK was not written", downloadedApk.isFile)
        assertEquals(payload.size.toLong(), downloadedApk.length())
        assertTrue(
            "Install notification was not posted",
            waitForNotification(Notifications.ID_APP_UPDATE_PROMPT),
        )

        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATE_PROMPT)
    }

    @Test
    fun failedDownloadPostsRetryErrorNotification() {
        grantNotificationPermission()

        SingleResponseHttpServer(statusCode = 500, payload = ByteArray(0)).use { server ->
            resetUpdaterWork()
            AppUpdateDownloadJob.start(context, server.url, "v0.1.1")

            assertTrue(
                "Download error notification was not posted",
                waitForNotification(Notifications.ID_APP_UPDATE_ERROR),
            )
        }

        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG).result.get(15, TimeUnit.SECONDS)
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATE_ERROR)
    }

    @Test
    fun stopCancelsRunningDownloadWork() {
        grantNotificationPermission()

        HangingHttpServer().use { server ->
            resetUpdaterWork()
            AppUpdateDownloadJob.start(context, server.url, "v0.1.1")

            assertTrue("Controlled server was not reached", server.awaitRequest())
            AppUpdateDownloadJob.stop(context)

            val state = waitForUpdaterState { it.isFinished }
            assertEquals(WorkInfo.State.CANCELLED, state)
        }
    }

    private fun grantNotificationPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
    }

    private fun resetUpdaterWork() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_TAG).result.get(15, TimeUnit.SECONDS)
        workManager.pruneWork().result.get(15, TimeUnit.SECONDS)
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATER)
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATE_PROMPT)
        NotificationReceiver.dismissNotification(context, Notifications.ID_APP_UPDATE_ERROR)
    }

    private fun waitForUpdaterState(predicate: (WorkInfo.State) -> Boolean): WorkInfo.State {
        val workManager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(45)
        var lastState: WorkInfo.State? = null

        while (System.currentTimeMillis() < deadline) {
            val work = workManager.getWorkInfosForUniqueWork(WORK_TAG).get(5, TimeUnit.SECONDS)
            lastState = work.firstOrNull()?.state
            if (lastState != null && predicate(lastState)) return lastState
            Thread.sleep(250)
        }

        error("Timed out waiting for $WORK_TAG state; last state was $lastState")
    }

    private fun waitForNotification(id: Int): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (System.currentTimeMillis() < deadline) {
            if (notificationManager.activeNotifications.any { it.id == id }) return true
            Thread.sleep(250)
        }
        return false
    }

    private class SingleResponseHttpServer(
        private val statusCode: Int,
        private val payload: ByteArray,
    ) : Closeable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val worker = thread(start = true, name = "controlled-apk-http-server") {
            try {
                serverSocket.accept().use(::serve)
            } catch (_: Exception) {
                // Closed by the test cleanup path.
            }
        }

        val url: String = "http://127.0.0.1:${serverSocket.localPort}/update.apk"

        private fun serve(socket: Socket) {
            val input = socket.getInputStream().bufferedReader()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }

            val output = socket.getOutputStream()
            val headers = listOf(
                "HTTP/1.1 $statusCode ${if (statusCode == 200) "OK" else "Controlled Failure"}",
                "Content-Type: application/vnd.android.package-archive",
                "Content-Length: ${payload.size}",
                "Connection: close",
            ).joinToString(separator = "\r\n", postfix = "\r\n\r\n")
            output.write(headers.toByteArray(Charsets.ISO_8859_1))
            output.write(payload)
            output.flush()
        }

        override fun close() {
            serverSocket.close()
            worker.join(1_000)
        }
    }

    private class HangingHttpServer : Closeable {
        private val closed = AtomicBoolean(false)
        private val requestReceived = CountDownLatch(1)
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val worker = thread(start = true, name = "hanging-apk-http-server") {
            try {
                serverSocket.accept().use(::holdOpen)
            } catch (_: Exception) {
                // Closed by the test cleanup path.
            }
        }

        val url: String = "http://127.0.0.1:${serverSocket.localPort}/update.apk"

        fun awaitRequest(): Boolean = requestReceived.await(10, TimeUnit.SECONDS)

        private fun holdOpen(socket: Socket) {
            val input = socket.getInputStream().bufferedReader()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            requestReceived.countDown()
            while (!closed.get()) {
                Thread.sleep(100)
            }
        }

        override fun close() {
            closed.set(true)
            serverSocket.close()
            worker.join(1_000)
        }
    }

    private companion object {
        const val WORK_TAG = "AppUpdateDownload"
    }
}
