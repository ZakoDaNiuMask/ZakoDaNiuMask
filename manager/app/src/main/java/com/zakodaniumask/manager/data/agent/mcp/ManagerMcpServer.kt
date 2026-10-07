// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.mcp

import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.agent.ShellExecutor
import com.zakodaniumask.manager.data.detection.DetectorRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-process MCP tool source for capabilities that only exist on the manager
 * side (JNI / Natives, Android detectors) which the Rust ksud MCP server cannot
 * reach. Tools are described with the same MCP shape as the ksud server.
 */
class ManagerMcpServer(
    private val detectorRepository: DetectorRepository,
    private val shellExecutor: ShellExecutor,
) {

    fun listTools(): List<AgentTool> = TOOLS.map { spec ->
        AgentTool(
            name = spec.name,
            description = spec.description,
            inputSchema = spec.schema,
            tier = spec.tier,
            source = "manager",
        )
    }

    suspend fun callTool(name: String, arguments: JSONObject): AgentToolResult {
        return try {
            when (name) {
                "manager.status" -> AgentToolResult(statusJson().toString(2), false)
                "manager.managers" -> AgentToolResult(managersJson().toString(2), false)
                "detector.scan" -> AgentToolResult(detectorRepository.scan(), false)
                "shell.exec" -> {
                    val command = arguments.optString("command")
                    if (command.isBlank()) {
                        AgentToolResult("missing required argument 'command'", true)
                    } else {
                        val timeout = arguments.optLong("timeout_ms", 30_000L)
                            .coerceIn(1_000L, 600_000L)
                        val result = shellExecutor.exec(command, timeout)
                        val text = buildString {
                            append("mode=${result.mode} exit=${result.exitCode}")
                            if (result.stdout.isNotBlank()) {
                                append("\n")
                                append(result.stdout)
                            }
                            if (result.stderr.isNotBlank()) {
                                append("\n[stderr]\n")
                                append(result.stderr)
                            }
                        }
                        AgentToolResult(text, result.exitCode != 0)
                    }
                }

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

    private data class ToolSpec(
        val name: String,
        val description: String,
        val tier: ToolTier,
        val schema: JSONObject,
    )

    private companion object {
        private fun emptySchema(): JSONObject =
            JSONObject().put("type", "object").put("properties", JSONObject())

        val TOOLS: List<ToolSpec> = listOf(
            ToolSpec(
                "manager.status",
                "Manager-side status: manager/kernel versions, hook type, kernel patch " +
                    "implementation and the live feature states read through JNI.",
                ToolTier.READ,
                emptySchema(),
            ),
            ToolSpec(
                "manager.managers",
                "List the registered managers (uid + signature index).",
                ToolTier.READ,
                emptySchema(),
            ),
            ToolSpec(
                "detector.scan",
                "Run the root-detection suite (SU, bootloader, TEE, system properties, " +
                    "kernel checks, SELinux) and return the text report.",
                ToolTier.READ,
                emptySchema(),
            ),
            ToolSpec(
                "shell.exec",
                "Run a shell command. Runs as root when the agent's root-shell option is " +
                    "enabled, otherwise as a uid 1000 shell; with no root it runs directly.",
                ToolTier.WRITE,
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "command",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "shell command to run"),
                            )
                            .put(
                                "timeout_ms",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "timeout in ms (direct mode only)"),
                            ),
                    )
                    .put("required", JSONArray().put("command")),
            ),
        )
    }
}
