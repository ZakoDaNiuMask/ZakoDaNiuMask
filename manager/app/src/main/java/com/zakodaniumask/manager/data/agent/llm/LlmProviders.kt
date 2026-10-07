// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.llm

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val JSON = "application/json; charset=utf-8".toMediaType()

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

private fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
    val request = Request.Builder()
        .url(url)
        .post(body.toString().toRequestBody(JSON))
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
    httpClient.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${text.take(500)}")
        }
        return JSONObject(text)
    }
}

/**
 * OpenAI-compatible `/chat/completions` provider. Works with OpenAI, and with
 * any compatible endpoint (e.g. a local Ollama/LM Studio server) by pointing
 * the endpoint at it.
 */
class OpenAiCompatibleProvider : LlmProvider {
    override val type = LlmProviderType.OPENAI

    override suspend fun chat(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        request: LlmRequest,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val base = endpoint.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val messages = JSONArray()
        if (request.system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", request.system))
        }
        request.messages.forEach { message ->
            val entry = JSONObject().put("role", message.role)
            when {
                message.role == "assistant" && message.toolCalls.isNotEmpty() -> {
                    entry.put("content", message.content.ifBlank { JSONObject.NULL })
                    val calls = JSONArray()
                    message.toolCalls.forEach { call ->
                        calls.put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", call.name).put("arguments", call.arguments),
                                )
                        )
                    }
                    entry.put("tool_calls", calls)
                }

                message.role == "tool" -> {
                    entry.put("content", message.content)
                    entry.put("tool_call_id", message.toolCallId ?: "")
                }

                else -> entry.put("content", message.content)
            }
            messages.put(entry)
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)

        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                tools.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("parameters", tool.parameters),
                        )
                )
            }
            body.put("tools", tools)
        }

        val headers = mutableMapOf<String, String>()
        if (apiKey.isNotBlank()) headers["Authorization"] = "Bearer $apiKey"

        val json = postJson("$base/chat/completions", headers, body)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw RuntimeException("no choices in response")
        val message = choice.optJSONObject("message") ?: JSONObject()
        val text = message.optString("content", "")
        val toolCalls = buildList {
            val calls = message.optJSONArray("tool_calls") ?: JSONArray()
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val function = call.optJSONObject("function") ?: continue
                add(
                    LlmToolCall(
                        id = call.optString("id"),
                        name = function.optString("name"),
                        arguments = function.optString("arguments", "{}"),
                    )
                )
            }
        }
        LlmResponse(
            text = text,
            toolCalls = toolCalls,
            finishReason = choice.optString("finish_reason", ""),
        )
    }
}

/** Anthropic Messages API provider. */
class AnthropicProvider : LlmProvider {
    override val type = LlmProviderType.ANTHROPIC

    override suspend fun chat(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        request: LlmRequest,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val base = endpoint.ifBlank { "https://api.anthropic.com" }.trimEnd('/')
        val messages = JSONArray()
        request.messages.forEach { message ->
            when (message.role) {
                "assistant" -> {
                    val blocks = JSONArray()
                    if (message.content.isNotBlank()) {
                        blocks.put(JSONObject().put("type", "text").put("text", message.content))
                    }
                    message.toolCalls.forEach { call ->
                        val input = try {
                            JSONObject(call.arguments)
                        } catch (_: Throwable) {
                            JSONObject()
                        }
                        blocks.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", input)
                        )
                    }
                    messages.put(JSONObject().put("role", "assistant").put("content", blocks))
                }

                "tool" -> {
                    val block = JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", message.toolCallId ?: "")
                        .put("content", message.content)
                    messages.put(JSONObject().put("role", "user").put("content", JSONArray().put(block)))
                }

                else -> {
                    messages.put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", message.content)
                    )
                }
            }
        }

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("messages", messages)
        if (request.system.isNotBlank()) body.put("system", request.system)
        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                tools.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("input_schema", tool.parameters)
                )
            }
            body.put("tools", tools)
        }

        val headers = mutableMapOf(
            "anthropic-version" to "2023-06-01",
        )
        if (apiKey.isNotBlank()) headers["x-api-key"] = apiKey

        val json = postJson("$base/v1/messages", headers, body)
        val content = json.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> toolCalls.add(
                    LlmToolCall(
                        id = block.optString("id"),
                        name = block.optString("name"),
                        arguments = block.optJSONObject("input")?.toString() ?: "{}",
                    )
                )
            }
        }
        LlmResponse(
            text = text.toString(),
            toolCalls = toolCalls,
            finishReason = json.optString("stop_reason", ""),
        )
    }
}

fun providerFor(type: LlmProviderType): LlmProvider = when (type) {
    LlmProviderType.OPENAI -> OpenAiCompatibleProvider()
    LlmProviderType.ANTHROPIC -> AnthropicProvider()
}
