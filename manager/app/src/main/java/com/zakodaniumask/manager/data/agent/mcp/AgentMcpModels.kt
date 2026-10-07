// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.mcp

import org.json.JSONArray
import org.json.JSONObject

/** Capability tier of a tool, mirroring ksud's `.mcp_policy.json` tiers. */
enum class ToolTier(val id: String) {
    READ("read"),
    WRITE("write"),
    DANGER("danger");

    companion object {
        fun fromId(id: String): ToolTier = entries.firstOrNull { it.id == id } ?: READ
    }
}

/** A tool exposed by one of the MCP sources. */
data class AgentTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val tier: ToolTier,
    val source: String,
)

/**
 * The domain a tool belongs to: the part of the name before the first dot.
 * Tools without a dot (e.g. `insmod`, `soft_reboot`) are their own domain.
 */
val AgentTool.domain: String
    get() = if (name.contains('.')) name.substringBefore('.') else name

data class AgentToolResult(
    val text: String,
    val isError: Boolean,
)

/** Whether a tool comes from the in-process manager server or the ksud MCP server. */
fun parseToolsResult(source: String, result: JSONObject): List<AgentTool> {
    val tools = result.optJSONArray("tools") ?: JSONArray()
    return buildList {
        for (i in 0 until tools.length()) {
            val tool = tools.optJSONObject(i) ?: continue
            val name = tool.optString("name")
            if (name.isBlank()) continue
            add(
                AgentTool(
                    name = name,
                    description = tool.optString("description"),
                    inputSchema = tool.optJSONObject("inputSchema") ?: JSONObject(),
                    tier = tierOf(name),
                    source = source,
                )
            )
        }
    }
}

/**
 * The tier a tool runs at. ksud tags tiers server-side; the manager only needs
 * them for the confirmation UI, so a conservative prefix-based mapping is used.
 */
private fun tierOf(name: String): ToolTier = when {
    name.startsWith("flash.") ||
        name == "anykernel3" ||
        name == "ksu.install" ||
        name == "ksu.uninstall" ||
        name == "ksu.unload" ||
        name.startsWith("kernel.nuke") -> ToolTier.DANGER

    name.startsWith("module.") && !name.endsWith(".list") && !name.endsWith(".info") ||
        name.startsWith("feature.set") ||
        name.startsWith("feature.save") ||
        name.startsWith("sepolicy.apply") ||
        name.startsWith("sepolicy.patch") ||
        name.startsWith("profile.set") ||
        name.startsWith("profile.delete") ||
        name.startsWith("umount_config.add") ||
        name.startsWith("umount_config.del") ||
        name.startsWith("umount_config.clear") ||
        name.startsWith("kpm.load") ||
        name.startsWith("kpm.unload") ||
        name.startsWith("kpm.control") ||
        name.startsWith("plugin.enable") ||
        name.startsWith("plugin.disable") ||
        name.startsWith("plugin.run") ||
        name.startsWith("plugin.action") ||
        name.startsWith("kernel.spoof") ||
        name == "insmod" ||
        name == "resetprop.set" ||
        name == "soft_reboot" -> ToolTier.WRITE

    else -> ToolTier.READ
}

fun parseToolResult(result: JSONObject): AgentToolResult {
    val content = result.optJSONArray("content") ?: JSONArray()
    val text = buildString {
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                if (isNotEmpty()) append('\n')
                append(block.optString("text"))
            }
        }
    }
    return AgentToolResult(text = text, isError = result.optBoolean("isError", false))
}
