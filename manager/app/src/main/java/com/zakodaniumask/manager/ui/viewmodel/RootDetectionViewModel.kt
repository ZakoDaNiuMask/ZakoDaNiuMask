package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.detection.RootProbeClientRepository
import com.zakodaniumask.manager.data.detection.SuReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RootDetectionState(
    val report: SuReport? = null,
    val isScanning: Boolean = false,
)

/**
 * Runs the preliminary SU/root probe once at startup (in the background) and keeps the result so
 * the home preview and the detector page share it.
 */
class RootDetectionViewModel(
    private val repository: RootProbeClientRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RootDetectionState())
    val state: StateFlow<RootDetectionState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        if (_state.value.isScanning) return
        _state.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            val report = repository.scan()
            _state.update { it.copy(report = report, isScanning = false) }
        }
    }
}
