/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Reads and writes the user-configurable boot script consumed by ksud. The script and its
 * stage config live under the ksud working directory and are only touched through a root shell.
 */

package com.zakodaniumask.manager.data.bootscript

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.BootScriptSettings
import com.zakodaniumask.manager.domain.model.BootScriptStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BootScriptRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    suspend fun load(): BootScriptSettings = withContext(Dispatchers.IO) {
        val shell = ksuCliRepository.getRootShell()
        val content = readText(scriptFile(shell)).orEmpty()
        parseConfig(readText(configFile(shell)), content)
    }

    suspend fun save(settings: BootScriptSettings): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            fileOf(shell, SCRIPT_PATH).let { file ->
                file.parentFile?.mkdirs()
                writeText(file, settings.content)
            }
            writeText(
                fileOf(shell, CONFIG_PATH),
                JSONObject()
                    .put("enabled", settings.enabled)
                    .put("stage", settings.stage.id)
                    .toString(),
            )
            true
        }.getOrDefault(false)
    }

    fun isRootAvailable(): Boolean = ksuCliRepository.rootAvailable()

    private fun parseConfig(config: String?, content: String): BootScriptSettings {
        val base = BootScriptSettings(content = content.ifEmpty { BootScriptSettings.DEFAULT_BOOT_SCRIPT })
        if (config.isNullOrBlank()) return base
        val json = runCatching { JSONObject(config) }.getOrNull() ?: return base
        return base.copy(
            enabled = json.optBoolean("enabled", false),
            stage = BootScriptStage.fromId(json.optString("stage")),
        )
    }

    private fun scriptFile(shell: Shell) = fileOf(shell, SCRIPT_PATH)

    private fun configFile(shell: Shell) = fileOf(shell, CONFIG_PATH)

    private fun fileOf(shell: Shell, path: String) = SuFile(path).apply { this.shell = shell }

    private fun readText(file: SuFile): String? = runCatching {
        if (!file.exists() || !file.isFile) {
            null
        } else {
            SuFileInputStream.open(file).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    private fun writeText(file: SuFile, text: String) {
        SuFileOutputStream.open(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        private const val WORKING_DIR = "/data/adb/ksu"
        const val SCRIPT_PATH = "$WORKING_DIR/boot_script.sh"
        const val CONFIG_PATH = "$WORKING_DIR/boot_script.json"
    }
}
