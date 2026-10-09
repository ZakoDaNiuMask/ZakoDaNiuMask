// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck-ToolBox (MIT), ui/src/features/device-ids; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.deviceid.DeviceIdProvisionResult
import com.zakodaniumask.manager.data.deviceid.DeviceIdRepository
import com.zakodaniumask.manager.data.deviceid.DeviceIdsProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceIdUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val profile: DeviceIdsProfile = DeviceIdsProfile(),
    val dryRun: Boolean = true,
    val result: DeviceIdProvisionResult? = null,
    val error: String? = null,
)

class DeviceIdViewModel(
    private val repository: DeviceIdRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DeviceIdUiState())
    val state: StateFlow<DeviceIdUiState> = _state.asStateFlow()

    init {
        loadDefaults()
    }

    fun loadDefaults() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val profile = repository.defaults()
            _state.value = _state.value.copy(
                isLoading = false,
                profile = profile ?: _state.value.profile,
            )
        }
    }

    fun updateProfile(transform: (DeviceIdsProfile) -> DeviceIdsProfile) {
        _state.value = _state.value.copy(profile = transform(_state.value.profile), error = null)
    }

    fun setDryRun(value: Boolean) {
        _state.value = _state.value.copy(dryRun = value)
    }

    fun submit() {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, result = null, error = null)
            val current = _state.value
            repository.provision(current.profile, current.dryRun)
                .onSuccess { result ->
                    _state.value = _state.value.copy(isSubmitting = false, result = result)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = error.message ?: "device ID provisioning failed",
                    )
                }
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null, error = null)
    }
}
