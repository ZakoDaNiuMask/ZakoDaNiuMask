// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.mcp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.agent.ShellExecutor
import com.zakodaniumask.manager.data.agent.browser.BrowserActionResult
import com.zakodaniumask.manager.data.agent.browser.HeadlessBrowser
import com.zakodaniumask.manager.data.webui.WebUiRepository
import com.zakodaniumask.manager.data.agent.web.WebSearchRepository
import com.zakodaniumask.manager.data.detection.DetectorRepository
import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository
import com.zakodaniumask.manager.data.profile.ProfileRepository
import com.zakodaniumask.manager.ui.webui.WebUIActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-process MCP tool source for capabilities that only exist on the manager
 * side (JNI / Natives, Android detectors, root file access, app profiles) which
 * the Rust ksud MCP server cannot reach. Tools are described with the same MCP
 * shape as the ksud server.
 *
 * File and shell tools share the exact same privilege path as `shell.exec`
 * (see [ShellExecutor]): root, uid 1000, or direct depending on availability
 * and the agent's root-shell toggle.
 */
class ManagerMcpServer(
    private val detectorRepository: DetectorRepository,
    private val shellExecutor: ShellExecutor,
    private val profileRepository: ProfileRepository,
    private val superUserRepository: SuperUserRepository,
    private val webSearchRepository: WebSearchRepository,
    private val browser: HeadlessBrowser,
    private val webUiRepository: WebUiRepository,
    private val context: Context,
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
                        AgentToolResult(
                            buildString {
                                append("mode=${result.mode} exit=${result.exitCode}")
                                if (result.stdout.isNotBlank()) {
                                    append("\n")
                                    append(result.stdout)
                                }
                                if (result.stderr.isNotBlank()) {
                                    append("\n[stderr]\n")
                                    append(result.stderr)
                                }
                            },
                            result.exitCode != 0,
                        )
                    }
                }

                // ---- files (same privilege path as shell.exec) ----
                "file.read" -> {
                    val path = arguments.optString("path")
                    if (path.isBlank()) return err("missing required argument 'path'")
                    val max = arguments.optLong("max_bytes", DEFAULT_READ_BYTES)
                        .coerceIn(1L, MAX_READ_BYTES)
                    shell("head -c $max ${shq(path)}")
                }

                "file.list" -> shell("ls -la ${shq(arguments.req("path"))}")
                "file.stat" -> shell("stat ${shq(arguments.req("path"))}")
                "file.mkdir" -> shell("mkdir -p ${shq(arguments.req("path"))}")
                "file.delete" -> shell("rm -rf ${shq(arguments.req("path"))}")
                "file.copy" -> shell(
                    "cp -a ${shq(arguments.req("source"))} ${shq(arguments.req("dest"))}"
                )

                "file.move" -> shell(
                    "mv ${shq(arguments.req("source"))} ${shq(arguments.req("dest"))}"
                )

                "file.chmod" -> shell(
                    "chmod ${arguments.req("mode")} ${shq(arguments.req("path"))}"
                )

                "file.write" -> {
                    val content = arguments.optString("content")
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    if (bytes.size > MAX_WRITE_BYTES) {
                        return err("content too large (${bytes.size} > $MAX_WRITE_BYTES bytes)")
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val redirect = if (arguments.optBoolean("append", false)) ">>" else ">"
                    shell("printf '%s' '$b64' | base64 -d $redirect ${shq(arguments.req("path"))}")
                }

                // ---- superuser / app profiles ----
                "su.list" -> AgentToolResult(suListJson().toString(2), false)
                "su.get" -> {
                    val pkg = arguments.req("package")
                    val uid = arguments.optInt("uid", 0)
                    val profile = profileRepository.getProfile(pkg, uid)
                    AgentToolResult(profileJson(profile, pkg, uid).toString(2), false)
                }

                "su.set" -> {
                    val pkg = arguments.req("package")
                    val uid = arguments.optInt("uid", 0)
                    val base = profileRepository.getProfile(pkg, uid)
                    val template = arguments.optString("template").ifBlank { null }
                    val updated = base.copy(
                        allowSu = if (arguments.has("allow")) {
                            arguments.optBoolean("allow")
                        } else {
                            base.allowSu
                        },
                        umountModules = if (arguments.has("umount_modules")) {
                            arguments.optBoolean("umount_modules")
                        } else {
                            base.umountModules
                        },
                        rootTemplate = template ?: base.rootTemplate,
                        rootUseDefault = template == null && base.rootUseDefault,
                    )
                    val ok = profileRepository.setProfile(updated)
                    AgentToolResult(if (ok) "updated" else "failed to update profile", !ok)
                }

                "su.global" -> {
                    val feature = arguments.req("feature")
                    val enabled = arguments.optBoolean("enabled", true)
                    val ok = setGlobalFeature(feature, enabled)
                    AgentToolResult(
                        if (ok == null) "unknown feature '$feature'" else "$feature=$enabled",
                        ok == null,
                    )
                }

                "browser.open" -> {
                    val url = arguments.req("url")
                    AgentToolResult(browserResult(browser.open(url, null)), false)
                }

                "browser.control" -> {
                    val action = arguments.req("action")
                    AgentToolResult(
                        browserResult(browser.control(action, arguments)),
                        false,
                    )
                }

                "module.webui.list" -> AgentToolResult(moduleWebUiListJson().toString(2), false)

                "module.webui.open" -> {
                    val id = arguments.req("module_id")
                    val intent = Intent(context, WebUIActivity::class.java)
                        .setData(
                            Uri.Builder()
                                .scheme("kernelsu")
                                .authority("webui")
                                .appendQueryParameter("id", id)
                                .build()
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    AgentToolResult("opened module WebUI: $id", false)
                }

                "module.webui.control" -> {
                    val id = arguments.req("module_id")
                    val action = arguments.req("action")
                    AgentToolResult(
                        browserResult(browser.control(action, arguments, id)),
                        false,
                    )
                }

                "web.search" -> {
                    val query = arguments.req("query")
                    val max = arguments.optInt("max_results", 0)
                    val results = webSearchRepository.search(query, max)
                    val array = JSONArray()
                    results.forEach { r ->
                        array.put(
                            JSONObject()
                                .put("title", r.title)
                                .put("url", r.url)
                                .put("snippet", r.snippet)
                        )
                    }
                    AgentToolResult(
                        JSONObject().put("count", results.size).put("results", array).toString(2),
                        false,
                    )
                }

                "web.fetch" -> {
                    val url = arguments.req("url")
                    val maxChars = arguments.optInt("max_chars", 0)
                    AgentToolResult(webSearchRepository.fetch(url, maxChars), false)
                }

                "appprofile.get_sepolicy" -> {
                    val pkg = arguments.req("package")
                    AgentToolResult(profileRepository.getSepolicy(pkg), false)
                }

                "appprofile.set_sepolicy" -> {
                    val pkg = arguments.req("package")
                    val rules = arguments.req("sepolicy")
                    val ok = profileRepository.setSepolicy(pkg, rules)
                    AgentToolResult(if (ok) "applied" else "failed to apply sepolicy", !ok)
                }

                else -> AgentToolResult("unknown manager tool: $name", true)
            }
        } catch (t: Throwable) {
            AgentToolResult("manager tool '$name' failed: ${t.message}", true)
        }
    }

    private fun browserResult(result: BrowserActionResult): String =
        if (result.imageBase64 != null) {
            "${result.text} (image ${result.imageBase64.length} base64 chars)"
        } else {
            result.text
        }

    private suspend fun moduleWebUiListJson(): JSONObject {
        val array = runCatching { JSONArray(webUiRepository.listModules()) }
            .getOrElse { JSONArray() }
        val out = JSONArray()
        for (i in 0 until array.length()) {
            val module = array.optJSONObject(i) ?: continue
            val id = module.optString("id")
            if (id.isBlank()) continue
            val info = runCatching { webUiRepository.getModuleInfo(id) }.getOrNull()
            if (info?.hasWebUi == true) {
                out.put(
                    JSONObject()
                        .put("id", id)
                        .put("name", info.name)
                        .put("enabled", info.enabled)
                )
            }
        }
        return JSONObject().put("count", out.length()).put("modules", out)
    }

    private suspend fun shell(command: String): AgentToolResult {
        val result = shellExecutor.exec(command, 30_000L)
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
        return AgentToolResult(text, result.exitCode != 0)
    }

    private fun err(message: String) = AgentToolResult(message, true)

    private fun JSONObject.req(key: String): String {
        val value = optString(key)
        if (value.isBlank()) throw IllegalArgumentException("missing required argument '$key'")
        return value
    }

    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun profileJson(
        profile: com.zakodaniumask.manager.domain.model.AppProfile,
        pkg: String,
        uid: Int,
    ): JSONObject = JSONObject()
        .put("package", pkg)
        .put("uid", uid)
        .put("allow_su", profile.allowSu)
        .put("root_use_default", profile.rootUseDefault)
        .put("template", profile.rootTemplate.orEmpty())
        .put("umount_modules", profile.umountModules)
        .put("flags", profile.flags)

    private suspend fun suListJson(): JSONObject {
        runCatching { superUserRepository.refresh() }
        val groups = superUserRepository.state.value.groups
        val array = JSONArray()
        groups.forEach { group ->
            array.put(
                JSONObject()
                    .put("uid", group.uid)
                    .put("package", group.primaryPackageName)
                    .put("apps", JSONArray(group.apps.map { it.packageName }))
                    .put("user", group.userName.orEmpty())
                    .put("allow_su", group.profile?.allowSu ?: false)
                    .put("template", group.profile?.rootTemplate.orEmpty())
                    .put("should_umount", group.shouldUmount)
            )
        }
        return JSONObject().put("count", groups.size).put("apps", array)
    }

    private fun setGlobalFeature(feature: String, enabled: Boolean): Boolean? = runCatching {
        when (feature) {
            "su_compat" -> Natives.setSuEnabled(enabled)
            "kernel_umount" -> Natives.setKernelUmountEnabled(enabled)
            "sulog" -> Natives.setSuLogEnabled(enabled)
            "selinux_hide" -> Natives.setSelinuxHideEnabled(enabled) >= 0
            "mount_hide" -> Natives.setMountHideEnabled(enabled) >= 0
            "samsung_compat" -> Natives.setSamsungCompatEnabled(enabled) >= 0
            "ptctl" -> Natives.setPtctlEnabled(enabled) >= 0
            "uhook" -> Natives.setUhookEnabled(enabled) >= 0
            else -> return null
        }
    }.getOrNull()

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
        const val DEFAULT_READ_BYTES = 256L * 1024
        const val MAX_READ_BYTES = 1024L * 1024
        const val MAX_WRITE_BYTES = 256 * 1024

        fun emptySchema(): JSONObject =
            JSONObject().put("type", "object").put("properties", JSONObject())

        fun schema(required: List<String>, vararg props: Pair<String, JSONObject>): JSONObject {
            val properties = JSONObject()
            props.forEach { (name, spec) -> properties.put(name, spec) }
            return JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required))
        }

        fun str(description: String): JSONObject =
            JSONObject().put("type", "string").put("description", description)

        fun int(description: String): JSONObject =
            JSONObject().put("type", "integer").put("description", description)

        fun bool(description: String): JSONObject =
            JSONObject().put("type", "boolean").put("description", description)

        private val PATH = "path" to str("absolute file or directory path")

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
                schema(
                    listOf("command"),
                    "command" to str("shell command to run"),
                    "timeout_ms" to int("timeout in ms (direct mode only)"),
                ),
            ),
            ToolSpec(
                "file.read",
                "Read a file through the same privilege path as shell.exec.",
                ToolTier.READ,
                schema(listOf("path"), PATH, "max_bytes" to int("max bytes to read")),
            ),
            ToolSpec(
                "file.list",
                "List a directory (ls -la) through the same privilege path as shell.exec.",
                ToolTier.READ,
                schema(listOf("path"), PATH),
            ),
            ToolSpec(
                "file.stat",
                "Show file metadata (stat) through the same privilege path as shell.exec.",
                ToolTier.READ,
                schema(listOf("path"), PATH),
            ),
            ToolSpec(
                "file.write",
                "Overwrite (or append to) a file. Content is sent as base64 to avoid quoting.",
                ToolTier.WRITE,
                schema(
                    listOf("path", "content"),
                    PATH,
                    "content" to str("file content"),
                    "append" to bool("append instead of overwrite"),
                ),
            ),
            ToolSpec(
                "file.mkdir",
                "Create a directory (mkdir -p).",
                ToolTier.WRITE,
                schema(listOf("path"), PATH),
            ),
            ToolSpec(
                "file.copy",
                "Copy a file or directory (cp -a).",
                ToolTier.WRITE,
                schema(listOf("source", "dest"), "source" to str("source path"), "dest" to str("destination path")),
            ),
            ToolSpec(
                "file.move",
                "Move or rename a file or directory (mv).",
                ToolTier.WRITE,
                schema(listOf("source", "dest"), "source" to str("source path"), "dest" to str("destination path")),
            ),
            ToolSpec(
                "file.delete",
                "Delete a file or directory recursively (rm -rf).",
                ToolTier.WRITE,
                schema(listOf("path"), PATH),
            ),
            ToolSpec(
                "file.chmod",
                "Change file permissions (chmod).",
                ToolTier.WRITE,
                schema(listOf("path", "mode"), PATH, "mode" to str("octal mode, e.g. 644")),
            ),
            ToolSpec(
                "su.list",
                "List apps with a stored root profile (uid, package, allow_su, template).",
                ToolTier.READ,
                emptySchema(),
            ),
            ToolSpec(
                "su.get",
                "Get the root profile of a package/uid.",
                ToolTier.READ,
                schema(listOf("package"), "package" to str("package name"), "uid" to int("uid")),
            ),
            ToolSpec(
                "su.set",
                "Grant/deny root and update the profile of a package/uid.",
                ToolTier.WRITE,
                schema(
                    listOf("package"),
                    "package" to str("package name"),
                    "uid" to int("uid"),
                    "allow" to bool("allow root"),
                    "template" to str("root profile template id"),
                    "umount_modules" to bool("umount modules for this app"),
                ),
            ),
            ToolSpec(
                "su.global",
                "Toggle a global KernelSU feature (su_compat, kernel_umount, sulog, selinux_hide, " +
                    "mount_hide, samsung_compat, ptctl, uhook).",
                ToolTier.WRITE,
                schema(
                    listOf("feature", "enabled"),
                    "feature" to str("feature id"),
                    "enabled" to bool("enable or disable"),
                ),
            ),
            ToolSpec(
                "web.search",
                "Search the web (DuckDuckGo/SearXNG/Tavily/Brave/Serper). Returns title, url and snippet.",
                ToolTier.READ,
                schema(
                    listOf("query"),
                    "query" to str("search query"),
                    "max_results" to int("max results (optional)"),
                ),
            ),
            ToolSpec(
                "web.fetch",
                "Fetch a web page and return it as text (Jina reader or local parser).",
                ToolTier.READ,
                schema(
                    listOf("url"),
                    "url" to str("http(s) url"),
                    "max_chars" to int("max characters (optional)"),
                ),
            ),
            ToolSpec(
                "browser.open",
                "Open an http(s) URL in the agent's headless browser.",
                ToolTier.READ,
                schema(listOf("url"), "url" to str("http(s) url")),
            ),
            ToolSpec(
                "browser.control",
                "Drive the headless browser: navigate, get_text, get_page_info, execute_js, " +
                    "find_elements, click, type, scroll, screenshot, back, forward, set_user_agent.",
                ToolTier.WRITE,
                schema(
                    listOf("action"),
                    "action" to str("action name"),
                    "url" to str("url (navigate)"),
                    "selector" to str("css selector"),
                    "text" to str("text to type"),
                    "script" to str("javascript (execute_js)"),
                    "direction" to str("scroll direction: up|down"),
                    "amount" to int("scroll amount in px"),
                    "user_agent" to str("user agent string"),
                ),
            ),
            ToolSpec(
                "module.webui.list",
                "List installed modules that ship a Web UI (webroot).",
                ToolTier.READ,
                emptySchema(),
            ),
            ToolSpec(
                "module.webui.open",
                "Open a module's Web UI in the in-app WebUI activity.",
                ToolTier.WRITE,
                schema(listOf("module_id"), "module_id" to str("module id")),
            ),
            ToolSpec(
                "module.webui.control",
                "Drive a module's Web UI in a hidden WebView (the module's ksu.* API works). " +
                    "Actions match browser.control.",
                ToolTier.DANGER,
                schema(
                    listOf("module_id", "action"),
                    "module_id" to str("module id"),
                    "action" to str("action name"),
                    "url" to str("module page path (navigate)"),
                    "selector" to str("css selector"),
                    "text" to str("text to type"),
                    "script" to str("javascript (execute_js)"),
                ),
            ),
            ToolSpec(
                "appprofile.get_sepolicy",
                "Get the root-profile SELinux rules of a package.",
                ToolTier.READ,
                schema(listOf("package"), "package" to str("package name")),
            ),
            ToolSpec(
                "appprofile.set_sepolicy",
                "Set the root-profile SELinux rules of a package.",
                ToolTier.WRITE,
                schema(
                    listOf("package", "sepolicy"),
                    "package" to str("package name"),
                    "sepolicy" to str("sepolicy statements"),
                ),
            ),
        )
    }
}
