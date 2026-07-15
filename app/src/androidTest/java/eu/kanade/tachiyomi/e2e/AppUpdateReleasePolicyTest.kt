package eu.kanade.tachiyomi.e2e

import eu.kanade.domain.release.interactor.GetApplicationRelease
import eu.kanade.domain.release.model.Release
import eu.kanade.domain.release.service.ReleaseService
import eu.kanade.tachiyomi.data.updater.GITHUB_REPOSITORY
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class AppUpdateReleasePolicyTest {
    @Test
    fun controlledReleaseResponseFindsNewAmatsubuUpdateWithoutGitHub() = runBlocking {
        val controlledRelease = Release(
            version = "v0.1.1",
            info = "Controlled changelog",
            releaseLink = "https://example.com/releases/v0.1.1",
            downloadLink = "https://example.com/amatsubu-arm64-v8a-modern-android-v0.1.1.apk",
        )
        val releaseService = ControlledReleaseService(controlledRelease)
        val checker = GetApplicationRelease(releaseService, InMemoryPreferenceStore())

        val result = checker.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                commitCount = 0,
                versionName = "0.1.0",
                repository = GITHUB_REPOSITORY,
                forceCheck = true,
            ),
        )

        assertEquals(GITHUB_REPOSITORY, releaseService.lastArguments?.repository)
        assertEquals(GetApplicationRelease.Result.NewUpdate(controlledRelease), result)
    }

    @Test
    fun controlledEqualVersionResponseReportsNoUpdateWithoutGitHub() = runBlocking {
        val releaseService = ControlledReleaseService(
            Release(
                version = "v0.1.0",
                info = "Controlled changelog",
                releaseLink = "https://example.com/releases/v0.1.0",
                downloadLink = "https://example.com/amatsubu-arm64-v8a-modern-android-v0.1.0.apk",
            ),
        )
        val checker = GetApplicationRelease(releaseService, InMemoryPreferenceStore())

        val result = checker.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                commitCount = 0,
                versionName = "0.1.0",
                repository = GITHUB_REPOSITORY,
                forceCheck = true,
            ),
        )

        assertTrue(result is GetApplicationRelease.Result.NoNewUpdate)
    }

    private class ControlledReleaseService(
        private val release: Release,
    ) : ReleaseService {
        var lastArguments: GetApplicationRelease.Arguments? = null
            private set

        override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release {
            lastArguments = arguments
            return release
        }
    }
}
