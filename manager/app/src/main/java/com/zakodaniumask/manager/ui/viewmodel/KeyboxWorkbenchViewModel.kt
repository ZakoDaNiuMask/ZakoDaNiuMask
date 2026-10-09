// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.trickystore.KeyboxProvider
import com.zakodaniumask.manager.data.trickystore.KeyboxRepository
import com.zakodaniumask.manager.data.trickystore.KeystoreStatus
import com.zakodaniumask.manager.data.trickystore.TrickyStoreRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KeyboxWorkbenchUiState(
    val loading: Boolean = true,
    val status: KeystoreStatus? = null,
    val providers: List<KeyboxProvider> = emptyList(),
    val working: Boolean = false,
)

sealed interface KeyboxEvent {
    data class Installed(val source: String, val path: String, val keys: Int) : KeyboxEvent
    data class Failed(val detail: String) : KeyboxEvent
    data class Exported(val json: String) : KeyboxEvent
}

class KeyboxWorkbenchViewModel(
    private val keyboxRepository: KeyboxRepository,
    private val trickyStoreRepository: TrickyStoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(KeyboxWorkbenchUiState())
    val state: StateFlow<KeyboxWorkbenchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<KeyboxEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<KeyboxEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val status = runCatching { trickyStoreRepository.status() }.getOrNull()
            _state.value = KeyboxWorkbenchUiState(
                loading = false,
                status = status,
                providers = keyboxRepository.providers(),
            )
        }
    }

    fun installAosp() = run { install { keyboxRepository.installAosp() } }

    fun generate() = run { install { keyboxRepository.generate() } }

    fun installLocal(content: String) = run { install { keyboxRepository.installLocal(content) } }

    fun installFromUrl(url: String, decode: String) = run {
        install { keyboxRepository.installFromUrl(url, decode) }
    }

    fun fetchProvider(provider: KeyboxProvider) = run {
        install { keyboxRepository.installFromUrl(provider.url, provider.decode) }
    }

    fun saveProviders(providers: List<KeyboxProvider>) {
        if (keyboxRepository.saveProviders(providers)) {
            _state.value = _state.value.copy(providers = keyboxRepository.providers())
        } else {
            viewModelScope.launch { _events.emit(KeyboxEvent.Failed("invalid provider")) }
        }
    }

    fun resetProviders() {
        keyboxRepository.resetProviders()
        _state.value = _state.value.copy(providers = keyboxRepository.providers())
    }

    fun importProviders(content: String) {
        if (keyboxRepository.importProviders(content)) {
            _state.value = _state.value.copy(providers = keyboxRepository.providers())
        } else {
            viewModelScope.launch { _events.emit(KeyboxEvent.Failed("invalid provider file")) }
        }
    }

    fun exportProviders() {
        viewModelScope.launch { _events.emit(KeyboxEvent.Exported(keyboxRepository.exportProviders())) }
    }

    private fun install(block: suspend () -> Result<com.zakodaniumask.manager.data.trickystore.KeyboxInstall>) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            block()
                .onSuccess { result ->
                    _state.value = _state.value.copy(working = false)
                    _events.emit(KeyboxEvent.Installed(result.source, result.targetPath, result.summary.keyboxes))
                    refresh()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(working = false)
                    _events.emit(KeyboxEvent.Failed(error.message ?: "install failed"))
                }
        }
    }
}
