package eu.kanade.tachiyomi.data.suwayomi

import androidx.work.Data

internal object EnqueueBoundServerIdentity {
    private const val KEY_SERVER_KEY = "enqueue_bound_server_key"

    fun put(builder: Data.Builder, serverKey: String): Data.Builder {
        return builder.putString(KEY_SERVER_KEY, serverKey)
    }

    fun read(inputData: Data): String? {
        return inputData.getString(KEY_SERVER_KEY)?.takeIf { it.isNotBlank() }
    }

    fun check(inputData: Data, currentServerKey: String): EnqueueBoundServerIdentityCheck {
        val enqueuedServerKey = read(inputData)
            ?: return EnqueueBoundServerIdentityCheck.Missing
        return if (enqueuedServerKey == currentServerKey) {
            EnqueueBoundServerIdentityCheck.Matched(enqueuedServerKey)
        } else {
            EnqueueBoundServerIdentityCheck.Mismatched(
                enqueuedServerKey = enqueuedServerKey,
                currentServerKey = currentServerKey,
            )
        }
    }
}

internal sealed interface EnqueueBoundServerIdentityCheck {
    data object Missing : EnqueueBoundServerIdentityCheck
    data class Matched(val serverKey: String) : EnqueueBoundServerIdentityCheck
    data class Mismatched(
        val enqueuedServerKey: String,
        val currentServerKey: String,
    ) : EnqueueBoundServerIdentityCheck
}
