package eu.kanade.tachiyomi.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import eu.kanade.tachiyomi.data.notification.ServerNotificationSyncJob
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NotificationWorkManagerSmokeTest {

    @Test
    fun promptNotificationReconciliationQueuesRuntimeWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)

        ServerNotificationSyncJob.cancel(context)
        ServerNotificationSyncJob.schedulePromptReconciliation(context)

        val work = workManager.getWorkInfosByTag("ServerNotificationSync").get(15, TimeUnit.SECONDS)
        check(work.isNotEmpty()) { "Prompt notification reconciliation did not queue WorkManager work" }

        ServerNotificationSyncJob.cancel(context)
    }

    @Test
    fun healthNotificationReconciliationQueuesRuntimeWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workManager = WorkManager.getInstance(context)

        ServerNotificationSyncJob.cancel(context)
        ServerNotificationSyncJob.scheduleHealthReconciliation(context)

        val work = workManager.getWorkInfosByTag("ServerNotificationSync").get(15, TimeUnit.SECONDS)
        check(work.isNotEmpty()) { "Health notification reconciliation did not queue WorkManager work" }

        ServerNotificationSyncJob.cancel(context)
    }
}
