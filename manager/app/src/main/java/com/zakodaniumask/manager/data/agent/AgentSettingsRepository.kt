// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import org.json.JSONArray
import org.json.JSONObject

/** How much the agent is allowed to do without asking. */
enum class AgentMode(val id: String) {
    /** Only read-only tools run; write/danger calls are refused. */
    READ_ONLY("read_only"),

    /** Read tools run automatically; write tools ask first; danger is refused. */
    WRITE("write"),

    /** Read + write run automatically; danger tools ask first. */
    FULL_AUTO("full_auto");

    companion object {
        fun fromId(id: String): AgentMode = entries.firstOrNull { it.id == id } ?: READ_ONLY
    }
}

data class AgentSettings(
    val provider: LlmProviderType = LlmProviderType.OPENAI,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val maxIterations: Int = 8,
    val mode: AgentMode = AgentMode.READ_ONLY,
    val systemPrompt: String = "",
)

data class AgentAuditEntry(
    val timestamp: Long,
    val source: String,
    val tool: String,
    val arguments: String,
    val approved: Boolean,
    val isError: Boolean,
    val result: String,
)

class AgentSettingsRepository(
    private val settings: AppSettingsRepository,
) {
    fun load(): AgentSettings = AgentSettings(
        provider = LlmProviderType.fromId(settings.getString("agent_provider", "openai")),
        endpoint = settings.getString("agent_endpoint", ""),
        apiKey = settings.getString("agent_api_key", ""),
        model = settings.getString("agent_model", ""),
        temperature = settings.getFloat("agent_temperature", 0.3f).toDouble(),
        maxTokens = settings.getInt("agent_max_tokens", 2048),
        maxIterations = settings.getInt("agent_max_iterations", 8),
        mode = AgentMode.fromId(settings.getString("agent_mode", "read_only")),
        systemPrompt = settings.getString("agent_system_prompt", ""),
    )

    fun save(value: AgentSettings) {
        settings.putString("agent_provider", value.provider.id)
        settings.putString("agent_endpoint", value.endpoint)
        settings.putString("agent_api_key", value.apiKey)
        settings.putString("agent_model", value.model)
        settings.putFloat("agent_temperature", value.temperature.toFloat())
        settings.putInt("agent_max_tokens", value.maxTokens)
        settings.putInt("agent_max_iterations", value.maxIterations)
        settings.putString("agent_mode", value.mode.id)
        settings.putString("agent_system_prompt", value.systemPrompt)
    }

    fun loadAudit(): List<AgentAuditEntry> {
        val raw = settings.getString("agent_audit", "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    add(
                        AgentAuditEntry(
                            timestamp = entry.optLong("ts"),
                            source = entry.optString("source"),
                            tool = entry.optString("tool"),
                            arguments = entry.optString("args"),
                            approved = entry.optBoolean("approved"),
                            isError = entry.optBoolean("error"),
                            result = entry.optString("result"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun appendAudit(entry: AgentAuditEntry) {
        val current = loadAudit()
        val updated = (listOf(entry) + current).take(200)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("ts", item.timestamp)
                    .put("source", item.source)
                    .put("tool", item.tool)
                    .put("args", item.arguments)
                    .put("approved", item.approved)
                    .put("error", item.isError)
                    .put("result", item.result.take(4000))
            )
        }
        settings.putString("agent_audit", array.toString())
    }

    fun clearAudit() {
        settings.putString("agent_audit", "")
    }
}
