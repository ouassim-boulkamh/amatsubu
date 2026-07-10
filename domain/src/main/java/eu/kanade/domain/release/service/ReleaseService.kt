package eu.kanade.domain.release.service

import eu.kanade.domain.release.interactor.GetApplicationRelease
import eu.kanade.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(arguments: GetApplicationRelease.Arguments): Release?
}
