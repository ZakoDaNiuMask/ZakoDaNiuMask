// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.llm

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private val JSON = "application/json; charset=utf-8".toMediaType()

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

private fun buildRequest(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
): Request = Request.Builder()
    .url(url)
    .post(body.toString().toRequestBody(JSON))
    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
    .build()

private fun Response.requireBody(): okhttp3.ResponseBody {
    if (!isSuccessful) {
        throw RuntimeException("HTTP $code: ${body?.string().orEmpty().take(500)}")
    }
    return body ?: throw RuntimeException("empty response body")
}

private fun okhttp3.ResponseBody.isEventStream(): Boolean =
    contentType()?.let { it.type == "text" && it.subtype == "event-stream" } ?: false

/** Resolve the request URL from the endpoint, an optional path override and the provider default. */
private fun resolveUrl(base: String, apiPath: String, defaultPath: String): String {
    val path = apiPath.trim()
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val suffix = when {
        path.isEmpty() -> defaultPath
        path.startsWith("/") -> path
        else -> "/$path"
    }
    return base + suffix
}

/** One-shot JSON POST (used by non-streaming providers such as Gemini). */
private fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
    httpClient.newCall(buildRequest(url, headers, body)).execute().use { response ->
        return JSONObject(response.requireBody().string())
    }
}

/** Apply the user-configured User-Agent and extra headers. */
private fun MutableMap<String, String>.applyCommon(config: LlmHttpConfig) {
    if (config.userAgent.isNotBlank()) this["User-Agent"] = config.userAgent
    config.extraHeaders.lineSequence().forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) {
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isNotEmpty()) this[key] = value
        }
    }
}

/**
 * OpenAI-compatible `/chat/completions` provider with SSE streaming. Works with
 * OpenAI and any compatible endpoint (e.g. a local Ollama/LM Studio server).
 */
class OpenAiCompatibleProvider : LlmProvider {
    override val type = LlmProviderType.OPENAI

    override suspend fun chat(
        config: LlmHttpConfig,
        request: LlmRequest,
        onDelta: (String) -> Unit,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val base = config.endpoint.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val url = resolveUrl(base, config.apiPath, "/chat/completions")
        val body = buildBody(config.model, config.temperature, config.maxTokens, request)
        if (config.thinking != ThinkingLevel.OFF) {
            body.put("reasoning_effort", config.thinking.id)
        }
        val headers = mutableMapOf<String, String>()
        if (config.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${config.apiKey}"
        headers.applyCommon(config)

        httpClient.newCall(buildRequest(url, headers, body)).execute().use { response ->
            val responseBody = response.requireBody()
            if (!responseBody.isEventStream()) {
                return@use parseNonStream(JSONObject(responseBody.string()), onDelta)
            }
            responseBody.source().use { source ->
                val text = StringBuilder()
                val calls = LinkedHashMap<Int, ToolCallAccumulator>()
                var finishReason = ""
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val chunk = try {
                        JSONObject(payload)
                    } catch (_: Throwable) {
                        continue
                    }
                    val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    if (choice.optString("finish_reason").isNotEmpty()) {
                        finishReason = choice.optString("finish_reason")
                    }
                    val delta = choice.optJSONObject("delta") ?: continue
                    val content = delta.optString("content", "")
                    if (content.isNotEmpty()) {
                        text.append(content)
                        onDelta(content)
                    }
                    val toolCalls = delta.optJSONArray("tool_calls") ?: continue
                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.optJSONObject(i) ?: continue
                        val index = tc.optInt("index", i)
                        val acc = calls.getOrPut(index) { ToolCallAccumulator() }
                        tc.optString("id").takeIf { it.isNotEmpty() }?.let { acc.id = it }
                        val fn = tc.optJSONObject("function") ?: continue
                        fn.optString("name").takeIf { it.isNotEmpty() }?.let { acc.name = it }
                        acc.arguments.append(fn.optString("arguments", ""))
                    }
                }
                LlmResponse(text.toString(), calls.values.map { it.toCall() }, finishReason)
            }
        }
    }

    private fun buildBody(
        model: String,
        temperature: Double,
        maxTokens: Int,
        request: LlmRequest,
    ): JSONObject {
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
            .put("stream", true)
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
        return body
    }

    private fun parseNonStream(json: JSONObject, onDelta: (String) -> Unit): LlmResponse {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw RuntimeException("no choices in response")
        val message = choice.optJSONObject("message") ?: JSONObject()
        val text = message.optString("content", "")
        if (text.isNotEmpty()) onDelta(text)
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
        return LlmResponse(text, toolCalls, choice.optString("finish_reason", ""))
    }
}

