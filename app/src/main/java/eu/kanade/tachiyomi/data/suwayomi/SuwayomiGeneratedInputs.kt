package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.type.AuthMode
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FilterChangeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.PartialSettingsTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SortSelectionInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SourcePreferenceChangeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.TriState
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIChannel
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIFlavor
import eu.kanade.tachiyomi.data.suwayomi.generated.type.WebUIInterface
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal fun SuwayomiSourceFilterChange.toGeneratedInput(): FilterChangeInput = FilterChangeInput(
    position = position,
    checkBoxState = Optional.presentIfNotNull(checkBoxState),
    groupChange = Optional.presentIfNotNull(groupChange?.toGeneratedInput()),
    selectState = Optional.presentIfNotNull(selectState),
    sortState = Optional.presentIfNotNull(
        sortState?.let {
            SortSelectionInput(ascending = it.ascending, index = it.index)
        },
    ),
    textState = Optional.presentIfNotNull(textState),
    triState = Optional.presentIfNotNull(triState?.let { TriState.valueOf(it.name) }),
)

internal fun SuwayomiSourcePreferenceChange.toGeneratedInput() = SourcePreferenceChangeInput(
    position = position,
    checkBoxState = Optional.presentIfNotNull(checkBoxState),
    editTextState = Optional.presentIfNotNull(editTextState),
    listState = Optional.presentIfNotNull(listState),
    multiSelectState = Optional.presentIfNotNull(multiSelectState),
    switchState = Optional.presentIfNotNull(switchState),
)

internal fun JsonObject.toGeneratedInput() = PartialSettingsTypeInput(
    authMode = optionalString("authMode") { AuthMode.safeValueOf(it) },
    authPassword = optionalString("authPassword"),
    authUsername = optionalString("authUsername"),
    autoBackupIncludeCategories = optionalBoolean("autoBackupIncludeCategories"),
    autoBackupIncludeChapters = optionalBoolean("autoBackupIncludeChapters"),
    autoBackupIncludeClientData = optionalBoolean("autoBackupIncludeClientData"),
    autoBackupIncludeHistory = optionalBoolean("autoBackupIncludeHistory"),
    autoBackupIncludeManga = optionalBoolean("autoBackupIncludeManga"),
    autoBackupIncludeServerSettings = optionalBoolean("autoBackupIncludeServerSettings"),
    autoBackupIncludeTracking = optionalBoolean("autoBackupIncludeTracking"),
    autoDownloadIgnoreReUploads = optionalBoolean("autoDownloadIgnoreReUploads"),
    autoDownloadNewChapters = optionalBoolean("autoDownloadNewChapters"),
    autoDownloadNewChaptersLimit = optionalInt("autoDownloadNewChaptersLimit"),
    backupInterval = optionalInt("backupInterval"),
    backupPath = optionalString("backupPath"),
    backupTTL = optionalInt("backupTTL"),
    backupTime = optionalString("backupTime"),
    debugLogsEnabled = optionalBoolean("debugLogsEnabled"),
    downloadAsCbz = optionalBoolean("downloadAsCbz"),
    downloadsPath = optionalString("downloadsPath"),
    electronPath = optionalString("electronPath"),
    excludeCompleted = optionalBoolean("excludeCompleted"),
    excludeEntryWithUnreadChapters = optionalBoolean("excludeEntryWithUnreadChapters"),
    excludeNotStarted = optionalBoolean("excludeNotStarted"),
    excludeUnreadChapters = optionalBoolean("excludeUnreadChapters"),
    extensionRepos =
    this["extensionRepos"]?.jsonArray?.map { it.jsonPrimitive.content }?.let { Optional.present(it) }
        ?: Optional.Absent,
    flareSolverrAsResponseFallback = optionalBoolean("flareSolverrAsResponseFallback"),
    flareSolverrEnabled = optionalBoolean("flareSolverrEnabled"),
    flareSolverrSessionName = optionalString("flareSolverrSessionName"),
    flareSolverrSessionTtl = optionalInt("flareSolverrSessionTtl"),
    flareSolverrTimeout = optionalInt("flareSolverrTimeout"),
    flareSolverrUrl = optionalString("flareSolverrUrl"),
    globalUpdateInterval = optionalDouble("globalUpdateInterval"),
    initialOpenInBrowserEnabled = optionalBoolean("initialOpenInBrowserEnabled"),
    ip = optionalString("ip"),
    localSourcePath = optionalString("localSourcePath"),
    maxLogFileSize = optionalString("maxLogFileSize"),
    maxLogFiles = optionalInt("maxLogFiles"),
    maxLogFolderSize = optionalString("maxLogFolderSize"),
    maxSourcesInParallel = optionalInt("maxSourcesInParallel"),
    port = optionalInt("port"),
    socksProxyEnabled = optionalBoolean("socksProxyEnabled"),
    socksProxyHost = optionalString("socksProxyHost"),
    socksProxyPassword = optionalString("socksProxyPassword"),
    socksProxyPort = optionalString("socksProxyPort"),
    socksProxyUsername = optionalString("socksProxyUsername"),
    socksProxyVersion = optionalInt("socksProxyVersion"),
    systemTrayEnabled = optionalBoolean("systemTrayEnabled"),
    updateMangas = optionalBoolean("updateMangas"),
    webUIChannel = optionalString("webUIChannel") { WebUIChannel.safeValueOf(it) },
    webUIFlavor = optionalString("webUIFlavor") { WebUIFlavor.safeValueOf(it) },
    webUIInterface = optionalString("webUIInterface") { WebUIInterface.safeValueOf(it) },
    webUIUpdateCheckInterval = optionalDouble("webUIUpdateCheckInterval"),
)

private fun JsonObject.optionalBoolean(key: String): Optional<Boolean?> =
    this[key]?.jsonPrimitive?.boolean?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalInt(key: String): Optional<Int?> =
    this[key]?.jsonPrimitive?.int?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalDouble(key: String): Optional<Double?> =
    this[key]?.jsonPrimitive?.double?.let { Optional.present(it) } ?: Optional.Absent

private fun <T> JsonObject.optionalString(key: String, transform: (String) -> T): Optional<T?> =
    this[key]?.jsonPrimitive?.content?.let(transform)?.let { Optional.present(it) } ?: Optional.Absent

private fun JsonObject.optionalString(key: String): Optional<String?> = optionalString(key) { it }
