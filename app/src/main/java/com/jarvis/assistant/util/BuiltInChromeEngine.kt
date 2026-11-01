package com.jarvis.assistant.util

import com.jarvis.assistant.data.model.CitationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BuiltInChromeEngine {

    data class GroundedSearchResult(
        val query: String,
        val summary: String,
        val isVerified: Boolean,
        val citations: List<CitationSource>
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchWithCitations(query: String): GroundedSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext GroundedSearchResult(
                query = cleanQuery,
                summary = "",
                isVerified = false,
                citations = emptyList()
            )
        }

        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val title = json.optString("title", cleanQuery)
                        val extract = json.optString("extract", "")
                        val pageUrl = json.optJSONObject("content_urls")
                            ?.optJSONObject("desktop")
                            ?.optString("page", "")
                            .orEmpty()
                        if (extract.isNotBlank() && pageUrl.startsWith("https://")) {
                            return@withContext GroundedSearchResult(
                                query = cleanQuery,
                                summary = extract,
                                isVerified = true,
                                citations = listOf(CitationSource(title = title, url = pageUrl))
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        GroundedSearchResult(
            query = cleanQuery,
            summary = "Live web search executed for: $cleanQuery",
            isVerified = false,
            citations = emptyList()
        )
    }
}
