// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.web

import com.zakodaniumask.manager.data.agent.AgentSettingsRepository
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Provider-agnostic web search + page fetch for the agent.
 *
 * Backends: DuckDuckGo (no key), SearXNG (self-hosted), Tavily, Brave, Serper.
 * Fetch: Jina Reader first with a local jsoup fallback (or a fixed reader).
 */
class WebSearchRepository(
    private val settings: AgentSettingsRepository,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun config(): WebConfig = settings.load().let { s ->
        WebConfig(
            enabled = s.webSearchEnabled,
            backend = s.webSearchBackend,
            endpoint = s.webSearchEndpoint,
            apiKey = s.webSearchApiKey,
            maxResults = s.webSearchMaxResults,
            fetchReader = s.webFetchReader,
            fetchApiKey = s.webFetchApiKey,
            allowLocal = s.webFetchAllowLocal,
        )
    }

    suspend fun search(query: String, maxResults: Int): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val cfg = config()
            if (!cfg.enabled) error("web search is disabled in agent settings")
            val limit = (maxResults.takeIf { it > 0 } ?: cfg.maxResults).coerceIn(1, 20)
            when (cfg.backend) {
                WebSearchBackend.DUCKDUCKGO -> searchDuckDuckGo(query, limit)
                WebSearchBackend.SEARXNG -> searchSearxng(cfg, query, limit)
                WebSearchBackend.TAVILY -> searchTavily(cfg, query, limit)
                WebSearchBackend.BRAVE -> searchBrave(cfg, query, limit)
                WebSearchBackend.SERPER -> searchSerper(cfg, query, limit)
            }
        }

    suspend fun fetch(url: String, maxChars: Int): String = withContext(Dispatchers.IO) {
        WebFetcher(client, config()).fetch(url, maxChars)
    }

    // ---- backends ----

    private fun searchDuckDuckGo(query: String, limit: Int): List<SearchResult> {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val body = get(url, browserHeaders())
        val doc = org.jsoup.Jsoup.parse(body)
        val results = mutableListOf<SearchResult>()
        doc.select("div.result").forEach { el ->
            val link = el.selectFirst("a.result__a") ?: return@forEach
            val title = link.text()
            val href = unwrapDuckDuckGo(link.attr("href"))
            val snippet = el.selectFirst(".result__snippet")?.text().orEmpty()
            if (href.isNotBlank()) results += SearchResult(title, href, snippet)
        }
        return results.take(limit)
    }

    private fun searchSearxng(cfg: WebConfig, query: String, limit: Int): List<SearchResult> {
        val base = cfg.endpoint.trimEnd('/').ifBlank { error("SearXNG endpoint not configured") }
        val url = "$base/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json"
        val json = JSONObject(get(url, browserHeaders()))
        val arr = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    SearchResult(
                        item.optString("title"),
                        item.optString("url"),
                        item.optString("content"),
                    )
                )
                if (size >= limit) break
            }
        }
    }

    private fun searchTavily(cfg: WebConfig, query: String, limit: Int): List<SearchResult> {
        val payload = JSONObject()
            .put("api_key", cfg.apiKey)
            .put("query", query)
            .put("max_results", limit)
        val json = JSONObject(
            postJson("https://api.tavily.com/search", payload, browserHeaders())
        )
        val arr = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    SearchResult(
                        item.optString("title"),
                        item.optString("url"),
                        item.optString("content"),
                    )
                )
            }
        }
    }

    private fun searchBrave(cfg: WebConfig, query: String, limit: Int): List<SearchResult> {
        val url = "https://api.search.brave.com/res/v1/web/search?count=$limit&q=" +
            URLEncoder.encode(query, "UTF-8")
        val headers = browserHeaders() + ("X-Subscription-Token" to cfg.apiKey)
        val json = JSONObject(get(url, headers))
        val arr = json.optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    SearchResult(
                        item.optString("title"),
                        item.optString("url"),
                        item.optString("description"),
                    )
                )
            }
        }
    }

    private fun searchSerper(cfg: WebConfig, query: String, limit: Int): List<SearchResult> {
        val payload = JSONObject().put("q", query).put("num", limit)
        val json = JSONObject(
            postJson("https://google.serper.dev/search", payload, browserHeaders() + ("X-API-KEY" to cfg.apiKey))
        )
        val arr = json.optJSONArray("organic") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    SearchResult(
                        item.optString("title"),
                        item.optString("link"),
                        item.optString("snippet"),
                    )
                )
            }
        }
    }

    // ---- http helpers ----

    private fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/json",
    )

    private fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(300)}")
            return body
        }
    }

    private fun postJson(url: String, payload: JSONObject, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(300)}")
            return body
        }
    }

    private fun unwrapDuckDuckGo(href: String): String {
        val marker = "uddg="
        val idx = href.indexOf(marker)
        if (idx < 0) return href
        val encoded = href.substring(idx + marker.length).substringBefore('&')
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/134.0.0.0 Mobile Safari/537.36"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
