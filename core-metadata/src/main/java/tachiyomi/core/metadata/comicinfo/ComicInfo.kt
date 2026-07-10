package tachiyomi.core.metadata.comicinfo

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

const val COMIC_INFO_FILE = "ComicInfo.xml"

data class ComicInfoMangaMetadata(
    val title: String,
    val description: String?,
    val author: String?,
    val artist: String?,
    val genre: String?,
    val status: Int,
)

fun ComicInfoMangaMetadata.toComicInfo() = ComicInfo(
    series = ComicInfo.Series(title),
    summary = description?.let { ComicInfo.Summary(it) },
    writer = author?.let { ComicInfo.Writer(it) },
    penciller = artist?.let { ComicInfo.Penciller(it) },
    genre = genre?.let { ComicInfo.Genre(it) },
    publishingStatus = ComicInfo.PublishingStatusTachiyomi(
        ComicInfoPublishingStatus.toComicInfoValue(status.toLong()),
    ),
    title = null,
    number = null,
    web = null,
    translator = null,
    inker = null,
    colorist = null,
    letterer = null,
    coverArtist = null,
    tags = null,
    categories = null,
    source = null,
)

fun ComicInfo.toMangaMetadata(): ComicInfoMangaMetadata {
    val genre = listOfNotNull(
        genre?.value,
        tags?.value,
        categories?.value,
    )
        .distinct()
        .joinToString(", ") { it.trim() }
        .takeIf { it.isNotEmpty() }

    val artist = listOfNotNull(
        penciller?.value,
        inker?.value,
        colorist?.value,
        letterer?.value,
        coverArtist?.value,
    )
        .flatMap { it.split(", ") }
        .distinct()
        .joinToString(", ") { it.trim() }
        .takeIf { it.isNotEmpty() }

    return ComicInfoMangaMetadata(
        title = series?.value.orEmpty(),
        description = summary?.value,
        author = writer?.value,
        artist = artist,
        genre = genre,
        status = ComicInfoPublishingStatus.toMangaStatusValue(publishingStatus?.value),
    )
}

// https://anansi-project.github.io/docs/comicinfo/schemas/v2.0
@Suppress("UNUSED")
@Serializable
@XmlSerialName("ComicInfo", "", "")
data class ComicInfo(
    val title: Title?,
    val series: Series?,
    val number: Number?,
    val summary: Summary?,
    val writer: Writer?,
    val penciller: Penciller?,
    val inker: Inker?,
    val colorist: Colorist?,
    val letterer: Letterer?,
    val coverArtist: CoverArtist?,
    val translator: Translator?,
    val genre: Genre?,
    val tags: Tags?,
    val web: Web?,
    val publishingStatus: PublishingStatusTachiyomi?,
    val categories: CategoriesTachiyomi?,
    val source: SourceMihon?,
) {
    @XmlElement(false)
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlSchema: String = "http://www.w3.org/2001/XMLSchema"

    @XmlElement(false)
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlSchemaInstance: String = "http://www.w3.org/2001/XMLSchema-instance"

    @Serializable
    @XmlSerialName("Title", "", "")
    data class Title(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Series", "", "")
    data class Series(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Number", "", "")
    data class Number(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Summary", "", "")
    data class Summary(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Writer", "", "")
    data class Writer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Penciller", "", "")
    data class Penciller(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Inker", "", "")
    data class Inker(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Colorist", "", "")
    data class Colorist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Letterer", "", "")
    data class Letterer(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("CoverArtist", "", "")
    data class CoverArtist(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Translator", "", "")
    data class Translator(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Genre", "", "")
    data class Genre(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Tags", "", "")
    data class Tags(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Web", "", "")
    data class Web(@XmlValue(true) val value: String = "")

    // The spec doesn't have a good field for this
    @Serializable
    @XmlSerialName("PublishingStatusTachiyomi", "http://www.w3.org/2001/XMLSchema", "ty")
    data class PublishingStatusTachiyomi(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("Categories", "http://www.w3.org/2001/XMLSchema", "ty")
    data class CategoriesTachiyomi(@XmlValue(true) val value: String = "")

    @Serializable
    @XmlSerialName("SourceMihon", "http://www.w3.org/2001/XMLSchema", "mh")
    data class SourceMihon(@XmlValue(true) val value: String = "")
}

enum class ComicInfoPublishingStatus(
    val comicInfoValue: String,
    val mangaStatusValue: Int,
) {
    ONGOING("Ongoing", 1),
    COMPLETED("Completed", 2),
    LICENSED("Licensed", 3),
    PUBLISHING_FINISHED("Publishing finished", 4),
    CANCELLED("Cancelled", 5),
    ON_HIATUS("On hiatus", 6),
    UNKNOWN("Unknown", 0),
    ;

    companion object {
        fun toComicInfoValue(value: Long): String {
            return entries.firstOrNull { it.mangaStatusValue == value.toInt() }?.comicInfoValue
                ?: UNKNOWN.comicInfoValue
        }

        fun toMangaStatusValue(value: String?): Int {
            return entries.firstOrNull { it.comicInfoValue == value }?.mangaStatusValue
                ?: UNKNOWN.mangaStatusValue
        }
    }
}
