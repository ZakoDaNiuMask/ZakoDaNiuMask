/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * User kernel-module ("kernel driver") manager. Files live under /data/adb/user_ko as <uuid>.ko,
 * metadata (display name, module name, auto-load) is kept in config.json, loading runs through
 * ksud's `insmod` (ksuinit ELF loader) and unloading through rmmod. All file access uses a root
 * shell; ksud reads config.json at boot to auto-load flagged entries.
 */

package com.zakodaniumask.manager.data.userko

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.UserKoModule
import com.zakodaniumask.manager.domain.model.UserKoOperationResult
import com.zakodaniumask.manager.domain.model.UserKoStages
import com.zakodaniumask.manager.domain.model.UserKoState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class UserKoRepository(
    private val context: Context,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun isRootAvailable(): Boolean = ksuCliRepository.rootAvailable()

    suspend fun load(): UserKoState = withContext(Dispatchers.IO) {
        val shell = ksuCliRepository.getRootShell()
        val config = readConfig(shell)
        val loadedNames = loadedModuleNames()
        val stage = config.optString("stage").takeIf { it in UserKoStages.All }
            ?: UserKoStages.POST_FS_DATA
        val entries = config.optJSONArray(KEY_ENTRIES) ?: JSONArray()
        val modules = buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString(KEY_ID)
                if (id.isEmpty()) continue
                val moduleName = entry.optString(KEY_MODULE_NAME)
                add(
                    UserKoModule(
                        id = id,
                        name = entry.optString(KEY_NAME).ifEmpty { id },
                        moduleName = moduleName,
                        autoLoad = entry.optBoolean(KEY_AUTO_LOAD, false),
                        loaded = moduleName.isNotEmpty() && loadedNames.contains(moduleName),
                    ),
                )
            }
        }
        UserKoState(stage = stage, modules = modules, rootAvailable = isRootAvailable())
    }

    /** Imports a picked `.ko`, storing it as <uuid>.ko and recording the modinfo name. */
    suspend fun import(uri: String, displayName: String): UserKoOperationResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(Uri.parse(uri))
                    ?.use { it.readBytes() }
                    ?: return@runCatching UserKoOperationResult(false, "cannot read the selected file")
                val id = UUID.randomUUID().toString()
                val moduleName = ElfModinfo.moduleName(bytes).orEmpty()

                val shell = ksuCliRepository.getRootShell()
                dirOf(shell).mkdirs()
                writeBytes(fileOf(shell, "$DIR/$id.ko"), bytes)

                val config = readConfig(shell)
                val entries = config.optJSONArray(KEY_ENTRIES) ?: JSONArray()
                entries.put(
                    JSONObject()
                        .put(KEY_ID, id)
                        .put(KEY_NAME, displayName.ifBlank { id })
                        .put(KEY_MODULE_NAME, moduleName)
                        .put(KEY_AUTO_LOAD, false),
                )
                config.put(KEY_ENTRIES, entries)
                if (config.optString("stage").isEmpty()) {
                    config.put("stage", UserKoStages.POST_FS_DATA)
                }
                writeConfig(shell, config)
                UserKoOperationResult(true, moduleName)
            }.getOrElse { UserKoOperationResult(false, it.message.orEmpty()) }
        }

    suspend fun loadModule(id: String): UserKoOperationResult = withContext(Dispatchers.IO) {
        runRootCapture("${ksuCliRepository.getKsuDaemonPath()} insmod ${quote("$DIR/$id.ko")}")
    }

    suspend fun unloadModule(id: String): UserKoOperationResult = withContext(Dispatchers.IO) {
        val shell = ksuCliRepository.getRootShell()
        val moduleName = findModuleName(shell, id)
        if (moduleName.isNullOrBlank()) {
            return@withContext UserKoOperationResult(false, "unknown module name")
        }
        runRootCapture("rmmod ${quote(moduleName)}")
    }

    suspend fun delete(id: String): UserKoOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            fileOf(shell, "$DIR/$id.ko").delete()
            val config = readConfig(shell)
            val entries = config.optJSONArray(KEY_ENTRIES) ?: JSONArray()
            val kept = JSONArray()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                if (entry.optString(KEY_ID) != id) kept.put(entry)
            }
            config.put(KEY_ENTRIES, kept)
            writeConfig(shell, config)
            UserKoOperationResult(true, "")
        }.getOrElse { UserKoOperationResult(false, it.message.orEmpty()) }
    }

    suspend fun setAutoLoad(id: String, enabled: Boolean): UserKoOperationResult =
        withContext(Dispatchers.IO) {
            updateEntry(id) { it.put(KEY_AUTO_LOAD, enabled) }
        }

    suspend fun setStage(stage: String): UserKoOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val config = readConfig(shell)
            config.put("stage", stage)
            writeConfig(shell, config)
            UserKoOperationResult(true, "")
        }.getOrElse { UserKoOperationResult(false, it.message.orEmpty()) }
    }

    private fun updateEntry(
        id: String,
        mutate: (JSONObject) -> Unit,
    ): UserKoOperationResult = runCatching {
        val shell = ksuCliRepository.getRootShell()
        val config = readConfig(shell)
        val entries = config.optJSONArray(KEY_ENTRIES) ?: JSONArray()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.optString(KEY_ID) == id) {
                mutate(entry)
                entries.put(index, entry)
                break
            }
        }
        config.put(KEY_ENTRIES, entries)
        writeConfig(shell, config)
        UserKoOperationResult(true, "")
    }.getOrElse { UserKoOperationResult(false, it.message.orEmpty()) }

    private fun findModuleName(shell: Shell, id: String): String? {
        val entries = readConfig(shell).optJSONArray(KEY_ENTRIES) ?: return null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (entry.optString(KEY_ID) == id) {
                return entry.optString(KEY_MODULE_NAME)
            }
        }
        return null
    }

    private fun loadedModuleNames(): Set<String> = runCatching {
        File("/proc/modules").useLines { lines ->
            lines.mapNotNull { it.substringBefore(' ').takeIf(String::isNotBlank) }.toSet()
        }
    }.getOrDefault(emptySet())

    private fun runRootCapture(command: String): UserKoOperationResult {
        val shell = ksuCliRepository.getRootShell()
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val result = runCatching {
            shell.newJob().add(command).to(out, err).exec()
        }.getOrNull() ?: return UserKoOperationResult(false, "root shell unavailable")
        val output = (out + err).joinToString("\n").trim()
        return UserKoOperationResult(result.isSuccess, output.ifEmpty { if (result.isSuccess) "ok" else "failed" })
    }

    private fun readConfig(shell: Shell): JSONObject {
        val raw = readText(fileOf(shell, CONFIG_PATH))
        if (raw.isNullOrBlank()) {
            return JSONObject()
                .put("stage", UserKoStages.POST_FS_DATA)
                .put(KEY_ENTRIES, JSONArray())
        }
        return runCatching { JSONObject(raw) }.getOrElse {
            JSONObject().put("stage", UserKoStages.POST_FS_DATA).put(KEY_ENTRIES, JSONArray())
        }
    }

    private fun writeConfig(shell: Shell, config: JSONObject) {
        dirOf(shell).mkdirs()
        writeText(fileOf(shell, CONFIG_PATH), config.toString())
    }

    private fun dirOf(shell: Shell) = SuFile(DIR).apply { this.shell = shell }

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

    private fun writeBytes(file: SuFile, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        SuFileOutputStream.open(file).use { it.write(bytes) }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    companion object {
        const val DIR = "/data/adb/user_ko"
        const val CONFIG_PATH = "$DIR/config.json"

        private const val KEY_ENTRIES = "entries"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_MODULE_NAME = "module_name"
        private const val KEY_AUTO_LOAD = "auto_load"
    }
}