/** Anthropic Messages API provider with SSE streaming. */
class AnthropicProvider : LlmProvider {
    override val type = LlmProviderType.ANTHROPIC

    override suspend fun chat(
        config: LlmHttpConfig,
        request: LlmRequest,
        onDelta: (String) -> Unit,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val base = config.endpoint.ifBlank { "https://api.anthropic.com" }.trimEnd('/')
        val url = resolveUrl(base, config.apiPath, "/v1/messages")
        val body = buildBody(config.model, config.temperature, config.maxTokens, request)
        if (config.thinking != ThinkingLevel.OFF && config.maxTokens > 1024) {
            val budget = (config.maxTokens / 2).coerceIn(1024, 8192)
            body.put(
                "thinking",
                JSONObject().put("type", "enabled").put("budget_tokens", budget),
            )
        }
        val headers = mutableMapOf("anthropic-version" to "2023-06-01")
        if (config.apiKey.isNotBlank()) headers["x-api-key"] = config.apiKey
        headers.applyCommon(config)

        httpClient.newCall(buildRequest(url, headers, body)).execute().use { response ->
            val responseBody = response.requireBody()
            if (!responseBody.isEventStream()) {
                return@use parseNonStream(JSONObject(responseBody.string()), onDelta)
            }
            responseBody.source().use { source ->
                val text = StringBuilder()
                val calls = LinkedHashMap<Int, ToolCallAccumulator>()
                var stopReason = ""
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val event = try {
                        JSONObject(payload)
                    } catch (_: Throwable) {
                        continue
                    }
                    when (event.optString("type")) {
                        "content_block_start" -> {
                            val index = event.optInt("index", 0)
                            val block = event.optJSONObject("content_block") ?: continue
                            if (block.optString("type") == "tool_use") {
                                val acc = calls.getOrPut(index) { ToolCallAccumulator() }
                                acc.id = block.optString("id")
                                acc.name = block.optString("name")
                            }
                        }

                        "content_block_delta" -> {
                            val index = event.optInt("index", 0)
                            val delta = event.optJSONObject("delta") ?: continue
                            when (delta.optString("type")) {
                                "text_delta" -> {
                                    val chunk = delta.optString("text")
                                    if (chunk.isNotEmpty()) {
                                        text.append(chunk)
                                        onDelta(chunk)
                                    }
                                }

                                "input_json_delta" -> {
                                    calls.getOrPut(index) { ToolCallAccumulator() }
                                        .arguments.append(delta.optString("partial_json"))
                                }
                            }
                        }

                        "message_delta" -> {
                            stopReason = event.optJSONObject("delta")
                                ?.optString("stop_reason")
                                ?: stopReason
                        }
                    }
                }
                LlmResponse(text.toString(), calls.values.map { it.toCall() }, stopReason)
            }
        }
    }

    private fun buildBody(
        model: String,
        temperature: Double,
        maxTokens: Int,
        request: LlmRequest,
    ): JSONObject {
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

                else -> messages.put(
                    JSONObject().put("role", "user").put("content", message.content)
                )
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("messages", messages)
            .put("stream", true)
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
        return body
    }

    private fun parseNonStream(json: JSONObject, onDelta: (String) -> Unit): LlmResponse {
        val content = json.optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> {
                    val chunk = block.optString("text")
                    text.append(chunk)
                    if (chunk.isNotEmpty()) onDelta(chunk)
                }

                "tool_use" -> toolCalls.add(
                    LlmToolCall(
                        id = block.optString("id"),
                        name = block.optString("name"),
                        arguments = block.optJSONObject("input")?.toString() ?: "{}",
                    )
                )
            }
        }
        return LlmResponse(text.toString(), toolCalls, json.optString("stop_reason", ""))
    }
}

