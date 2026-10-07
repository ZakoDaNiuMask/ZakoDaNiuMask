// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and edits the kernel-side ksud MCP policy
 * (`/data/adb/ksu/.mcp_policy.json`) through `ksud mcp-policy`.
 */
class AgentMcpPolicyRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    data class McpPolicy(
        val maxTier: String = "read",
        val allow: List<String> = emptyList(),
        val deny: List<String> = emptyList(),
    )

    suspend fun load(): McpPolicy = withContext(Dispatchers.IO) {
        val output = runCatching {
            ShellUtils.fastCmd(
                ksuCliRepository.getRootShell(),
                "${ksuCliRepository.getKsuDaemonPath()} mcp-policy get",
            )
        }.getOrDefault("")
        parse(output)
    }

    suspend fun setMaxTier(tier: String): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("mcp-policy set-max-tier $tier", true)
    }

    suspend fun allow(tool: String): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("mcp-policy allow $tool", true)
    }

    suspend fun deny(tool: String): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("mcp-policy deny $tool", true)
    }

    suspend fun clear(tool: String): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("mcp-policy clear $tool", true)
    }

    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("mcp-policy reset", true)
    }

    private fun parse(output: String): McpPolicy {
        val json = runCatching { JSONObject(output.trim()) }.getOrNull() ?: return McpPolicy()
        fun readList(name: String): List<String> {
            val array = json.optJSONArray(name) ?: JSONArray()
            return buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotEmpty()) add(value)
                }
            }
        }
        return McpPolicy(
            maxTier = json.optString("max_tier", "read"),
            allow = readList("allow"),
            deny = readList("deny"),
        )
    }
}
