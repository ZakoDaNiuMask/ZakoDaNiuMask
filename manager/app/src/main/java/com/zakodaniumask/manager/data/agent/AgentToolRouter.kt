// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.agent.mcp.AgentTool
import com.zakodaniumask.manager.data.agent.mcp.AgentToolResult
import com.zakodaniumask.manager.data.agent.mcp.KsudMcpClient
import com.zakodaniumask.manager.data.agent.mcp.ManagerMcpServer
import org.json.JSONObject

/**
 * Aggregates the two MCP tool sources: the in-process manager server (JNI-only
 * features) and the ksud MCP server (spawned over stdio).
 */
class AgentToolRouter(
    private val ksudClient: KsudMcpClient,
    private val managerServer: ManagerMcpServer,
) {
    suspend fun listTools(): List<AgentTool> {
        val managerTools = managerServer.listTools()
        val ksudTools = try {
            ksudClient.listTools()
        } catch (t: Throwable) {
            // ksud unavailable (e.g. no root); manager tools still work.
            emptyList()
        }
        return managerTools + ksudTools
    }

    suspend fun call(tool: AgentTool, arguments: JSONObject): AgentToolResult =
        if (tool.source == "manager") {
            managerServer.callTool(tool.name, arguments)
        } else {
            ksudClient.callTool(tool.name, arguments)
        }

    fun close() {
        ksudClient.close()
    }
}
