package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import okhttp3.OkHttpClient

/** Keeps generated GraphQL transport private to the Suwayomi boundary. */
internal class SuwayomiApolloClientFactory(
    private val client: OkHttpClient,
    private val endpoint: () -> String,
) {
    fun create(): ApolloClient = ApolloClient.Builder()
        .serverUrl(endpoint())
        .okHttpClient(client)
        .build()
}
