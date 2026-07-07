package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class SuwayomiClientProvider(
    preferenceStore: PreferenceStore = Injekt.get(),
    networkHelper: NetworkHelper = Injekt.get(),
    json: Json = Injekt.get(),
    snapshotCache: SuwayomiSnapshotCache? = Injekt.getInstanceOrNull(SuwayomiSnapshotCache::class.java),
) {
    val preferences = SuwayomiPreferences(preferenceStore)

    val httpClient = preferences.httpClient(networkHelper.client)

    val graphQlClient = SuwayomiGraphQlClient(
        client = httpClient,
        json = json,
        endpoint = preferences::graphQlEndpoint,
        snapshotCache = snapshotCache,
    )

    val subscriptionClient = SuwayomiGraphQlSubscriptionClient(
        client = httpClient,
        json = json,
        endpoint = preferences::graphQlEndpoint,
    )

    val liveStatusClient = SuwayomiLiveStatusClient(
        graphQlClient = graphQlClient,
        subscriptionClient = subscriptionClient,
    )

    fun baseUrl(): String = preferences.baseUrl()

    fun serverKey(): String = preferences.graphQlEndpoint().trimEnd('/')

    fun restUrl(path: String): String = "${baseUrl()}/${path.trimStart('/')}"
}
