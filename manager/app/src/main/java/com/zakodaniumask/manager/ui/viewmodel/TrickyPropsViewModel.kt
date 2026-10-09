// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.trickystore.TrickyPropsRepository
import com.zakodaniumask.manager.data.trickystore.TrickyStoreRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrickyPropsUiState(
    val loading: Boolean = true,
    val propHandlerEnabled: Boolean = true,
    val bootHash: String = "",
    val saving: Boolean = false,
)

sealed interface TrickyPropsEvent {
    data class Saved(val synced: Boolean) : TrickyPropsEvent
    data class Failed(val detail: String) : TrickyPropsEvent
}

class TrickyPropsViewModel(
    private val propsRepository: TrickyPropsRepository,
    private val trickyStoreRepository: TrickyStoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrickyPropsUiState())
    val state: StateFlow<TrickyPropsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrickyPropsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TrickyPropsEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val status = runCatching { trickyStoreRepository.status() }.getOrNull()
            _state.value = TrickyPropsUiState(
                loading = false,
                propHandlerEnabled = status?.props?.propHandlerEnabled ?: true,
                bootHash = status?.props?.bootHash.orEmpty(),
            )
        }
    }

    fun setPropHandlerEnabled(value: Boolean) {
        _state.value = _state.value.copy(propHandlerEnabled = value)
    }

    fun setBootHash(value: String) {
        _state.value = _state.value.copy(bootHash = value.trim())
    }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val current = _state.value
            propsRepository.save(current.propHandlerEnabled, current.bootHash)
                .onSuccess { result ->
                    _state.value = _state.value.copy(saving = false)
                    _events.emit(TrickyPropsEvent.Saved(result.syncedBackendPolicy))
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(saving = false)
                    _events.emit(TrickyPropsEvent.Failed(error.message ?: "save failed"))
                }
        }
    }
}
