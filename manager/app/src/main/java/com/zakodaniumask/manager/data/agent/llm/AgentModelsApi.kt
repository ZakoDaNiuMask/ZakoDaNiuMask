// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.llm

import com.zakodaniumask.manager.data.agent.AgentProviderProfile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches the list of model ids offered by a provider's models endpoint. The
 * shapes mirror OpenMinis' ModelsApi ports:
 *  - OpenAI-compatible: `GET {base}/models` -> `data[].id`
 *  - Anthropic: `GET {base}/v1/models` -> `data[].id`
 *  - Gemini: `GET {base}/v1beta/models?key=...` -> `models[].name`
 */
object AgentModelsApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(profile: AgentProviderProfile): List<String> = withContext(Dispatchers.IO) {
        val base = profile.effectiveEndpoint.trimEnd('/')
        require(base.isNotBlank()) { "No endpoint configured" }
        val request = when (profile.type) {
            LlmProviderType.GEMINI -> {
                val baseUrl = if (base.endsWith("/v1beta")) base else "$base/v1beta"
                Request.Builder()
                    .url("$baseUrl/models?key=${profile.apiKey}")
                    .get()
                    .applyHeaders(profile)
                    .build()
            }

            LlmProviderType.ANTHROPIC -> {
                val baseUrl = if (base.endsWith("/v1")) base else "$base/v1"
                Request.Builder()
                    .url("$baseUrl/models")
                    .get()
                    .header("x-api-key", profile.apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .applyHeaders(profile)
                    .build()
            }

            else -> Request.Builder()
                .url("$base/models")
                .get()
                .header("Authorization", "Bearer ${profile.apiKey}")
                .applyHeaders(profile)
                .build()
        }
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${body.take(300)}")
            }
            parse(profile.type, body)
        }
    }

    private fun parse(type: LlmProviderType, body: String): List<String> {
        val root = JSONObject(body)
        val ids = buildList {
            if (type == LlmProviderType.GEMINI) {
                val models = root.optJSONArray("models") ?: return@buildList
                for (i in 0 until models.length()) {
                    val name = models.optJSONObject(i)?.optString("name").orEmpty()
                    if (name.isNotBlank()) add(name.removePrefix("models/"))
                }
            } else {
                val data = root.optJSONArray("data") ?: return@buildList
                for (i in 0 until data.length()) {
                    val id = data.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank()) add(id)
                }
            }
        }
        return ids.distinct().sorted()
    }

    private fun Request.Builder.applyHeaders(profile: AgentProviderProfile): Request.Builder = apply {
        if (profile.userAgent.isNotBlank()) header("User-Agent", profile.userAgent)
        profile.extraHeaders.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) header(key, value)
            }
        }
    }
}
