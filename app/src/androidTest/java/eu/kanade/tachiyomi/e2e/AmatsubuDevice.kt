package eu.kanade.tachiyomi.e2e

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal class AmatsubuDevice {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = Selectors.PACKAGE_NAME

    fun launchApp() {
        configureControlledSourcePreferences()
        device.pressHome()
        startApp()
    }

    fun launchSettings() {
        device.pressHome()
        startApp(Intent.ACTION_APPLICATION_PREFERENCES)
        assertText(Selectors.Text.SETTINGS)
    }

    fun launchControlledSource() {
        configureControlledSourcePreferences()
        device.pressHome()
        startApp(ACTION_AUTOMATION_SOURCE) {
            putExtra("source_id", Selectors.ControlledSource.ID)
            putExtra("source_name", Selectors.ControlledSource.NAME)
            putExtra("source_display_name", Selectors.ControlledSource.DISPLAY_NAME)
            putExtra("source_supports_latest", true)
            putExtra("source_is_configurable", true)
            putExtra("source_initial_type", "SEARCH")
            putExtra("source_initial_query", Selectors.ControlledSource.SEARCH_TERM)
        }
        assertTag(Selectors.Tags.SOURCE_SEARCH_BUTTON)
    }

    fun launchControlledUpdatePrompt() {
        device.pressHome()
        startApp(ACTION_AUTOMATION_APP_UPDATE) {
            putExtra("update_version", "v0.1.1")
            putExtra(
                "update_changelog",
                """
                    ## Controlled update

                    - Restored updater prompt
                    - Validates Markdown changelog rendering
                """.trimIndent(),
            )
            putExtra("update_release_link", "https://example.com/releases/v0.1.1")
            putExtra("update_download_link", "https://example.com/amatsubu-arm64-v8a-modern-android-v0.1.1.apk")
        }
    }

    private fun startApp(action: String? = null, configure: Intent.() -> Unit = {}) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launch intent found for $packageName")
        if (action != null) {
            intent.action = action
        }
        intent.configure()
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        check(device.wait(Until.hasObject(By.pkg(packageName)), DEFAULT_TIMEOUT_MILLIS)) {
            "Timed out waiting for $packageName to launch"
        }
        device.waitForIdle()
    }

    fun tapText(text: String) {
        findText(text).click()
        device.waitForIdle()
    }

    fun tapTextContains(text: String) {
        findTextContains(text).click()
        device.waitForIdle()
    }

    fun scrollToText(text: String) {
        repeat(MAX_SCROLL_ATTEMPTS) {
            if (hasText(text)) return
            swipeUp()
            device.waitForIdle()
        }

        check(hasText(text)) {
            "Timed out scrolling to text \"$text\""
        }
    }

    fun scrollToTag(tag: String) {
        repeat(MAX_SCROLL_ATTEMPTS) {
            if (hasTag(tag)) return
            swipeUp()
            device.waitForIdle()
        }

        check(hasTag(tag)) {
            "Timed out scrolling to test tag \"$tag\""
        }
    }

    fun scrollToTagOrText(tag: String, text: String) {
        repeat(MAX_SCROLL_ATTEMPTS) {
            if (hasTag(tag) || hasText(text)) return
            swipeUp()
            device.waitForIdle()
        }

        check(hasTag(tag) || hasText(text)) {
            "Timed out scrolling to test tag \"$tag\" or text \"$text\""
        }
    }

    private fun swipeUp() {
        val x = device.displayWidth / 2
        val startY = (device.displayHeight * SWIPE_START_RATIO).toInt()
        val endY = (device.displayHeight * SWIPE_END_RATIO).toInt()
        device.swipe(x, startY, x, endY, SWIPE_STEPS)
    }

    fun tapDesc(desc: String) {
        findObject("content description \"$desc\"") { By.desc(desc) }.click()
        device.waitForIdle()
    }

    fun tapTag(tag: String) {
        findTag(tag).click()
        device.waitForIdle()
    }

    fun pressBack() {
        device.pressBack()
        device.waitForIdle()
    }

    fun tapTagOrText(tag: String, text: String) {
        findTagOrText(tag, text).click()
        device.waitForIdle()
    }

    fun assertText(text: String) {
        check(device.wait(Until.hasObject(By.text(text)), DEFAULT_TIMEOUT_MILLIS)) {
            "Timed out waiting for text \"$text\""
        }
    }

    fun assertTextContains(text: String) {
        check(device.wait(Until.hasObject(By.textContains(text)), DEFAULT_TIMEOUT_MILLIS)) {
            "Timed out waiting for text containing \"$text\""
        }
    }

    fun assertDesc(desc: String) {
        check(device.wait(Until.hasObject(By.desc(desc)), DEFAULT_TIMEOUT_MILLIS)) {
            "Timed out waiting for content description \"$desc\""
        }
    }

    fun assertAnyText(vararg texts: String) {
        check(hasAnyText(*texts)) {
            "Timed out waiting for any text: ${texts.joinToString()}"
        }
    }

    fun assertTag(tag: String) {
        check(hasTag(tag, DEFAULT_TIMEOUT_MILLIS)) {
            "Timed out waiting for test tag \"$tag\""
        }
    }

    fun assertAnyTag(vararg tags: String) {
        check(tags.any { hasTag(it, DEFAULT_TIMEOUT_MILLIS) }) {
            "Timed out waiting for any test tag: ${tags.joinToString()}"
        }
    }

    fun hasText(text: String, timeoutMillis: Long = SHORT_TIMEOUT_MILLIS): Boolean {
        return device.wait(Until.hasObject(By.text(text)), timeoutMillis)
    }

    fun hasTextContains(text: String, timeoutMillis: Long = SHORT_TIMEOUT_MILLIS): Boolean {
        return device.wait(Until.hasObject(By.textContains(text)), timeoutMillis)
    }

    fun hasDesc(desc: String, timeoutMillis: Long = SHORT_TIMEOUT_MILLIS): Boolean {
        return device.wait(Until.hasObject(By.desc(desc)), timeoutMillis)
    }

    fun hasTag(tag: String, timeoutMillis: Long = SHORT_TIMEOUT_MILLIS): Boolean {
        return device.wait(Until.hasObject(By.res(tag)), timeoutMillis) ||
            device.wait(Until.hasObject(By.res(packageName, tag)), timeoutMillis)
    }

    fun hasAnyText(vararg texts: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (texts.any { device.hasObject(By.text(it)) || device.hasObject(By.textContains(it)) }) {
                return true
            }
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    fun openHomeTab(tag: String, text: String) {
        tapTagOrText(tag, text)
    }

    fun openControlledSource() {
        openHomeTab(Selectors.Tags.HOME_BROWSE, Selectors.Text.BROWSE)
        if (hasText(Selectors.Text.SOURCES)) {
            tapText(Selectors.Text.SOURCES)
        }
        scrollToTagOrText(Selectors.Tags.CONTROLLED_SOURCE, Selectors.ControlledSource.NAME)
        tapTagOrText(Selectors.Tags.CONTROLLED_SOURCE, Selectors.ControlledSource.NAME)
        assertAnyText(Selectors.ControlledSource.NAME, Selectors.ControlledSource.MANGA_TITLE)
    }

    private fun configureControlledSourcePreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sourceId = Selectors.ControlledSource.ID
        val enabledLanguages = preferences.getStringSet("source_languages", emptySet()).orEmpty()
        val disabledSources = preferences.getStringSet("hidden_catalogues", emptySet()).orEmpty()
        val pinnedSources = preferences.getStringSet("pinned_catalogues", emptySet()).orEmpty()

        preferences.edit()
            .putStringSet("source_languages", enabledLanguages + "all")
            .putStringSet("hidden_catalogues", disabledSources - sourceId)
            .putStringSet("pinned_catalogues", pinnedSources + sourceId)
            .putString("amatsubu_server_url", "http://192.168.137.1:4568/")
            .putBoolean("browse_hide_in_library_items", false)
            .apply()
    }

    private fun findText(text: String): UiObject2 {
        return findObject("text \"$text\"") { By.text(text) }
    }

    private fun findTextContains(text: String): UiObject2 {
        return findObject("text containing \"$text\"") { By.textContains(text) }
    }

    private fun findTag(tag: String): UiObject2 {
        val byResourceId = device.wait(Until.findObject(By.res(tag)), SHORT_TIMEOUT_MILLIS)
        if (byResourceId != null) return byResourceId
        return findObject("test tag \"$tag\"") { By.res(packageName, tag) }
    }

    private fun findTagOrText(tag: String, text: String): UiObject2 {
        val byTag = device.wait(Until.findObject(By.res(tag)), SHORT_TIMEOUT_MILLIS)
            ?: device.wait(Until.findObject(By.res(packageName, tag)), SHORT_TIMEOUT_MILLIS)
        if (byTag != null) return byTag

        return device.wait(Until.findObject(By.text(text)), SHORT_TIMEOUT_MILLIS)
            ?: device.wait(Until.findObject(By.desc(text)), SHORT_TIMEOUT_MILLIS)
            ?: error("Could not find test tag \"$tag\", text \"$text\", or description \"$text\"")
    }

    private fun findObject(label: String, selector: () -> androidx.test.uiautomator.BySelector): UiObject2 {
        return device.wait(Until.findObject(selector()), DEFAULT_TIMEOUT_MILLIS)
            ?: error("Timed out waiting for $label")
    }

    private companion object {
        const val ACTION_AUTOMATION_SOURCE = "eu.kanade.tachiyomi.DEBUG_AUTOMATION_SOURCE"
        const val ACTION_AUTOMATION_APP_UPDATE = "eu.kanade.tachiyomi.DEBUG_AUTOMATION_APP_UPDATE"
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val SHORT_TIMEOUT_MILLIS = 2_000L
        const val POLL_MILLIS = 250L
        const val MAX_SCROLL_ATTEMPTS = 16
        const val SWIPE_START_RATIO = 0.78f
        const val SWIPE_END_RATIO = 0.25f
        const val SWIPE_STEPS = 24
    }
}