private class ToolCallAccumulator {
    var id: String = ""
    var name: String = ""
    val arguments = StringBuilder()

    fun toCall(): LlmToolCall = LlmToolCall(
        id = id.ifBlank { "call_${name.ifBlank { "tool" }}_${hashCode()}" },
        name = name,
        arguments = arguments.toString().ifBlank { "{}" },
    )
}

/** Google Gemini (`generativelanguage.googleapis.com`), non-streaming. */
class GeminiProvider : LlmProvider {
    override val type = LlmProviderType.GEMINI

    override suspend fun chat(
        config: LlmHttpConfig,
        request: LlmRequest,
        onDelta: (String) -> Unit,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val base = config.endpoint.ifBlank { "https://generativelanguage.googleapis.com" }.trimEnd('/')
        val defaultPath = "/v1beta/models/${config.model}:generateContent"
        var url = resolveUrl(base, config.apiPath, defaultPath)
        if (config.apiKey.isNotBlank()) {
            url += (if (url.contains('?')) "&" else "?") + "key=${config.apiKey}"
        }

        val contents = JSONArray()
        val toolNamesById = HashMap<String, String>()
        request.messages.forEach { message ->
            when (message.role) {
                "assistant" -> {
                    val parts = JSONArray()
                    if (message.content.isNotBlank()) {
                        parts.put(JSONObject().put("text", message.content))
                    }
                    message.toolCalls.forEach { call ->
                        toolNamesById[call.id] = call.name
                        val args = try {
                            JSONObject(call.arguments)
                        } catch (_: Throwable) {
                            JSONObject()
                        }
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject().put("name", call.name).put("args", args),
                            )
                        )
                    }
                    contents.put(JSONObject().put("role", "model").put("parts", parts))
                }

                "tool" -> {
                    val name = toolNamesById[message.toolCallId] ?: "tool"
                    val response = JSONObject().put("content", message.content)
                    val parts = JSONArray().put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject().put("name", name).put("response", response),
                        )
                    )
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }

                else -> contents.put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
                )
            }
        }

        val body = JSONObject().put("contents", contents)
        if (request.system.isNotBlank()) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))),
            )
        }
        body.put(
            "generationConfig",
            JSONObject().put("temperature", config.temperature).put("maxOutputTokens", config.maxTokens),
        )
        if (request.tools.isNotEmpty()) {
            val declarations = JSONArray()
            request.tools.forEach { tool ->
                declarations.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", tool.parameters)
                )
            }
            body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
        }

        val headers = mutableMapOf<String, String>()
        headers.applyCommon(config)
        val json = postJson(url, headers, body)
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw RuntimeException("no candidates in response")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()
        var index = 0
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val chunk = part.optString("text", "")
            if (chunk.isNotEmpty()) text.append(chunk)
            val call = part.optJSONObject("functionCall")
            if (call != null) {
                toolCalls.add(
                    LlmToolCall(
                        id = "gemini_call_${index++}",
                        name = call.optString("name"),
                        arguments = call.optJSONObject("args")?.toString() ?: "{}",
                    )
                )
            }
        }
        if (text.isNotEmpty()) onDelta(text.toString())
        LlmResponse(text.toString(), toolCalls, candidate.optString("finishReason", ""))
    }
}

fun providerFor(type: LlmProviderType): LlmProvider = when (type) {
    LlmProviderType.OPENAI -> OpenAiCompatibleProvider()
    LlmProviderType.ANTHROPIC -> AnthropicProvider()
    LlmProviderType.GEMINI -> GeminiProvider()
}
