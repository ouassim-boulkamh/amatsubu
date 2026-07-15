package tachiyomi.data.release

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class ReleaseServiceImplTest {
    private val assets = listOf(
        GitHubAsset("amatsubu-universal-all-devices-v0.1.1.apk", "universal"),
        GitHubAsset("amatsubu-arm64-v8a-modern-android-v0.1.1.apk", "arm64"),
        GitHubAsset("amatsubu-armeabi-v7a-legacy-32bit-arm-v0.1.1.apk", "arm32"),
    )

    @Test
    fun `selects the dedicated arm64 asset for the Android test device ABI`() {
        ReleaseServiceImpl.selectDownloadLink(assets, "arm64-v8a", isFoss = false) shouldBe "arm64"
    }

    @Test
    fun `falls back to the universal asset for an unmatched ABI`() {
        ReleaseServiceImpl.selectDownloadLink(assets, "riscv64", isFoss = false) shouldBe "universal"
    }

    @Test
    fun `release workflow asset names remain compatible with ABI selection`() {
        val workflowAssets = releaseWorkflowAssetNames(tag = "v9.9.9")
            .map { assetName -> GitHubAsset(assetName, assetName) }

        workflowAssets.map { it.name } shouldBe listOf(
            "amatsubu-universal-all-devices-v9.9.9.apk",
            "amatsubu-arm64-v8a-modern-android-v9.9.9.apk",
            "amatsubu-armeabi-v7a-legacy-32bit-arm-v9.9.9.apk",
            "amatsubu-x86-32bit-emulator-v9.9.9.apk",
            "amatsubu-x86_64-64bit-emulator-v9.9.9.apk",
        )
        ReleaseServiceImpl.selectDownloadLink(workflowAssets, "arm64-v8a", isFoss = false) shouldBe
            "amatsubu-arm64-v8a-modern-android-v9.9.9.apk"
        ReleaseServiceImpl.selectDownloadLink(workflowAssets, "armeabi-v7a", isFoss = false) shouldBe
            "amatsubu-armeabi-v7a-legacy-32bit-arm-v9.9.9.apk"
        ReleaseServiceImpl.selectDownloadLink(workflowAssets, "x86", isFoss = false) shouldBe
            "amatsubu-x86-32bit-emulator-v9.9.9.apk"
        ReleaseServiceImpl.selectDownloadLink(workflowAssets, "x86_64", isFoss = false) shouldBe
            "amatsubu-x86_64-64bit-emulator-v9.9.9.apk"
        ReleaseServiceImpl.selectDownloadLink(workflowAssets, "riscv64", isFoss = false) shouldBe
            "amatsubu-universal-all-devices-v9.9.9.apk"
    }

    @Test
    fun `release workflow supports controlled draft validation without changing stable tag releases`() {
        val workflow = releaseWorkflowFile().readText()

        workflow.contains("workflow_dispatch:") shouldBe true
        workflow.contains("tag:") shouldBe true
        workflow.contains("draft:") shouldBe true
        workflow.contains("prerelease:") shouldBe true
        workflow.contains("push:") shouldBe true
        workflow.contains("- v*") shouldBe true
        workflow.contains("contents: write") shouldBe true
        workflow.contains("Release tag must start with 'v'") shouldBe true
        workflow.contains("Manual validation release tag must contain '-validation'") shouldBe true
        workflow.contains("tag=\"\$INPUT_TAG\"") shouldBe true
        workflow.contains("tag=\"\${GITHUB_REF#refs/tags/}\"") shouldBe true
        workflow.contains("inputs.draft") shouldBe false
        workflow.contains("inputs.prerelease") shouldBe false
        workflow.contains(
            "draft: \${{ github.event_name == 'workflow_dispatch' }}",
        ) shouldBe true
        workflow.contains(
            "prerelease: \${{ github.event_name == 'workflow_dispatch' }}",
        ) shouldBe true
    }

    @Test
    fun `ci workflows do not use obsolete updater flag`() {
        val buildWorkflow = workflowFile("build.yml").readText()
        val releaseWorkflow = releaseWorkflowFile().readText()

        buildWorkflow.contains("-Penable-updater") shouldBe false
        releaseWorkflow.contains("-Penable-updater") shouldBe false
        buildWorkflow.contains("./gradlew assembleRelease -Pinclude-telemetry") shouldBe true
        releaseWorkflow.contains("./gradlew assembleRelease -Pinclude-telemetry") shouldBe true
    }

    private fun releaseWorkflowAssetNames(tag: String): List<String> {
        val workflow = releaseWorkflowFile().readText()
        return workflow
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("amatsubu-") && it.endsWith(".apk") }
            .map { it.replace("\${{ needs.get_tag.outputs.tag }}", tag) }
            .distinct()
            .toList()
    }

    private fun releaseWorkflowFile(): File {
        return workflowFile("release.yml")
    }

    private fun workflowFile(name: String): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir is not set")
        return generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .map { directory -> File(directory, ".github/workflows/$name") }
            .firstOrNull { it.isFile }
            ?: error("Could not find .github/workflows/$name from $userDir")
    }
}
