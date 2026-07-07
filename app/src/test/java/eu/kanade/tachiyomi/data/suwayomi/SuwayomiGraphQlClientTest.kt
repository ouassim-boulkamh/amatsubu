package eu.kanade.tachiyomi.data.suwayomi

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class SuwayomiGraphQlClientTest {

    @Test
    fun `Tsumiru issue 27 named category query filters by inLibrary and category id`() = runTest {
        var requestBody = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestBody = chain.request().bodyString()
                jsonResponse(
                    chain.request(),
                    """
                        {
                          "data": {
                            "mangas": {
                              "nodes": [
                                {
                                  "id": 1,
                                  "inLibrary": true,
                                  "sourceId": "1",
                                  "title": "Kept",
                                  "url": "/manga/1"
                                },
                                {
                                  "id": 2,
                                  "inLibrary": false,
                                  "sourceId": "1",
                                  "title": "Removed",
                                  "url": "/manga/2"
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                )
            }
            .build()
        val graphQlClient = SuwayomiGraphQlClient(
            client = client,
            json = Json { ignoreUnknownKeys = true },
            endpoint = { "http://example.org/api/graphql" },
        )

        val mangas = graphQlClient.getCategoryMangas(categoryId = 7)

        assertEquals(listOf(1), mangas.map { it.id })
        assertTrue(requestBody.contains("GetNamedCategoryMangas"))
        assertTrue(requestBody.contains("condition: { inLibrary: ${'$'}"))
        assertTrue(requestBody.contains("categoryIds"))
        assertTrue(requestBody.contains("\"categoryIds\":[7]"))
        assertTrue(requestBody.contains("\"inLibrary\":true"))
    }

    @Test
    fun `Batch F schema drift errors are surfaced as compatibility failures`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                jsonResponse(
                    chain.request(),
                    """
                        {
                          "data": null,
                          "errors": [
                            {
                              "message": "Unknown argument 'condition' on field 'Query.sources'."
                            }
                          ]
                        }
                    """.trimIndent(),
                )
            }
            .build()
        val graphQlClient = SuwayomiGraphQlClient(
            client = client,
            json = Json { ignoreUnknownKeys = true },
            endpoint = { "http://example.org/api/graphql" },
        )

        val error = assertThrows<IllegalStateException> {
            graphQlClient.sourceList()
        }

        assertEquals("Unknown argument 'condition' on field 'Query.sources'.", error.message)
    }

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun jsonResponse(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
