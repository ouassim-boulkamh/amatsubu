package eu.kanade.tachiyomi.data.suwayomi

import com.apollographql.apollo.api.Optional
import eu.kanade.tachiyomi.data.suwayomi.generated.FetchSourceMangaMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.SourceDetailsQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.SourceListQuery
import eu.kanade.tachiyomi.data.suwayomi.generated.UpdateSourcePreferenceMutation
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchSourceMangaInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.UpdateSourcePreferenceInput
import eu.kanade.tachiyomi.data.suwayomi.generated.type.FetchSourceMangaType as GeneratedFetchSourceMangaType

internal class SuwayomiSourceGraphQlDomain(
    private val apolloClientFactory: SuwayomiApolloClientFactory,
) {

    suspend fun sourceList(): List<SuwayomiSourceDto> {
        val response = apolloClientFactory.create().query(SourceListQuery()).execute()
        val sourceNodes = response.data?.sources?.nodes
            ?: error(response.errors?.firstOrNull()?.message ?: "Suwayomi server returned no source list")
        return sourceNodes.map { source ->
            SuwayomiSourceDto(
                baseUrl = source.baseUrl,
                contentWarning = source.contentWarning.rawValue,
                displayName = source.displayName,
                homeUrl = source.homeUrl,
                iconUrl = source.iconUrl,
                id = source.id.toString(),
                isConfigurable = source.isConfigurable,
                isNsfw = source.isNsfw,
                lang = source.lang,
                name = source.name,
                supportsLatest = source.supportsLatest,
            )
        }
    }

    suspend fun fetchSourceManga(
        sourceId: String,
        type: FetchSourceMangaType,
        page: Int,
        queryText: String? = null,
        filters: List<SuwayomiSourceFilterChange> = emptyList(),
    ): SourceMangaPageDto {
        val response = apolloClientFactory.create().mutation(
            FetchSourceMangaMutation(
                FetchSourceMangaInput(
                    source = sourceId,
                    type = GeneratedFetchSourceMangaType.valueOf(type.name),
                    page = page,
                    query = Optional.presentIfNotNull(queryText),
                    filters = Optional.presentIfNotNull(
                        filters.takeIf {
                            it.isNotEmpty()
                        }?.map { it.toGeneratedInput() },
                    ),
                ),
            ),
        ).execute()
        val payload = response.data?.fetchSourceManga
            ?: error(
                response.errors?.firstOrNull()?.message ?: response.exception?.message
                    ?: "Suwayomi server returned no source manga page",
            )
        return SourceMangaPageDto(payload.hasNextPage, payload.mangas.map { it.amatsubuManga.toSuwayomiDto() })
    }

    suspend fun sourceDetails(sourceId: String): SuwayomiSourceDetailsDto {
        val response = apolloClientFactory.create().query(SourceDetailsQuery(sourceId)).execute()
        val source = response.data?.source
            ?: error(
                response.errors?.firstOrNull()?.message ?: response.exception?.message
                    ?: "Suwayomi server returned no source",
            )
        return SuwayomiSourceDetailsDto(
            baseUrl = source.baseUrl,
            contentWarning = source.contentWarning.rawValue,
            displayName = source.displayName,
            homeUrl = source.homeUrl,
            iconUrl = source.iconUrl,
            id = source.id.toString(),
            isConfigurable = source.isConfigurable,
            isNsfw = source.isNsfw,
            lang = source.lang,
            name = source.name,
            supportsLatest = source.supportsLatest,
            filters = source.filters.map { it.amatsubuSourceFilter.toSuwayomiDto() },
            preferences = source.preferences.map { it.amatsubuSourcePreference.toSuwayomiDto() },
        )
    }

    suspend fun sourceFilters(sourceId: String): List<SuwayomiSourceFilterDto> {
        return sourceDetails(sourceId).filters
    }

    suspend fun sourcePreferences(sourceId: String): List<SuwayomiSourcePreferenceDto> {
        return sourceDetails(sourceId).preferences
    }

    suspend fun updateSourcePreference(
        sourceId: String,
        change: SuwayomiSourcePreferenceChange,
    ): List<SuwayomiSourcePreferenceDto> {
        val response = apolloClientFactory.create().mutation(
            UpdateSourcePreferenceMutation(
                UpdateSourcePreferenceInput(source = sourceId, change = change.toGeneratedInput()),
            ),
        ).execute()
        return response.data?.updateSourcePreference?.preferences?.map { it.amatsubuSourcePreference.toSuwayomiDto() }
            ?: error(
                response.errors?.firstOrNull()?.message ?: response.exception?.message
                    ?: "Suwayomi server returned no source preferences",
            )
    }
}
