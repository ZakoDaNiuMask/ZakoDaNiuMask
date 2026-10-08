// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.AppSettingsRepository
import com.zakodaniumask.manager.data.backup.AllowlistBackupSource
import com.zakodaniumask.manager.data.backup.AllowlistDocument
import com.zakodaniumask.manager.data.backup.BackupEngine
import com.zakodaniumask.manager.data.backup.BackupKind
import com.zakodaniumask.manager.data.backup.BackupSourceRegistry
import com.zakodaniumask.manager.data.backup.BackupStorage
import com.zakodaniumask.manager.data.backup.LocalBackupStorage
import com.zakodaniumask.manager.data.backup.WebDavBackupStorage
import com.zakodaniumask.manager.data.backup.toProfile
import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository
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
) : AndroidViewModel(application) {

    data class UiState(
        val busy: Boolean = false,
        val isError: Boolean = false,
        val message: String? = null,
    )

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

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

    fun backup(useWebDav: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val engine = BackupEngine(
                        sources = BackupSourceRegistry(listOf(AllowlistBackupSource(superUserRepository))),
                        storages = listOf(storage(useWebDav)),
                    )
                    engine.backup(BackupKind.ALLOWLIST).isSuccess
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

    fun restore(useWebDav: Boolean) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val store = storage(useWebDav)
                    val latest = store.list("allowlist").getOrThrow()
                        .filter { it.relativePath.endsWith(".json") }
                        .maxByOrNull { it.relativePath }
                        ?: return@runCatching RestoreOutcome(false, getApplication<Application>().getString(R.string.backup_none))
                    val bytes = store.get(latest.relativePath).getOrThrow()
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
                    RestoreOutcome(true, getApplication<Application>().getString(R.string.backup_restored, ok, failed))
                }.getOrElse { error ->
                    RestoreOutcome(false, error.message ?: error.javaClass.simpleName)
                }
            }
            mutableState.update {
                it.copy(busy = false, isError = !message.success, message = message.text)
            }
        }
    }

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
