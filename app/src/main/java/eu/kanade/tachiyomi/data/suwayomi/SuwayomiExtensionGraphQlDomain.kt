package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.AddExtensionStoreMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.ExtensionStoresQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchExtensionListMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.RemoveExtensionStoreMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateExtensionMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateExtensionsMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.AddExtensionStoreInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.RemoveExtensionStoreInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionPatchInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateExtensionsInput

internal class SuwayomiExtensionGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
) {

    suspend fun extensionList(): List<SuwayomiExtensionDto> {
        val response = apolloClientFactory.create().mutation(FetchExtensionListMutation()).execute()
        val extensions = response.data?.fetchExtensions?.extensions
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension list")
        return extensions.map { it.amatsubuExtension.toSuwayomiDto() }
    }

    suspend fun extensionStores(): List<SuwayomiExtensionStoreDto> {
        val response = apolloClientFactory.create().query(ExtensionStoresQuery()).execute()
        val stores = response.data?.extensionStores?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension stores")
        return stores.map { it.amatsubuExtensionStore.toSuwayomiDto() }
    }

    suspend fun addExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto {
        val response = apolloClientFactory.create().mutation(
            AddExtensionStoreMutation(AddExtensionStoreInput(indexUrl = indexUrl)),
        ).execute()
        val store = response.data?.addExtensionStore?.extensionStore
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension store")
        return store.amatsubuExtensionStore.toSuwayomiDto()
    }

    suspend fun removeExtensionStore(indexUrl: String): SuwayomiExtensionStoreDto? {
        val response = apolloClientFactory.create().mutation(
            RemoveExtensionStoreMutation(RemoveExtensionStoreInput(indexUrl = indexUrl)),
        ).execute()
        val payload = response.data?.removeExtensionStore
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension store result")
        return payload.extensionStore?.amatsubuExtensionStore?.toSuwayomiDto()
    }

    suspend fun updateExtension(
        pkgName: String,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): SuwayomiExtensionDto? {
        val response = apolloClientFactory.create().mutation(
            UpdateExtensionMutation(
                UpdateExtensionInput(
                    id = pkgName,
                    patch = UpdateExtensionPatchInput(
                        install = Optional.present(install),
                        uninstall = Optional.present(uninstall),
                        update = Optional.present(update),
                    ),
                ),
            ),
        ).execute()
        val payload = response.data?.updateExtension
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension update result")
        return payload.extension?.amatsubuExtension?.toSuwayomiDto()
    }

    suspend fun updateExtensions(
        pkgNames: List<String>,
        install: Boolean = false,
        uninstall: Boolean = false,
        update: Boolean = false,
    ): List<SuwayomiExtensionDto> {
        val ids = pkgNames.distinct()
        if (ids.isEmpty()) return emptyList()
        val response = apolloClientFactory.create().mutation(
            UpdateExtensionsMutation(
                UpdateExtensionsInput(
                    ids = ids,
                    patch = UpdateExtensionPatchInput(
                        install = Optional.present(install),
                        uninstall = Optional.present(uninstall),
                        update = Optional.present(update),
                    ),
                ),
            ),
        ).execute()
        val extensions = response.data?.updateExtensions?.extensions
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no extension update result")
        return extensions.map { it.amatsubuExtension.toSuwayomiDto() }
    }
}
