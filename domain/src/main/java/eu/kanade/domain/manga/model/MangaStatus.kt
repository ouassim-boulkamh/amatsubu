package eu.kanade.domain.manga.model

enum class MangaStatus(val value: Long) {
    UNKNOWN(0L),
    ONGOING(1L),
    COMPLETED(2L),
    LICENSED(3L),
    PUBLISHING_FINISHED(4L),
    CANCELLED(5L),
    ON_HIATUS(6L),
    ;

    companion object {
        fun from(value: Long): MangaStatus {
            return entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}
