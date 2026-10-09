// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/rkp; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.rkp.DiceCurve
import com.zakodaniumask.manager.data.rkp.KeySource
import com.zakodaniumask.manager.data.rkp.KeySourceKind
import com.zakodaniumask.manager.data.rkp.RkpInfoResult
import com.zakodaniumask.manager.data.rkp.RkpKeyboxResult
import com.zakodaniumask.manager.data.rkp.RkpProfile
import com.zakodaniumask.manager.data.rkp.RkpRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RkpUiState(
    val loading: Boolean = true,
    val profile: RkpProfile = RkpProfile(),
    val info: RkpInfoResult? = null,
    val keybox: RkpKeyboxResult? = null,
    val working: Boolean = false,
)

sealed interface RkpEvent {
    data class Failed(val detail: String) : RkpEvent
    data class Message(val key: String) : RkpEvent
}

class RkpViewModel(
    private val repository: RkpRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RkpUiState())
    val state: StateFlow<RkpUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RkpEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RkpEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = RkpUiState(loading = false, profile = repository.profile())
    }

    fun updateProfile(transform: (RkpProfile) -> RkpProfile) {
        _state.value = _state.value.copy(profile = transform(_state.value.profile))
    }

    fun updateKeySource(transform: (KeySource) -> KeySource) =
        updateProfile { it.copy(keySource = transform(it.keySource)) }

    fun setKeySourceKind(kind: KeySourceKind) = updateKeySource { it.copy(kind = kind) }

    fun setCurve(curve: DiceCurve) = updateProfile { it.copy(curve = curve) }

    fun setNumKeys(count: Int) = updateProfile { it.copy(numKeys = count.coerceIn(1, 4)) }

    fun applyDetected() {
        val detected = repository.detect()
        updateProfile { it.copy(device = detected) }
    }

    fun save() {
        repository.saveProfile(_state.value.profile)
        viewModelScope.launch { _events.emit(RkpEvent.Message("saved")) }
    }

    fun clear() {
        repository.clearProfile()
        refresh()
    }

    fun derive() {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            repository.info(_state.value.profile)
                .onSuccess { _state.value = _state.value.copy(working = false, info = it) }
                .onFailure {
                    _state.value = _state.value.copy(working = false)
                    _events.emit(RkpEvent.Failed(it.message ?: "derive failed"))
                }
        }
    }

    fun generateKeybox() {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            repository.saveProfile(_state.value.profile)
            repository.keybox(_state.value.profile)
                .onSuccess { _state.value = _state.value.copy(working = false, keybox = it) }
                .onFailure {
                    _state.value = _state.value.copy(working = false)
                    _events.emit(RkpEvent.Failed(it.message ?: "provisioning failed"))
                }
        }
    }

    fun clearKeybox() {
        _state.value = _state.value.copy(keybox = null)
    }
}
