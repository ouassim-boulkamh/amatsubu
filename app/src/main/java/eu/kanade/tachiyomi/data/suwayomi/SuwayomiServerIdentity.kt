package eu.kanade.tachiyomi.data.suwayomi

internal class SuwayomiServerIdentity private constructor(
    val baseUrl: String,
    val serverKey: String,
) {
    val notificationCheckpointKey: String
        get() = baseUrl

    companion object {
        fun fromBaseUrl(baseUrl: String): SuwayomiServerIdentity {
            val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
            return SuwayomiServerIdentity(
                baseUrl = normalizedBaseUrl,
                serverKey = "$normalizedBaseUrl/$GRAPHQL_PATH",
            )
        }

        fun fromGraphQlEndpoint(endpoint: String): SuwayomiServerIdentity {
            val normalizedEndpoint = normalizeBaseUrl(endpoint)
            val baseUrl = normalizedEndpoint.removeSuffix("/$GRAPHQL_PATH")
            return fromBaseUrl(baseUrl)
        }

        fun notificationCheckpointKey(identity: String): String {
            val normalized = normalizeBaseUrl(identity)
            return if (normalized.endsWith("/$GRAPHQL_PATH")) {
                fromGraphQlEndpoint(normalized).notificationCheckpointKey
            } else {
                normalized
            }
        }

        private fun normalizeBaseUrl(value: String): String {
            return value.trim().trimEnd('/')
        }

        private const val GRAPHQL_PATH = "api/graphql"
    }
}
