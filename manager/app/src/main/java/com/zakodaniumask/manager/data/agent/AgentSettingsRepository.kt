// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.data.agent.web.WebFetchReader
import com.zakodaniumask.manager.data.agent.web.WebSearchBackend
import com.zakodaniumask.manager.data.agent.llm.ThinkingLevel
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
    val apiPath: String = "",
    val userAgent: String = "",
    val extraHeaders: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val maxIterations: Int = 8,
    val mode: AgentMode = AgentMode.READ_ONLY,
    val systemPrompt: String = "",
    /** Tool domains the agent must not use (empty = every domain). */
    val disabledDomains: Set<String> = emptySet(),
    /** Run shell tools as root; when false they run as uid 1000. */
    val allowRootShell: Boolean = false,
    /** Reasoning effort, applied per provider only when not OFF. */
    val thinking: ThinkingLevel = ThinkingLevel.OFF,
    /** Web search / fetch configuration. */
    val webSearchEnabled: Boolean = false,
    val webSearchBackend: WebSearchBackend = WebSearchBackend.DUCKDUCKGO,
    val webSearchEndpoint: String = "",
    val webSearchApiKey: String = "",
    val webSearchMaxResults: Int = 5,
    val webFetchReader: WebFetchReader = WebFetchReader.AUTO,
    val webFetchApiKey: String = "",
    val webFetchAllowLocal: Boolean = false,
    /** Headless browser automation (general web). */
    val browserEnabled: Boolean = false,
    val browserHeadlessGpu: Boolean = false,
    /** Module WebUI control in a hidden WebView. */
    val webUiEnabled: Boolean = false,
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
        provider = LlmProviderType.fromId(settings.getString("agent_provider", "openai").orEmpty()),
        endpoint = settings.getString("agent_endpoint", "").orEmpty(),
        apiKey = settings.getString("agent_api_key", "").orEmpty(),
        model = settings.getString("agent_model", "").orEmpty(),
        apiPath = settings.getString("agent_api_path", "").orEmpty(),
        userAgent = settings.getString("agent_user_agent", "").orEmpty(),
        extraHeaders = settings.getString("agent_extra_headers", "").orEmpty(),
        temperature = settings.getFloat("agent_temperature", 0.3f).toDouble(),
        maxTokens = settings.getInt("agent_max_tokens", 2048),
        maxIterations = settings.getInt("agent_max_iterations", 8),
        mode = AgentMode.fromId(settings.getString("agent_mode", "read_only").orEmpty()),
        systemPrompt = settings.getString("agent_system_prompt", "").orEmpty(),
        disabledDomains = settings.getStringSet("agent_disabled_domains", emptySet()),
        allowRootShell = settings.getBoolean("agent_allow_root_shell", false),
        thinking = ThinkingLevel.fromId(settings.getString("agent_thinking", "off").orEmpty()),
        webSearchEnabled = settings.getBoolean("agent_web_enabled", false),
        webSearchBackend = WebSearchBackend.fromId(
            settings.getString("agent_web_backend", "duckduckgo").orEmpty()
        ),
        webSearchEndpoint = settings.getString("agent_web_endpoint", "").orEmpty(),
        webSearchApiKey = settings.getString("agent_web_api_key", "").orEmpty(),
        webSearchMaxResults = settings.getInt("agent_web_max_results", 5),
        webFetchReader = WebFetchReader.fromId(
            settings.getString("agent_web_fetch_reader", "auto").orEmpty()
        ),
        webFetchApiKey = settings.getString("agent_web_fetch_api_key", "").orEmpty(),
        webFetchAllowLocal = settings.getBoolean("agent_web_fetch_allow_local", false),
        browserEnabled = settings.getBoolean("agent_browser_enabled", false),
        browserHeadlessGpu = settings.getBoolean("agent_browser_headless_gpu", false),
        webUiEnabled = settings.getBoolean("agent_webui_enabled", false),
    )

    fun save(value: AgentSettings) {
        settings.putString("agent_provider", value.provider.id)
        settings.putString("agent_endpoint", value.endpoint)
        settings.putString("agent_api_key", value.apiKey)
        settings.putString("agent_model", value.model)
        settings.putString("agent_api_path", value.apiPath)
        settings.putString("agent_user_agent", value.userAgent)
        settings.putString("agent_extra_headers", value.extraHeaders)
        settings.putFloat("agent_temperature", value.temperature.toFloat())
        settings.putInt("agent_max_tokens", value.maxTokens)
        settings.putInt("agent_max_iterations", value.maxIterations)
        settings.putString("agent_mode", value.mode.id)
        settings.putString("agent_system_prompt", value.systemPrompt)
        settings.putStringSet("agent_disabled_domains", value.disabledDomains)
        settings.putBoolean("agent_allow_root_shell", value.allowRootShell)
        settings.putString("agent_thinking", value.thinking.id)
        settings.putBoolean("agent_web_enabled", value.webSearchEnabled)
        settings.putString("agent_web_backend", value.webSearchBackend.id)
        settings.putString("agent_web_endpoint", value.webSearchEndpoint)
        settings.putString("agent_web_api_key", value.webSearchApiKey)
        settings.putInt("agent_web_max_results", value.webSearchMaxResults)
        settings.putString("agent_web_fetch_reader", value.webFetchReader.id)
        settings.putString("agent_web_fetch_api_key", value.webFetchApiKey)
        settings.putBoolean("agent_web_fetch_allow_local", value.webFetchAllowLocal)
        settings.putBoolean("agent_browser_enabled", value.browserEnabled)
        settings.putBoolean("agent_browser_headless_gpu", value.browserHeadlessGpu)
        settings.putBoolean("agent_webui_enabled", value.webUiEnabled)
    }

    fun loadAudit(): List<AgentAuditEntry> {
        val raw = settings.getString("agent_audit", "").orEmpty()
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
