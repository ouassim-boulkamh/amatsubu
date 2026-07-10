package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore

internal class SuwayomiClientProvider(
    preferenceStore: PreferenceStore,
    networkHelper: NetworkHelper,
    json: Json,
    snapshotCache: SuwayomiSnapshotCache?,
    tokenStore: SuwayomiTokenStore,
) {
    val preferences = SuwayomiPreferences(preferenceStore, tokenStore)

    private val unauthenticatedGraphQlClient = SuwayomiGraphQlClient(
        client = SuwayomiPreferences(preferenceStore).httpClient(networkHelper.client),
        endpoint = preferences::graphQlEndpoint,
        snapshotCache = snapshotCache,
    )

    val tokenAuth = SuwayomiTokenAuthApi(
        operations = unauthenticatedGraphQlClient,
        tokenStore = tokenStore,
        serverKey = ::serverKey,
    )

    val httpClient = preferences.httpClient(networkHelper.client, tokenAuth::refresh)

    val graphQlClient = SuwayomiGraphQlClient(
        client = httpClient,
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

    fun serverIdentity(): SuwayomiServerIdentity = SuwayomiServerIdentity.fromBaseUrl(baseUrl())

    fun serverKey(): String = serverIdentity().serverKey

    fun restUrl(path: String): String = "${baseUrl()}/${path.trimStart('/')}"
}
