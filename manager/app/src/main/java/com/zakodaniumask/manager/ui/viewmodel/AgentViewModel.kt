// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.agent.AgentAuditEntry
import com.zakodaniumask.manager.data.agent.AgentMode
import com.zakodaniumask.manager.data.agent.AgentSettings
import com.zakodaniumask.manager.data.agent.AgentSettingsRepository
import com.zakodaniumask.manager.data.agent.AgentToolRouter
import com.zakodaniumask.manager.data.agent.llm.LlmMessage
import com.zakodaniumask.manager.data.agent.llm.LlmTool
import com.zakodaniumask.manager.data.agent.llm.LlmToolCall
import com.zakodaniumask.manager.data.agent.llm.providerFor
import com.zakodaniumask.manager.data.agent.mcp.AgentTool
import com.zakodaniumask.manager.data.agent.mcp.ToolTier
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class AgentToolStatus { PENDING, RUNNING, DONE, ERROR, DENIED }

sealed interface AgentChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : AgentChatItem
    data class Assistant(override val id: Long, val text: String) : AgentChatItem
    data class Info(override val id: Long, val text: String) : AgentChatItem
    data class ToolCall(
        override val id: Long,
        val tool: String,
        val arguments: String,
        val status: AgentToolStatus,
        val result: String = "",
    ) : AgentChatItem
}

data class AgentConfirm(
    val id: Long,
    val tool: String,
    val tier: ToolTier,
    val arguments: String,
)

data class AgentUiState(
    val items: List<AgentChatItem> = emptyList(),
    val isRunning: Boolean = false,
    val isConnected: Boolean = false,
    val connectedSources: List<String> = emptyList(),
    val error: String? = null,
)

private const val DEFAULT_SYSTEM_PROMPT =
    "You are the built-in assistant of ZakoDaNiuMask, a KernelSU-based Android root manager. " +
        "You can inspect and manage this device through MCP tools (status, modules, features, " +
        "superuser profiles, SuSFS, KPM, plugins, boot scripts, spoofs, flashing).\n" +
        "Rules:\n" +
        "- Prefer read-only tools; never run a write or danger tool without being asked.\n" +
        "- Explain what a destructive action will do before proposing it.\n" +
        "- Never invent tool output; only report what tools returned.\n" +
        "- Answer in the user's language."

