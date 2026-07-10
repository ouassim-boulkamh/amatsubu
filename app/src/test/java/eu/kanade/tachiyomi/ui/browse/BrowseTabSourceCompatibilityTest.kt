package eu.kanade.tachiyomi.ui.browse

import eu.kanade.tachiyomi.data.suwayomi.SuwayomiSourceDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseTabSourceCompatibilityTest {

    @Test
    fun `configurable Suwayomi source maps to narrow installed extension source`() {
        val source = suwayomiSource(isConfigurable = true).toExtensionSource()

        assertEquals(728120260708001L, source.id)
        assertEquals("Amatsubu Test Local", source.name)
        assertEquals("all", source.lang)
        assertTrue(source.supportsLatest)
        assertTrue(source.isConfigurable)
    }

    @Test
    fun `non-configurable Suwayomi source keeps preferences unavailable`() {
        val source = suwayomiSource(isConfigurable = false).toExtensionSource()

        assertEquals(false, source.isConfigurable)
        assertEquals(728120260708001L, source.id)
        assertEquals("Amatsubu Test Local (ALL)", source.toString())
    }

    private fun suwayomiSource(isConfigurable: Boolean): SuwayomiSourceDto {
        return SuwayomiSourceDto(
            displayName = "Amatsubu Test Local",
            id = "728120260708001",
            isConfigurable = isConfigurable,
            lang = "all",
            name = "Amatsubu Test Local",
            supportsLatest = true,
        )
    }
}
