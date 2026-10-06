/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Plugin management, ported from FolkPatch (https://github.com/LyraVoid/FolkPatch),
 * GPL-3.0. Talks to ksud's `plugin` subcommand through a root shell.
 */

package com.zakodaniumask.manager.data.plugin

import com.topjohnwu.superuser.ShellUtils
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.PluginConfigField
import com.zakodaniumask.manager.domain.model.PluginInfo
import com.zakodaniumask.manager.domain.model.PluginQuickAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PluginRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun list(): Result<List<PluginInfo>> = withContext(Dispatchers.IO) {
        runCatching { parsePlugins(run("plugin list")) }
    }

    suspend fun install(zipPath: String): Boolean = withContext(Dispatchers.IO) {
        runResult("plugin install ${quote(zipPath)}")
    }

    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        runResult("plugin uninstall ${quote(id)}")
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val op = if (enabled) "enable" else "disable"
        runResult("plugin $op ${quote(id)}")
    }

    suspend fun runCallback(id: String, function: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = run("plugin run ${quote(id)} ${quote(function)}")
                true to output
            }.getOrElse { false to (it.message.orEmpty()) }
        }

    suspend fun runAction(id: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = run("plugin action ${quote(id)}")
                true to output
            }.getOrElse { false to (it.message.orEmpty()) }
        }

    suspend fun getConfig(id: String, key: String): String = withContext(Dispatchers.IO) {
        runCatching { run("plugin config --id ${quote(id)} get ${quote(key)}") }.getOrDefault("")
    }

    suspend fun setConfig(id: String, key: String, value: String): Boolean =
        withContext(Dispatchers.IO) {
            runResult("plugin config --id ${quote(id)} set ${quote(key)} ${quote(value)}")
        }

    suspend fun getLog(id: String): String = withContext(Dispatchers.IO) {
        runCatching { run("plugin log ${quote(id)}") }.getOrDefault("")
    }

    suspend fun clearLog(id: String): Boolean = withContext(Dispatchers.IO) {
        runResult("plugin clear-log ${quote(id)}")
    }

    private fun run(args: String): String {
        val shell = ksuCliRepository.getRootShell()
        return ShellUtils.fastCmd(shell, "${ksuCliRepository.getKsuDaemonPath()} $args")
    }

    private fun runResult(args: String): Boolean {
        val shell = ksuCliRepository.getRootShell()
        return ShellUtils.fastCmdResult(shell, "${ksuCliRepository.getKsuDaemonPath()} $args")
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun parsePlugins(raw: String): List<PluginInfo> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[")) {
            return parsePlain(trimmed)
        }
        val array = JSONArray(trimmed)
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").trim()
            if (id.isEmpty()) return@mapNotNull null
            PluginInfo(
                id = id,
                name = obj.optString("name").ifBlank { id },
                author = obj.optString("author"),
                version = obj.optString("version"),
                description = obj.optString("description"),
                descriptions = labelsMap(obj.optJSONObject("descriptions")),
                license = obj.optString("license"),
                enabled = obj.optBoolean("enabled", true),
                hasManifest = obj.optBoolean("has_manifest", false),
                hasAction = obj.optBoolean("has_action", false),
                quickAction = obj.optJSONObject("quick_action")?.let { q ->
                    PluginQuickAction(
                        function = q.optString("function").ifBlank { "action" },
                        label = q.optString("label").ifBlank { q.optString("function") },
                        labels = labelsMap(q.optJSONObject("labels")),
                    )
                },
                config = parseConfig(obj.optJSONArray("config")),
            )
        }
    }

    private fun parseConfig(array: JSONArray?): List<PluginConfigField> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val c = array.optJSONObject(i) ?: return@mapNotNull null
            val optionsArray = c.optJSONArray("options")
            PluginConfigField(
                key = c.optString("key"),
                label = c.optString("label").ifBlank { c.optString("key") },
                labels = labelsMap(c.optJSONObject("labels")),
                type = c.optString("type", "text"),
                default = c.opt("default")?.toString() ?: "",
                options = if (optionsArray == null) {
                    emptyList()
                } else {
                    (0 until optionsArray.length()).map { optionsArray.optString(it) }
                },
            )
        }
    }

    private fun labelsMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { key -> map[key] = obj.optString(key) }
        return map
    }

    private fun parsePlain(raw: String): List<PluginInfo> {
        if (raw.isEmpty()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t', ' ')
                val id = parts.firstOrNull()?.trim().orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                PluginInfo(
                    id = id,
                    name = id,
                    author = "",
                    version = "",
                    description = "",
                    descriptions = emptyMap(),
                    license = "",
                    enabled = parts.getOrNull(1)?.trim() != "disabled",
                    hasManifest = false,
                    hasAction = false,
                    quickAction = null,
                    config = emptyList(),
                )
            }
            .toList()
    }
}
