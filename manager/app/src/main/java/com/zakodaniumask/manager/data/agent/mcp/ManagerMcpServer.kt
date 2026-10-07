// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.mcp

import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.Natives
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-process MCP tool source for capabilities that only exist on the manager
 * side (JNI / Natives), which the Rust ksud MCP server cannot reach. Tools are
 * described with the same MCP shape as the ksud server.
 */
class ManagerMcpServer {

    fun listTools(): List<AgentTool> = TOOLS.map { (name, description, schema) ->
        AgentTool(
            name = name,
            description = description,
            inputSchema = schema,
            tier = ToolTier.READ,
            source = "manager",
        )
    }

    fun callTool(name: String, arguments: JSONObject): AgentToolResult {
        return try {
            when (name) {
                "manager.status" -> AgentToolResult(statusJson().toString(2), false)
                "manager.managers" -> AgentToolResult(managersJson().toString(2), false)
                else -> AgentToolResult("unknown manager tool: $name", true)
            }
        } catch (t: Throwable) {
            AgentToolResult("manager tool '$name' failed: ${t.message}", true)
        }
    }

    private fun statusJson(): JSONObject {
        val features = JSONObject()
            .put("su_compat", runCatching { Natives.isSuEnabled() }.getOrDefault(false))
            .put("kernel_umount", runCatching { Natives.isKernelUmountEnabled() }.getOrDefault(false))
            .put("sulog", runCatching { Natives.isSuLogEnabled() }.getOrDefault(false))
            .put("selinux_hide", runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false))
            .put("mount_hide", runCatching { Natives.isMountHideEnabled() }.getOrDefault(false))
            .put("samsung_compat", runCatching { Natives.isSamsungCompatEnabled() }.getOrDefault(false))
            .put("ptctl", runCatching { Natives.isPtctlEnabled() }.getOrDefault(false))
            .put("uhook", runCatching { Natives.isUhookEnabled() }.getOrDefault(false))
            .put("kpm", runCatching { Natives.isKPMEnabled() }.getOrDefault(false))

        return JSONObject()
            .put("manager_version", BuildConfig.VERSION_NAME)
            .put("manager_version_code", BuildConfig.VERSION_CODE)
            .put("kernel_full_version", runCatching { Natives.getFullVersion() }.getOrDefault(""))
            .put("hook_type", runCatching { Natives.getHookType() }.getOrDefault(""))
            .put(
                "kernel_patch_implement",
                runCatching { Natives.getKernelPatchImplementation().name }.getOrDefault(""),
            )
            .put("is_manager", runCatching { Natives.isManager }.getOrDefault(false))
            .put("superuser_count", runCatching { Natives.getSuperuserCount() }.getOrDefault(0))
            .put("features", features)
    }

    private fun managersJson(): JSONObject {
        val managers = runCatching { Natives.getManagersList() }.getOrNull()
        val array = JSONArray()
        managers?.managers?.forEach { manager ->
            array.put(
                JSONObject()
                    .put("uid", manager.uid)
                    .put("signature_index", manager.signatureIndex)
            )
        }
        return JSONObject().put("managers", array)
    }

    private companion object {
        val TOOLS: List<Triple<String, String, JSONObject>> = listOf(
            Triple(
                "manager.status",
                "Manager-side status: manager/kernel versions, hook type, kernel patch " +
                    "implementation and the live feature states read through JNI.",
                JSONObject().put("type", "object").put("properties", JSONObject()),
            ),
            Triple(
                "manager.managers",
                "List the registered managers (uid + signature index).",
                JSONObject().put("type", "object").put("properties", JSONObject()),
            ),
        )
    }
}
