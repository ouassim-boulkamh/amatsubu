@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.suwayomi.ClientChapterCopyFreshness
import eu.kanade.tachiyomi.data.suwayomi.ClientDeviceChapterCopy

internal sealed interface ReaderChapterSource {
    data object Suwayomi : ReaderChapterSource
    data class DeviceCopy(val copy: ClientDeviceChapterCopy) : ReaderChapterSource
}

internal fun resolveReaderChapterSource(copy: ClientDeviceChapterCopy?): ReaderChapterSource {
    return when {
        copy?.freshness == ClientChapterCopyFreshness.FRESH && copy.isComplete -> {
            ReaderChapterSource.DeviceCopy(copy)
        }
        else -> ReaderChapterSource.Suwayomi
    }
}
