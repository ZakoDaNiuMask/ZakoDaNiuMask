// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.kernel.SpoofRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemSpoofUiState(
    val isLoading: Boolean = true,
    val currentTotalBytes: Long = 0L,
    val isApplying: Boolean = false,
)

sealed interface MemSpoofEvent {
    data class Result(val success: Boolean) : MemSpoofEvent
}

class MemSpoofViewModel(
    private val spoofRepository: SpoofRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MemSpoofUiState())
    val state: StateFlow<MemSpoofUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MemSpoofEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MemSpoofEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val total = spoofRepository.readCurrentMemTotalBytes()
            _state.value = MemSpoofUiState(isLoading = false, currentTotalBytes = total)
        }
    }

    fun apply(totalRamBytes: Long, cmaBytes: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplying = true)
            val success = spoofRepository.spoofMem(totalRamBytes, cmaBytes)
            _state.value = _state.value.copy(isApplying = false)
            _events.emit(MemSpoofEvent.Result(success = success))
        }
    }
}
