package eu.kanade.tachiyomi.data.suwayomi

import eu.kanade.tachiyomi.data.suwayomi.generated.ClearCachedImagesMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.DeleteGlobalMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.GetGlobalMetaQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.LastSyncStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.LibraryUpdateStatusQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ServerAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.ServerSettingsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SetGlobalMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SetMangaMetaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SetSettingsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StartSyncMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.StopLibraryUpdateMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateCategoryMangasMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateLibraryMangasMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.WebUiAboutQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.type.ClearCachedImagesInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.DeleteGlobalMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.GlobalMetaTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.MangaMetaTypeInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SetGlobalMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.SetMangaMetaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.StartSyncInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateCategoryMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateLibraryMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateStopInput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

internal class SuwayomiServerGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
) {

    suspend fun updateLibraryMangas(): Boolean {
        val response = apolloClientFactory.create().mutation(
            UpdateLibraryMangasMutation(UpdateLibraryMangaInput()),
        ).execute()
        return response.data?.updateLibraryManga?.updateStatus?.isRunning
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library update status")
    }

    suspend fun updateCategoryMangas(categoryId: Int): Boolean {
        val response = apolloClientFactory.create().mutation(
            UpdateCategoryMangasMutation(UpdateCategoryMangaInput(categories = listOf(categoryId))),
        ).execute()
        return response.data?.updateCategoryManga?.updateStatus?.isRunning
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no category update status")
    }

    suspend fun getLibraryUpdateStatus(): SuwayomiLibraryUpdateStatusDto {
        val response = apolloClientFactory.create().query(LibraryUpdateStatusQuery()).execute()
        return response.data?.libraryUpdateStatus?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no library update status")
    }

    suspend fun stopLibraryUpdate() {
        val response = apolloClientFactory.create().mutation(StopLibraryUpdateMutation(UpdateStopInput())).execute()
        if (response.data?.updateStop ==
            null
        ) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server did not stop library update")
        }
    }

    suspend fun lastSyncStatus(): SuwayomiSyncStatusDto? {
        val response = apolloClientFactory.create().query(LastSyncStatusQuery()).execute()
        if (response.data ==
            null
        ) {
            error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no sync status")
        }
        return response.data?.lastSyncStatus?.toSuwayomiDto()
    }

    suspend fun startSync(): StartSyncPayload {
        val response = apolloClientFactory.create().mutation(StartSyncMutation(StartSyncInput())).execute()
        return response.data?.startSync?.let { StartSyncPayload(it.result.rawValue) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no sync result")
    }

    suspend fun getGlobalMeta(key: String): SuwayomiGlobalMetaDto? {
        val response = apolloClientFactory.create().query(GetGlobalMetaQuery(key)).execute()
        if (response.errors?.isNotEmpty() == true) {
            error(response.errors!!.first().message)
        }
        return response.data?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
    }

    suspend fun setGlobalMeta(
        key: String,
        value: String,
    ): SuwayomiGlobalMetaDto {
        val response = apolloClientFactory.create().mutation(
            SetGlobalMetaMutation(SetGlobalMetaInput(meta = GlobalMetaTypeInput(key, value))),
        ).execute()
        return response.data?.setGlobalMeta?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no global metadata")
    }

    suspend fun deleteGlobalMeta(key: String): SuwayomiGlobalMetaDto? {
        val response = apolloClientFactory.create().mutation(
            DeleteGlobalMetaMutation(DeleteGlobalMetaInput(key = key)),
        ).execute()
        if (response.data?.deleteGlobalMeta == null && response.errors?.isNotEmpty() == true) {
            error(response.errors!!.first().message)
        }
        return response.data?.deleteGlobalMeta?.meta?.let { SuwayomiGlobalMetaDto(it.key, it.value) }
    }

    suspend fun clearCachedImages(
        cachedPages: Boolean? = null,
        cachedThumbnails: Boolean? = null,
        downloadedThumbnails: Boolean? = null,
    ): SuwayomiClearCachedImagesDto {
        val response = apolloClientFactory.create().mutation(
            ClearCachedImagesMutation(
                ClearCachedImagesInput(
                    cachedPages = cachedPages.optional(),
                    cachedThumbnails = cachedThumbnails.optional(),
                    downloadedThumbnails = downloadedThumbnails.optional(),
                ),
            ),
        ).execute()
        return response.data?.clearCachedImages?.let {
            SuwayomiClearCachedImagesDto(
                cachedPages = it.cachedPages,
                cachedThumbnails = it.cachedThumbnails,
                downloadedThumbnails = it.downloadedThumbnails,
            )
        } ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no cache clear result")
    }

    suspend fun setMangaMeta(
        mangaId: Int,
        key: String,
        value: String,
    ): SuwayomiMangaMetaDto {
        val response = apolloClientFactory.create().mutation(
            SetMangaMetaMutation(SetMangaMetaInput(meta = MangaMetaTypeInput(key, mangaId, value))),
        ).execute()
        return response.data?.setMangaMeta?.meta?.let { SuwayomiMangaMetaDto(it.key, it.mangaId, it.value) }
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no manga metadata")
    }

    suspend fun serverAbout(): SuwayomiServerAboutDto {
        val response = apolloClientFactory.create().query(ServerAboutQuery()).execute()
        return response.data?.aboutServer?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no server about data")
    }

    suspend fun webUiAbout(): SuwayomiWebUiAboutDto {
        val response = apolloClientFactory.create().query(WebUiAboutQuery()).execute()
        return response.data?.aboutWebUI?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no Web UI about data")
    }

    suspend fun serverSettings(): SuwayomiServerSettingsDto {
        val response = apolloClientFactory.create().query(ServerSettingsQuery()).execute()
        return response.data?.settings?.amatsubuServerSettings?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no settings")
    }

    suspend fun setSettings(settings: JsonObject): SuwayomiServerSettingsDto {
        val response = apolloClientFactory.create().mutation(SetSettingsMutation(settings.toGeneratedInput())).execute()
        return response.data?.setSettings?.settings?.amatsubuServerSettings?.toSuwayomiDto()
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no updated settings")
    }

    suspend fun setExtensionRepos(extensionRepos: List<String>): SuwayomiServerSettingsDto {
        return setSettings(
            buildJsonObject {
                putJsonArray("extensionRepos") {
                    extensionRepos.forEach { add(it) }
                }
            },
        )
    }
}
