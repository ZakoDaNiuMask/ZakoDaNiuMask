// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.trickystore.KeystoreStatus
import com.zakodaniumask.manager.data.trickystore.PackageEntry
import com.zakodaniumask.manager.data.trickystore.SaveRequest
import com.zakodaniumask.manager.data.trickystore.TargetEntry
import com.zakodaniumask.manager.data.trickystore.TargetMode
import com.zakodaniumask.manager.data.trickystore.TrickyStoreRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrickyStoreUiState(
    val loading: Boolean = true,
    val status: KeystoreStatus? = null,
    val packages: List<PackageEntry> = emptyList(),
    val targets: List<TargetEntry> = emptyList(),
    val defaultPolicy: Map<String, String> = emptyMap(),
    val perAppPolicy: Map<String, Map<String, String>> = emptyMap(),
    val systemApps: List<String> = emptyList(),
    val autoAddNewApps: Boolean = false,
    val search: String = "",
    val saving: Boolean = false,
)

sealed interface TrickyStoreEvent {
    data class Saved(val restartRequired: Boolean) : TrickyStoreEvent
    data class Failed(val detail: String) : TrickyStoreEvent
}

class TrickyStoreViewModel(
    private val repository: TrickyStoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrickyStoreUiState())
    val state: StateFlow<TrickyStoreUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrickyStoreEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TrickyStoreEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val status = runCatching { repository.status() }.getOrNull()
            val systemApps = repository.systemApps()
            val targets = status?.config?.targets.orEmpty()
            _state.value = TrickyStoreUiState(
                loading = false,
                status = status,
                packages = repository.listPackages(targets, systemApps),
                targets = targets,
                defaultPolicy = status?.config?.defaultPolicy.orEmpty(),
                perAppPolicy = status?.config?.perAppPolicy.orEmpty(),
                systemApps = systemApps,
                autoAddNewApps = repository.autoAddNewApps(),
            )
        }
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value)
    }

    fun toggleTarget(packageName: String) {
        val targets = _state.value.targets.toMutableList()
        val index = targets.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            targets.removeAt(index)
            val perApp = _state.value.perAppPolicy.toMutableMap().apply { remove(packageName) }
            _state.value = _state.value.copy(targets = targets, perAppPolicy = perApp)
        } else {
            targets += TargetEntry(packageName)
            _state.value = _state.value.copy(targets = targets)
        }
    }

    fun setMode(packageName: String, mode: TargetMode) {
        val targets = _state.value.targets.map {
            if (it.packageName == packageName) it.copy(mode = mode) else it
        }
        _state.value = _state.value.copy(targets = targets)
    }

    fun setPerAppPolicy(packageName: String, key: String, value: String) {
        val perApp = _state.value.perAppPolicy.toMutableMap()
        val policy = perApp.getOrPut(packageName) { linkedMapOf() }.toMutableMap()
        if (value.isBlank()) policy.remove(key) else policy[key] = value
        if (policy.isEmpty()) perApp.remove(packageName) else perApp[packageName] = policy
        _state.value = _state.value.copy(perAppPolicy = perApp)
    }

    fun selectAll(packages: List<String>) {
        val existing = _state.value.targets.associateBy { it.packageName }
        val merged = _state.value.targets.toMutableList()
        packages.forEach { pkg ->
            if (existing[pkg] == null) merged += TargetEntry(pkg)
        }
        _state.value = _state.value.copy(targets = merged)
    }

    fun deselectAll() {
        _state.value = _state.value.copy(targets = emptyList(), perAppPolicy = emptyMap())
    }

    fun setAutoAdd(value: Boolean) {
        _state.value = _state.value.copy(autoAddNewApps = value)
    }

    fun setDefaultPolicy(key: String, value: String) {
        val policy = _state.value.defaultPolicy.toMutableMap()
        if (value.isBlank()) policy.remove(key) else policy[key] = value
        _state.value = _state.value.copy(defaultPolicy = policy)
    }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val current = _state.value
            val request = SaveRequest(
                targets = current.targets,
                defaultPolicy = current.defaultPolicy,
                perAppPolicy = current.perAppPolicy,
                systemApps = current.systemApps,
                autoAddNewApps = current.autoAddNewApps,
            )
            repository.save(request)
                .onSuccess { result ->
                    _state.value = current.copy(saving = false)
                    _events.emit(TrickyStoreEvent.Saved(result.restartRequired))
                }
                .onFailure { error ->
                    _state.value = current.copy(saving = false)
                    _events.emit(TrickyStoreEvent.Failed(error.message ?: "save failed"))
                }
        }
    }
}
