package eu.kanade.tachiyomi.util

import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.time.Instant

fun Manga.removeCovers(coverCache: CoverCache = Injekt.get()): Manga {
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

suspend fun Manga.editCover(
    stream: InputStream,
    coverCache: CoverCache = Injekt.get(),
): Manga {
    if (!favorite) return this

    coverCache.setCustomCoverToCache(this, stream)
    return copy(coverLastModified = Instant.now().toEpochMilli())
}
