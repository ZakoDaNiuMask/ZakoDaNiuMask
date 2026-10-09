// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from YukiSU (GPL-3.0); modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.superkey.SuperKeyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SuperKeyUiState(
    val configured: Boolean = false,
    val authenticated: Boolean = false,
    val hasSavedKey: Boolean = false,
    val autoAuth: Boolean = false,
    val skipStorage: Boolean = false,
    val patchEnabled: Boolean = false,
    val signatureBypass: Boolean = false,
    val working: Boolean = false,
    val input: String = "",
)

sealed interface SuperKeyEvent {
    data class Result(val success: Boolean, val message: String) : SuperKeyEvent
}

class SuperKeyViewModel(
    private val repository: SuperKeyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SuperKeyUiState())
    val state: StateFlow<SuperKeyUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SuperKeyEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SuperKeyEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(
            configured = repository.isConfigured(),
            authenticated = repository.isAuthenticated(),
            hasSavedKey = repository.hasSavedKey(),
            autoAuth = repository.isAutoAuthenticationEnabled(),
            skipStorage = repository.shouldSkipStorage(),
            patchEnabled = repository.isPatchEnabled(),
            signatureBypass = repository.isSignatureBypass(),
        )
    }

    fun setInput(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun setAutoAuth(enabled: Boolean) {
        repository.setAutoAuthenticationEnabled(enabled)
        _state.value = _state.value.copy(autoAuth = enabled)
    }

    fun setSkipStorage(skip: Boolean) {
        repository.setSkipStorage(skip)
        refresh()
    }

    fun setPatchEnabled(enabled: Boolean) {
        repository.setPatchEnabled(enabled)
        _state.value = _state.value.copy(patchEnabled = enabled)
    }

    fun setSignatureBypass(enabled: Boolean) {
        repository.setSignatureBypass(enabled)
        _state.value = _state.value.copy(signatureBypass = enabled)
    }

    fun authenticate(save: Boolean) {
        val key = _state.value.input
        if (key.isBlank()) {
            viewModelScope.launch { _events.emit(SuperKeyEvent.Result(false, "empty")) }
            return
        }
        if (_state.value.working) return
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            val success = repository.authenticate(key)
            if (success && save) repository.saveKey(key)
            _state.value = _state.value.copy(working = false)
            refresh()
            _events.emit(SuperKeyEvent.Result(success, if (success) "ok" else "failed"))
        }
    }

    fun clear() {
        repository.clearKey()
        _state.value = _state.value.copy(input = "")
        refresh()
    }
}
