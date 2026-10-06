/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * KPM (KernelPatch Module) runtime control. Talks to ksud's `kpm` subcommand through a root shell;
 * module management only works when the kernel was built with CONFIG_KPM or patched with kpimg.
 */

package com.zakodaniumask.manager.data.kpm

import com.topjohnwu.superuser.ShellUtils
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.KpmModule
import com.zakodaniumask.manager.domain.model.KpmStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KpmRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun getStatus(): KpmStatus = withContext(Dispatchers.IO) {
        val supported = runCatching { Natives.isKPMEnabled() }.getOrDefault(false)
        val version = if (supported) runCatching { runKpm("version").trim() }.getOrDefault("") else ""
        KpmStatus(supported = supported, version = version)
    }

    suspend fun listModules(): List<KpmModule> = withContext(Dispatchers.IO) {
        val names = runCatching { runKpm("list") }
            .getOrDefault("")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        names.mapNotNull { name ->
            val info = runCatching { runKpm("info ${quote(name)}") }.getOrDefault("")
            parseModule(name, info)
        }
    }

    suspend fun moduleInfo(name: String): String = withContext(Dispatchers.IO) {
        runCatching { runKpm("info ${quote(name)}") }.getOrDefault("")
    }

    suspend fun loadModule(path: String, args: String?): Boolean = withContext(Dispatchers.IO) {
        val suffix = args?.takeIf { it.isNotBlank() }?.let { " ${quote(it)}" }.orEmpty()
        runCatching { runKpm("load ${quote(path)}$suffix") }.isSuccess
    }

    suspend fun unloadModule(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { runKpm("unload ${quote(name)}") }.isSuccess
    }

    suspend fun controlModule(name: String, args: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            runKpm("control ${quote(name)} ${quote(args)}").trim().toIntOrNull() ?: -1
        }.getOrDefault(-1)
    }

    private fun runKpm(args: String): String {
        val shell = ksuCliRepository.getRootShell()
        return ShellUtils.fastCmd(shell, "${ksuCliRepository.getKsuDaemonPath()} kpm $args")
    }

    private fun parseModule(id: String, raw: String): KpmModule {
        val props = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                when {
                    index > 0 -> line.substring(0, index).trim() to line.substring(index + 1).trim()
                    else -> line to ""
                }
            }
            .toMap()
        return KpmModule(
            id = id,
            name = props["name"] ?: id,
            version = props["version"].orEmpty(),
            author = props["author"].orEmpty(),
            description = props["description"].orEmpty(),
            args = props["args"].orEmpty(),
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
