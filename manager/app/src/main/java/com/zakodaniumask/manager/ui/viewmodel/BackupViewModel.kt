// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.backup.AllowlistBackupSource
import com.zakodaniumask.manager.data.backup.BootBackupSource
import com.zakodaniumask.manager.data.backup.ModuleBackupSource
import com.zakodaniumask.manager.data.backup.AllowlistDocument
import com.zakodaniumask.manager.data.backup.BackupEngine
import com.zakodaniumask.manager.data.backup.BackupKind
import com.zakodaniumask.manager.data.backup.BackupSourceRegistry
import com.zakodaniumask.manager.data.backup.BackupStorage
import com.zakodaniumask.manager.data.backup.LocalBackupStorage
import com.zakodaniumask.manager.data.backup.WebDavBackupStorage
import com.zakodaniumask.manager.data.backup.toProfile
import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class BackupViewModel(
    application: Application,
    private val settings: AppSettingsRepository,
    private val superUserRepository: SuperUserRepository,
    private val ksuCliRepository: KsuCliRepository,
) : AndroidViewModel(application) {

    data class UiState(
        val busy: Boolean = false,
        val isError: Boolean = false,
        val message: String? = null,
    )

    data class BackupItem(
        val kind: BackupKind,
        val relativePath: String,
        val sizeBytes: Long,
    )

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val mutableHistory = MutableStateFlow<List<BackupItem>>(emptyList())
    val history: StateFlow<List<BackupItem>> = mutableHistory.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun webDavUrl(): String = settings.getString(KEY_URL, "").orEmpty()
    fun webDavUser(): String = settings.getString(KEY_USER, "").orEmpty()
    fun webDavPass(): String = settings.getString(KEY_PASS, "").orEmpty()

    fun saveWebDav(url: String, user: String, pass: String) {
        settings.putString(KEY_URL, url.trim())
        settings.putString(KEY_USER, user.trim())
        settings.putString(KEY_PASS, pass)
    }

    fun backup(kind: BackupKind, useWebDav: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val engine = BackupEngine(
                        sources = sources(),
                        storages = listOf(storage(useWebDav)),
                    )
                    engine.backup(kind).isSuccess
                }.getOrDefault(false)
            }
            mutableState.update {
                it.copy(
                    busy = false,
                    isError = !result,
                    message = getApplication<Application>().getString(
                        if (result) R.string.backup_done else R.string.backup_failed
                    ),
                )
            }
        }
    }

    fun refreshHistory(kind: BackupKind, useWebDav: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    storage(useWebDav).list(prefixFor(kind)).getOrThrow()
                        .filter { it.relativePath.endsWith(suffixFor(kind)) }
                        .sortedByDescending { it.relativePath }
                        .map { BackupItem(kind, it.relativePath, it.sizeBytes) }
                }.getOrElse { emptyList() }
            }
            mutableHistory.value = items
            mutableState.update {
                it.copy(
                    busy = false,
                    isError = items.isEmpty(),
                    message = if (items.isEmpty()) {
                        getApplication<Application>().getString(R.string.backup_none)
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun restore(item: BackupItem, useWebDav: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val store = storage(useWebDav)
                    when (item.kind) {
                        BackupKind.ALLOWLIST -> restoreAllowlist(store, item.relativePath)
                        BackupKind.MODULE -> restoreModule(store, item.relativePath)
                        BackupKind.BOOT -> restoreBoot(store, item.relativePath)
                    }
                }.getOrElse { error ->
                    RestoreOutcome(false, error.message ?: error.javaClass.simpleName)
                }
            }
            mutableState.update {
                it.copy(busy = false, isError = !outcome.success, message = outcome.text)
            }
        }
    }

    private suspend fun restoreAllowlist(
        store: BackupStorage,
        relativePath: String,
    ): RestoreOutcome {
        val bytes = store.get(relativePath).getOrThrow()
        val document = AllowlistDocument.fromJson(String(bytes, Charsets.UTF_8)).getOrThrow()
        var ok = 0
        var failed = 0
        for (entry in document.entries) {
            if (runCatching { Natives.setAppProfile(entry.toProfile()) }.getOrDefault(false)) {
                ok++
            } else {
                failed++
            }
        }
        return RestoreOutcome(
            true,
            getApplication<Application>().getString(R.string.backup_restored, ok, failed),
        )
    }

    private suspend fun restoreModule(
        store: BackupStorage,
        relativePath: String,
    ): RestoreOutcome {
        val bytes = store.get(relativePath).getOrThrow()
        val tmp = File(getApplication<Application>().cacheDir, relativePath.substringAfterLast('/'))
        val ok = try {
            tmp.writeBytes(bytes)
            ksuCliRepository.moduleInstall(tmp.absolutePath)
        } finally {
            tmp.delete()
        }
        return RestoreOutcome(
            ok,
            getApplication<Application>().getString(
                if (ok) R.string.backup_restore_reboot else R.string.backup_failed
            ),
        )
    }

    private suspend fun restoreBoot(
        store: BackupStorage,
        relativePath: String,
    ): RestoreOutcome {
        val partition = relativePath.substringAfterLast('/').substringBefore("-stock-")
        val bytes = store.get(relativePath).getOrThrow()
        val tmp = File(getApplication<Application>().cacheDir, relativePath.substringAfterLast('/'))
        val ok = try {
            tmp.writeBytes(bytes)
            ksuCliRepository.flashImage(tmp.absolutePath, partition)
        } finally {
            tmp.delete()
        }
        return RestoreOutcome(
            ok,
            getApplication<Application>().getString(
                if (ok) R.string.backup_restore_reboot else R.string.backup_failed
            ),
        )
    }

    private fun prefixFor(kind: BackupKind): String = when (kind) {
        BackupKind.ALLOWLIST -> "allowlist"
        BackupKind.MODULE -> "modules"
        BackupKind.BOOT -> "boot"
    }

    private fun suffixFor(kind: BackupKind): String = when (kind) {
        BackupKind.ALLOWLIST -> ".json"
        BackupKind.MODULE -> ".zip"
        BackupKind.BOOT -> ".img"
    }

    private fun sources(): BackupSourceRegistry = BackupSourceRegistry(
        listOf(
            AllowlistBackupSource(superUserRepository),
            ModuleBackupSource(
                listModules = { ksuCliRepository.listModules() },
                rootShell = { ksuCliRepository.getRootShell() },
            ),
            BootBackupSource(getApplication<Application>(), ksuCliRepository),
        )
    )

    private fun storage(useWebDav: Boolean): BackupStorage =
        if (useWebDav) {
            WebDavBackupStorage(httpClient, webDavUrl(), webDavUser(), webDavPass())
        } else {
            LocalBackupStorage(File(getApplication<Application>().filesDir, "ksu_backups"))
        }

    private data class RestoreOutcome(val success: Boolean, val text: String)

    private companion object {
        const val KEY_URL = "backup_webdav_url"
        const val KEY_USER = "backup_webdav_user"
        const val KEY_PASS = "backup_webdav_pass"
    }
}
