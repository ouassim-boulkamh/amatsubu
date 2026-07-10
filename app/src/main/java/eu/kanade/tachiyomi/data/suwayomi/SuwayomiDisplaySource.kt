package eu.kanade.tachiyomi.data.suwayomi

data class SuwayomiDisplaySource(
    val id: Long,
    val name: String,
    val lang: String,
    val supportsLatest: Boolean,
) {
    constructor(
        dto: SuwayomiSourceDto,
        fallbackId: Long,
    ) : this(
        id = dto.id.toLongOrNull() ?: fallbackId,
        name = dto.displayName.ifBlank { dto.name },
        lang = dto.lang,
        supportsLatest = dto.supportsLatest,
    )

    override fun toString(): String = if (lang.isEmpty()) name else "$name (${lang.uppercase()})"
}
