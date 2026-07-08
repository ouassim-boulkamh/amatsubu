package eu.kanade.domain

import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleIncognito
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addFactory { SetMigrateSorting(get()) }

        addFactory { ToggleIncognito(get()) }
        addFactory { GetIncognitoState(get(), get()) }

    }
}
