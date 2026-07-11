package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.testing.FuzzTestConfig
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import org.junit.jupiter.api.Test

class BackupCompatibilityPolicyPropertyTest {

    @Test
    suspend fun `duplicate compatible preferences yield one restore action per client key`() {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.list(Arb.int(0..2), 1..24),
            Arb.list(Arb.boolean(), 1..24),
        ) { keys, values ->
            val preferences = keys.mapIndexed { index, key ->
                BackupPreference("pref_$key", BooleanPreferenceValue(values[index % values.size]))
            }
            val policy = BackupCompatibilityPolicy(
                appPreferences = keys.distinct().associate { "pref_$it" to false },
                sourcePreferences = emptyMap(),
            )

            val result = policy.evaluate(
                Backup(backupManga = emptyList(), backupPreferences = preferences),
                RestoreOptions(appSettings = true),
            )

            result.restorable.appPreferences.map { it.key } shouldBe
                result.restorable.appPreferences.map { it.key }.distinct()
            result.restorable.appPreferences.size shouldBe keys.distinct().size
            result.summary.decisions.count { it.decision == BackupCompatibilityDecisionType.RESTORE_DIRECT } shouldBe
                keys.distinct().size
        }
    }

    @Test
    suspend fun `duplicate source preference blocks yield one restore block per source key`() {
        checkAll(
            FuzzTestConfig.caseCount(),
            Arb.list(Arb.int(0..2), 1..12),
        ) { sourceIds ->
            val sourcePreferences = sourceIds.mapIndexed { index, sourceId ->
                BackupSourcePreferences(
                    sourceKey = "source_$sourceId",
                    prefs = listOf(
                        BackupPreference("enabled", BooleanPreferenceValue(index % 2 == 0)),
                        BackupPreference("page_size", IntPreferenceValue(index)),
                        BackupPreference("label", StringPreferenceValue("value_$index")),
                    ),
                )
            }
            val policy = BackupCompatibilityPolicy(
                appPreferences = emptyMap<String, Any>(),
                sourcePreferences = sourceIds.distinct().associate { sourceId ->
                    "source_$sourceId" to mapOf<String, Any>("enabled" to false, "page_size" to 0, "label" to "")
                },
            )

            val result = policy.evaluate(
                Backup(backupManga = emptyList(), backupSourcePreferences = sourcePreferences),
                RestoreOptions(sourceSettings = true),
            )

            result.restorable.sourcePreferences.map { it.sourceKey } shouldBe
                result.restorable.sourcePreferences.map { it.sourceKey }.distinct()
            result.restorable.sourcePreferences.size shouldBe sourceIds.distinct().size
        }
    }
}
