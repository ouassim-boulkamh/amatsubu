package eu.kanade.tachiyomi.data.suwayomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuwayomiMappingsTest {

    @Test
    fun `server URL resolver preserves absolute asset URLs`() {
        assertEquals(
            "https://cdn.example.org/cover.jpg",
            resolveServerUrl("https://example.org/suwayomi", "https://cdn.example.org/cover.jpg"),
        )
    }

    @Test
    fun `server URL resolver preserves uppercase absolute asset URLs`() {
        assertEquals(
            "HTTPS://cdn.example.org/cover.jpg",
            resolveServerUrl("https://example.org/suwayomi", "HTTPS://cdn.example.org/cover.jpg"),
        )
    }

    @Test
    fun `server URL resolver joins reverse proxy base path with leading slash asset path`() {
        assertEquals(
            "https://example.org/suwayomi/api/v1/manga/1/thumbnail",
            resolveServerUrl("https://example.org/suwayomi/", "/api/v1/manga/1/thumbnail"),
        )
    }

    @Test
    fun `server URL resolver joins explicit port base URL with relative page path`() {
        assertEquals(
            "http://192.0.2.10:4567/api/v1/chapter/2/page/1",
            resolveServerUrl("http://192.0.2.10:4567", "api/v1/chapter/2/page/1"),
        )
    }
}
