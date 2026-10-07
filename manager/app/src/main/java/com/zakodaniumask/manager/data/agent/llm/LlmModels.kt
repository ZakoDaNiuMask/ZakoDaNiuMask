// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.llm

import org.json.JSONObject

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmMessage(
    /** One of: system, user, assistant, tool. */
    val role: String,
    val content: String = "",
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: JSONObject,
)

data class LlmRequest(
    val system: String,
    val messages: List<LlmMessage>,
    val tools: List<LlmTool>,
)

/**
 * HTTP-level configuration shared by every provider. [apiPath] overrides the
 * provider's default request path, or may be a full URL; [userAgent] and
 * [extraHeaders] are applied to the request.
 */
data class LlmHttpConfig(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val apiPath: String = "",
    val userAgent: String = "",
    val extraHeaders: String = "",
    val thinking: ThinkingLevel = ThinkingLevel.OFF,
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<LlmToolCall>,
    val finishReason: String,
)

/** Provider identifiers persisted in settings. */
enum class LlmProviderType(
    val id: String,
    val label: String,
    val defaultEndpoint: String,
    val openAiCompatible: Boolean,
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", true),
    ANTHROPIC("anthropic", "Anthropic", "https://api.anthropic.com", false),
    GEMINI("gemini", "Gemini", "https://generativelanguage.googleapis.com", false),
    XAI("xai", "xAI", "https://api.x.ai/v1", true),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", true),
    KIMI("kimi", "Moonshot Kimi", "https://api.moonshot.cn/v1", true),
    CUSTOM("custom", "Custom (OpenAI compatible)", "", true);

    companion object {
        fun fromId(id: String): LlmProviderType =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

/** Reasoning effort, applied per provider only when not OFF. */
enum class ThinkingLevel(val id: String) {
    OFF("off"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        fun fromId(id: String): ThinkingLevel =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

interface LlmProvider {
    val type: LlmProviderType

    suspend fun chat(
        config: LlmHttpConfig,
        request: LlmRequest,
        onDelta: (String) -> Unit,
        onThinking: (String) -> Unit = {},
    ): LlmResponse
}
