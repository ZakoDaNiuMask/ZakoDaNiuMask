// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.kernel.CpuIdentity
import com.zakodaniumask.manager.data.kernel.SpoofRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CpuSpoofUiState(
    val isLoading: Boolean = true,
    val current: CpuIdentity? = null,
    val isApplying: Boolean = false,
)

sealed interface CpuSpoofEvent {
    data class Result(val success: Boolean, val message: String) : CpuSpoofEvent
}

class CpuSpoofViewModel(
    private val spoofRepository: SpoofRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CpuSpoofUiState())
    val state: StateFlow<CpuSpoofUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CpuSpoofEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CpuSpoofEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val identity = spoofRepository.readCurrentCpuIdentity()
            _state.value = CpuSpoofUiState(isLoading = false, current = identity)
        }
    }

    fun apply(cpuIndex: Int, midrHex: String, bogomips: Int, hwcapHex: String, hwcap2Hex: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplying = true)
            val success = spoofRepository.spoofCpu(cpuIndex, midrHex, bogomips, hwcapHex, hwcap2Hex)
            _state.value = _state.value.copy(isApplying = false)
            _events.emit(CpuSpoofEvent.Result(success = success, message = ""))
        }
    }
}
