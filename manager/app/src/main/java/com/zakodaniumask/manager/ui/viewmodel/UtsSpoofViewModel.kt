// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.kernel.SpoofRepository
import com.zakodaniumask.manager.data.kernel.UnameIdentity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UtsSpoofUiState(
    val isLoading: Boolean = true,
    val current: UnameIdentity = UnameIdentity("", ""),
    val isApplying: Boolean = false,
)

sealed interface UtsSpoofEvent {
    data class Result(val success: Boolean) : UtsSpoofEvent
}

class UtsSpoofViewModel(
    private val spoofRepository: SpoofRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UtsSpoofUiState())
    val state: StateFlow<UtsSpoofUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UtsSpoofEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UtsSpoofEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val identity = spoofRepository.readCurrentUname()
            _state.value = UtsSpoofUiState(isLoading = false, current = identity)
        }
    }

    fun apply(release: String, version: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplying = true)
            val success = spoofRepository.spoofKernelUname(release, version)
            _state.value = _state.value.copy(isApplying = false)
            _events.emit(UtsSpoofEvent.Result(success = success))
        }
    }
}
