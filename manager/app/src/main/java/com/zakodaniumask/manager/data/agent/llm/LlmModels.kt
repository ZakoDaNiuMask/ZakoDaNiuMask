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

data class LlmResponse(
    val text: String,
    val toolCalls: List<LlmToolCall>,
    val finishReason: String,
)

/** Provider identifiers persisted in settings. */
enum class LlmProviderType(val id: String, val label: String) {
    OPENAI("openai", "OpenAI compatible"),
    ANTHROPIC("anthropic", "Anthropic");

    companion object {
        fun fromId(id: String): LlmProviderType =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

interface LlmProvider {
    val type: LlmProviderType

    suspend fun chat(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Double,
        maxTokens: Int,
        request: LlmRequest,
    ): LlmResponse
}