class AgentViewModel(
    private val router: AgentToolRouter,
    private val settingsRepository: AgentSettingsRepository,
) : ViewModel() {
    private val ids = AtomicLong(0)
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()

    private val mutableConfirm = MutableStateFlow<AgentConfirm?>(null)
    val confirm: StateFlow<AgentConfirm?> = mutableConfirm.asStateFlow()
    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    private val llmMessages = mutableListOf<LlmMessage>()
    private var cachedTools: List<AgentTool> = emptyList()

    fun settings(): AgentSettings = settingsRepository.load()

    fun saveSettings(value: AgentSettings) {
        settingsRepository.save(value)
    }

    fun audit(): List<AgentAuditEntry> = settingsRepository.loadAudit()

    fun clearAudit() = settingsRepository.clearAudit()

    fun clearConversation() {
        llmMessages.clear()
        mutableState.update { it.copy(items = emptyList(), error = null) }
    }

    fun resolveConfirm(approved: Boolean) {
        confirmDeferred?.complete(approved)
        confirmDeferred = null
        mutableConfirm.value = null
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || mutableState.value.isRunning) return
        append(AgentChatItem.User(nextId(), input))
        llmMessages += LlmMessage(role = "user", content = input)

        viewModelScope.launch {
            mutableState.update { it.copy(isRunning = true, error = null) }
            try {
                runLoop()
            } catch (t: Throwable) {
                mutableState.update { it.copy(error = t.message ?: t.javaClass.simpleName) }
            } finally {
                mutableState.update { it.copy(isRunning = false) }
            }
        }
    }

    private suspend fun runLoop() {
        val settings = settingsRepository.load()
        if (settings.model.isBlank()) {
            error("No model configured. Open agent settings to set endpoint, key and model.")
        }
        if (settings.endpoint.isBlank() && settings.provider.id == "openai") {
            error("No endpoint configured for the OpenAI-compatible provider.")
        }

        withContext(Dispatchers.IO) { ensureTools() }
        val provider = providerFor(settings.provider)
        val system = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
        val tools = cachedTools.map { LlmTool(it.name, it.description, it.inputSchema) }

        var iteration = 0
        while (iteration++ < settings.maxIterations) {
            var assistantId: Long? = null
            val streamed = StringBuilder()
            val response = withContext(Dispatchers.IO) {
                provider.chat(
                    endpoint = settings.endpoint,
                    apiKey = settings.apiKey,
                    model = settings.model,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    request = com.zakodaniumask.manager.data.agent.llm.LlmRequest(
                        system = system,
                        messages = llmMessages.toList(),
                        tools = tools,
                    ),
                    onDelta = { chunk ->
                        streamed.append(chunk)
                        val current = assistantId
                        if (current == null) {
                            val id = nextId()
                            assistantId = id
                            append(AgentChatItem.Assistant(id, streamed.toString()))
                        } else {
                            updateAssistantText(current, streamed.toString())
                        }
                    },
                )
            }

            if (response.toolCalls.isEmpty()) {
                if (response.text.isNotBlank()) {
                    if (assistantId == null) {
                        append(AgentChatItem.Assistant(nextId(), response.text))
                    }
                    llmMessages += LlmMessage("assistant", response.text)
                }
                return
            }

            if (response.text.isNotBlank() && assistantId == null) {
                append(AgentChatItem.Assistant(nextId(), response.text))
            }
            llmMessages += LlmMessage("assistant", response.text, response.toolCalls)

            for (call in response.toolCalls) {
                handleToolCall(call)
            }
        }
        append(
            AgentChatItem.Info(
                nextId(),
                "Reached the maximum of ${settings.maxIterations} tool iterations.",
            )
        )
    }

    private suspend fun handleToolCall(call: LlmToolCall) {
        val tool = cachedTools.firstOrNull { it.name == call.name }
        val arguments = runCatching { JSONObject(call.arguments) }.getOrElse { JSONObject() }
        val itemId = nextId()

        if (tool == null) {
            append(
                AgentChatItem.ToolCall(itemId, call.name, call.arguments, AgentToolStatus.ERROR, "unknown tool")
            )
            llmMessages += LlmMessage("tool", "unknown tool: ${call.name}", toolCallId = call.id)
            return
        }

        val approved = decideApproval(tool, arguments)
        if (!approved) {
            append(
                AgentChatItem.ToolCall(
                    itemId,
                    tool.name,
                    arguments.toString(),
                    AgentToolStatus.DENIED,
                    "denied by ${settingsRepository.load().mode.id} mode",
                )
            )
            settingsRepository.appendAudit(
                AgentAuditEntry(
                    System.currentTimeMillis(),
                    tool.source,
                    tool.name,
                    arguments.toString(),
                    approved = false,
                    isError = false,
                    result = "denied",
                )
            )
            llmMessages += LlmMessage(
                "tool",
                "The user/policy denied running ${tool.name}. Do not retry it; explain and continue.",
                toolCallId = call.id,
            )
            return
        }

        append(
            AgentChatItem.ToolCall(itemId, tool.name, arguments.toString(), AgentToolStatus.RUNNING)
        )
        val result = try {
            withContext(Dispatchers.IO) { router.call(tool, arguments) }
        } catch (t: Throwable) {
            com.zakodaniumask.manager.data.agent.mcp.AgentToolResult(
                "tool failed: ${t.message}",
                true,
            )
        }
        updateToolCall(
            itemId,
            if (result.isError) AgentToolStatus.ERROR else AgentToolStatus.DONE,
            result.text,
        )
        settingsRepository.appendAudit(
            AgentAuditEntry(
                System.currentTimeMillis(),
                tool.source,
                tool.name,
                arguments.toString(),
                approved = true,
                isError = result.isError,
                result = result.text,
            )
        )
        llmMessages += LlmMessage("tool", result.text, toolCallId = call.id)
    }

    private suspend fun decideApproval(tool: AgentTool, arguments: JSONObject): Boolean {
        val mode = settingsRepository.load().mode
        return when (tool.tier) {
            ToolTier.READ -> true
            ToolTier.WRITE -> when (mode) {
                AgentMode.READ_ONLY -> false
                AgentMode.WRITE -> awaitConfirm(tool, arguments)
                AgentMode.FULL_AUTO -> true
            }
            ToolTier.DANGER -> when (mode) {
                AgentMode.READ_ONLY -> false
                AgentMode.WRITE, AgentMode.FULL_AUTO -> awaitConfirm(tool, arguments)
            }
        }
    }

    private suspend fun awaitConfirm(tool: AgentTool, arguments: JSONObject): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        mutableConfirm.value = AgentConfirm(nextId(), tool.name, tool.tier, arguments.toString())
        return deferred.await()
    }

    private suspend fun ensureTools() {
        if (cachedTools.isNotEmpty()) return
        val tools = router.listTools()
        if (tools.isEmpty()) {
            error("No tools available. Is the device rooted and ksud installed?")
        }
        cachedTools = tools
        val sources = tools.map { it.source }.distinct()
        mutableState.update { it.copy(isConnected = true, connectedSources = sources) }
    }

    private fun append(item: AgentChatItem) {
        mutableState.update { it.copy(items = it.items + item) }
    }

    private fun updateAssistantText(id: Long, text: String) {
        mutableState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item is AgentChatItem.Assistant && item.id == id) {
                        item.copy(text = text)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun updateToolCall(id: Long, status: AgentToolStatus, result: String) {
        mutableState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item is AgentChatItem.ToolCall && item.id == id) {
                        item.copy(status = status, result = result)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun error(message: String): Nothing = throw RuntimeException(message)

    private fun nextId(): Long = ids.incrementAndGet()
}
