package eu.kanade.tachiyomi.ui.setting.track

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.suwayomi.SuwayomiClientProvider
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class SuwayomiTrackLoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            LoadingScreen()
        }

        val callbackUri = intent.data
        val trackerId = getPendingTrackerId(this)

        if (callbackUri == null || trackerId == null) {
            returnToSettings()
            return
        }

        lifecycleScope.launch {
            try {
                SuwayomiClientProvider().graphQlClient.loginTrackerOAuth(trackerId, callbackUri.toString())
                toast(MR.strings.login_success)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to complete Suwayomi tracker OAuth callback" }
                toast(e.message ?: "Tracker login failed")
            } finally {
                clearPendingTrackerId(this@SuwayomiTrackLoginActivity)
                returnToSettings()
            }
        }
    }

    private fun returnToSettings() {
        finish()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    companion object {
        private const val PREFS_NAME = "suwayomi_tracker_login"
        private const val PENDING_TRACKER_ID = "pending_tracker_id"

        fun setPendingTrackerId(context: Context, trackerId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PENDING_TRACKER_ID, trackerId)
                .apply()
        }

        private fun getPendingTrackerId(context: Context): Int? {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return preferences.getInt(PENDING_TRACKER_ID, -1).takeIf { it >= 0 }
        }

        private fun clearPendingTrackerId(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PENDING_TRACKER_ID)
                .apply()
        }
    }
}
