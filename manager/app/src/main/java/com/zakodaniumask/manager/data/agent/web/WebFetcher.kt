// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.web

import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Fetches a URL and converts it to plain text. Uses the Jina reader by default
 * (returns clean markdown) with a local jsoup fallback, or a fixed reader.
 *
 * Local/loopback targets are refused unless the web config allows local access.
 */
class WebFetcher(
    private val client: OkHttpClient,
    private val cfg: WebConfig,
) {
    fun fetch(url: String, maxChars: Int): String {
        val target = url.trim()
        require(target.startsWith("http://") || target.startsWith("https://")) {
            "only http(s) urls are supported"
        }
        guardLocal(target)
        val limit = (maxChars.takeIf { it > 0 } ?: DEFAULT_MAX_CHARS)
            .coerceIn(256, MAX_MAX_CHARS)
        val text = when (cfg.fetchReader) {
            WebFetchReader.JINA -> jina(target)
            WebFetchReader.LOCAL -> local(target)
            WebFetchReader.AUTO -> runCatching { jina(target) }.getOrElse { local(target) }
        }
        return if (text.length > limit) text.take(limit) + "\n...[truncated]" else text
    }

    private fun jina(url: String): String {
        val request = Request.Builder()
            .url("https://r.jina.ai/$url")
            .get()
            .apply {
                if (cfg.fetchApiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${cfg.fetchApiKey}")
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                error("jina HTTP ${response.code}")
            }
            return body
        }
    }

    private fun local(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val doc = Jsoup.parse(body, url)
            doc.select("script,style,noscript,nav,header,footer,svg").remove()
            return doc.body()?.text().orEmpty()
        }
    }

    private fun guardLocal(url: String) {
        if (cfg.allowLocal) return
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return
        if (isPrivateHost(host)) {
            error("refusing to fetch a local/private address ('$host'); enable local access in settings")
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
        if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) {
            return true
        }
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return when {
                a == 0 || a == 10 || a == 127 -> true
                a == 172 && b in 16..31 -> true
                a == 192 && b == 168 -> true
                a == 169 && b == 254 -> true
                a == 100 && b in 64..127 -> true
                else -> false
            }
        }
        return false
    }

    private companion object {
        const val DEFAULT_MAX_CHARS = 40_000
        const val MAX_MAX_CHARS = 200_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/134.0.0.0 Mobile Safari/537.36"
    }
}
