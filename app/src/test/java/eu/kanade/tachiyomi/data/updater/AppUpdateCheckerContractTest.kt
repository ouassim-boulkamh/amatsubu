package eu.kanade.tachiyomi.data.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppUpdateCheckerContractTest {
    @Test
    fun `uses Amatsubu public GitHub releases`() {
        GITHUB_REPOSITORY shouldBe "ouassim-boulkamh/amatsubu"
        RELEASE_URL shouldBe "https://github.com/ouassim-boulkamh/amatsubu/releases"
    }
}
