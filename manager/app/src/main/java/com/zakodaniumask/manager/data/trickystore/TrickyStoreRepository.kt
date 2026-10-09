// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SaveRequest(
    val targets: List<TargetEntry> = emptyList(),
    val defaultPolicy: Map<String, String> = emptyMap(),
    val perAppPolicy: Map<String, Map<String, String>> = emptyMap(),
    val systemApps: List<String> = emptyList(),
    val autoAddNewApps: Boolean = false,
)

data class SaveResult(
    val targetCount: Int,
    val restartRequired: Boolean,
)

data class PackageEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val selected: Boolean,
    val mode: TargetMode,
)

class TrickyStoreRepository(
    private val context: Context,
    private val ksuCliRepository: KsuCliRepository,
    private val settings: AppSettingsRepository,
) {
    suspend fun status(): KeystoreStatus = withContext(Dispatchers.IO) {
        val shell = ksuCliRepository.getRootShell()
        val backends = detectAll(shell)
        val active = backends.firstOrNull { it.active } ?: backends.firstOrNull()
        val props = readProps(shell)
        if (active == null) {
            return@withContext KeystoreStatus(
                backends = backends,
                active = null,
                schema = null,
                config = ConfigData(),
                configError = null,
                keybox = null,
                props = props,
            )
        }
        val adapter = Backends.forBackend(active.backend)
        val configResult = runCatching { adapter.read(shell) }
        val config = configResult.getOrDefault(ConfigData())
        KeystoreStatus(
            backends = backends,
            active = active,
            schema = adapter.policySchema(),
            config = config,
            configError = configResult.exceptionOrNull()?.message,
            keybox = keyboxStatus(shell, adapter.keyboxPath(shell)),
            props = props,
        )
    }

    suspend fun save(request: SaveRequest): Result<SaveResult> = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val active = (detectAll(shell).firstOrNull { it.active } ?: detectAll(shell).firstOrNull())
                ?: error("no keystore module detected")
            val adapter = Backends.forBackend(active.backend)
            val config = ConfigData(
                targets = request.targets,
                defaultPolicy = request.defaultPolicy,
                perAppPolicy = request.perAppPolicy,
            )
            adapter.write(shell, config).getOrThrow()
            setSystemApps(request.systemApps)
            setAutoAddNewApps(request.autoAddNewApps)
            val restartKeys = adapter.restartKeys()
            val restartRequired = restartKeys.any { request.defaultPolicy.containsKey(it) }
            SaveResult(targetCount = request.targets.size, restartRequired = restartRequired)
        }
    }

    fun listPackages(targets: List<TargetEntry>, systemApps: List<String>): List<PackageEntry> {
        val pm = context.packageManager
        val modes = targets.associate { it.packageName to it.mode }
        val selected = modes.keys
        val tracked = systemApps.toSet()
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .map { info ->
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                PackageEntry(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                    system = isSystem,
                    selected = info.packageName in selected,
                    mode = modes[info.packageName] ?: TargetMode.AUTO,
                )
            }
            .filter { it.system || it.selected || it.packageName in tracked || (pm.getLaunchIntentForPackage(it.packageName) != null) }
            .sortedWith(compareBy({ !it.selected }, { it.label.lowercase() }))
            .toList()
    }

    fun systemApps(): List<String> = settings.getStringSet(KEY_SYSTEM_APPS).toList()

    fun autoAddNewApps(): Boolean = settings.getBoolean(KEY_AUTO_ADD, false)

    fun entryEnabled(): Boolean = settings.getBoolean(KEY_ENTRY_ENABLED, false)

    fun setEntryEnabled(enabled: Boolean) = settings.putBoolean(KEY_ENTRY_ENABLED, enabled)

    fun providers(): List<KeyboxProvider> {
        val raw = settings.getString(KEY_PROVIDERS) ?: return DEFAULT_PROVIDERS
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                KeyboxProvider(item.optString("name"), item.optString("url"), item.optString("decode"))
            }
        }.getOrDefault(DEFAULT_PROVIDERS)
    }

    fun setProviders(providers: List<KeyboxProvider>) {
        val array = JSONArray()
        providers.forEach { provider ->
            array.put(
                JSONObject()
                    .put("name", provider.name)
                    .put("url", provider.url)
                    .put("decode", provider.decode)
            )
        }
        settings.putString(KEY_PROVIDERS, array.toString())
    }

    fun clearProviders() = settings.remove(KEY_PROVIDERS)

    private fun setSystemApps(apps: List<String>) =
        settings.putStringSet(KEY_SYSTEM_APPS, apps.filter { it.isNotBlank() }.toSet())

    private fun setAutoAddNewApps(enabled: Boolean) = settings.putBoolean(KEY_AUTO_ADD, enabled)

    private fun keyboxStatus(shell: Shell, path: String): KeyboxStatus = KeyboxStatus(
        path = path,
        exists = SuFileUtils.isFile(shell, path),
        size = SuFileUtils.size(shell, path),
        modified = SuFileUtils.lastModified(shell, path),
    )

    private fun readProps(shell: Shell): PropStatus {
        val hash = SuFileUtils.readText(shell, TrickystorePaths.BOOT_HASH)
            ?.lines()
            ?.firstOrNull { !it.trim().startsWith("#") }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return PropStatus(
            propHandlerEnabled = !SuFileUtils.exists(shell, TrickystorePaths.PROP_HANDLER_DISABLE),
            bootHash = hash,
        )
    }

    fun detectActive(shell: Shell): BackendDetection? {
        val all = detectAll(shell)
        return all.firstOrNull { it.active } ?: all.firstOrNull()
    }

    private fun detectAll(shell: Shell): List<BackendDetection> = CANDIDATES.mapNotNull { (backend, id) ->
        detectOne(shell, backend, id)
    }

    private fun detectOne(shell: Shell, backend: Backend, id: String): BackendDetection? {
        val dir = "${TrickystorePaths.MODULES_DIR}/$id"
        val propPath = "$dir/module.prop"
        if (!SuFileUtils.isFile(shell, propPath)) return null
        val prop = parseModuleProp(SuFileUtils.readText(shell, propPath).orEmpty())
        val versionCode = prop["versionCode"]?.toLongOrNull()
        val refined = if (backend == Backend.TRICKY_STORE && (versionCode ?: 0) < 246) {
            Backend.TRICKY_STORE_LEGACY
        } else {
            backend
        }
        val active = !SuFileUtils.exists(shell, "$dir/disable") && !SuFileUtils.exists(shell, "$dir/remove")
        return BackendDetection(
            backend = refined,
            moduleId = id,
            moduleDir = dir,
            name = prop["name"],
            version = prop["version"],
            versionCode = versionCode,
            active = active,
        )
    }

    private fun parseModuleProp(raw: String): Map<String, String> = raw.lines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val index = trimmed.indexOf('=')
            if (index <= 0) return@mapNotNull null
            trimmed.substring(0, index).trim() to trimmed.substring(index + 1).trim()
        }
        .toMap()

    private companion object {
        val CANDIDATES = listOf(
            Backend.TRICKY_STORE to "tricky_store",
            Backend.OH_MY_KEYMINT to "oh_my_keymint",
            Backend.TEE_SIMULATOR to "teesim",
        )
        const val KEY_SYSTEM_APPS = "tricky_store_system_apps"
        const val KEY_AUTO_ADD = "tricky_store_auto_add"
        const val KEY_PROVIDERS = "tricky_store_providers"
        const val KEY_ENTRY_ENABLED = "tricky_store_entry_enabled"

        val DEFAULT_PROVIDERS = listOf(
            KeyboxProvider(
                name = "Addon",
                url = "https://raw.githubusercontent.com/KOWX712/Tricky-Addon-Update-Target-List/keybox/.extra",
                decode = "xxd -r -p | base64 -d",
            )
        )
    }
}
