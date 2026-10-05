package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.domain.model.BootScriptSettings
import com.zakodaniumask.manager.domain.model.BootScriptStage
import com.zakodaniumask.manager.domain.usecase.GetBootScriptUseCase
import com.zakodaniumask.manager.domain.usecase.GetKernelStatusUseCase
import com.zakodaniumask.manager.domain.usecase.SetBootScriptUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BootScriptUiState(
    val isLoading: Boolean = true,
    val isRootAvailable: Boolean = false,
    val enabled: Boolean = false,
    val stage: BootScriptStage = BootScriptStage.POST_FS_DATA,
    val content: String = BootScriptSettings.DEFAULT_BOOT_SCRIPT,
    val isSaving: Boolean = false,
)

sealed interface BootScriptEvent {
    data object Saved : BootScriptEvent
    data object Failed : BootScriptEvent
}

class BootScriptViewModel(
    private val getBootScript: GetBootScriptUseCase,
    private val setBootScript: SetBootScriptUseCase,
    private val getKernelStatus: GetKernelStatusUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(BootScriptUiState())
    val state: StateFlow<BootScriptUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BootScriptEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BootScriptEvent> = _events.asSharedFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val rootAvailable = runCatching { getKernelStatus().isRootAvailable }.getOrDefault(false)
            val settings = runCatching { getBootScript() }.getOrNull() ?: BootScriptSettings()
            _state.value = BootScriptUiState(
                isLoading = false,
                isRootAvailable = rootAvailable,
                enabled = settings.enabled,
                stage = settings.stage,
                content = settings.content,
            )
        }
    }

    fun save(enabled: Boolean, stage: BootScriptStage, content: String) {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true)
        viewModelScope.launch {
            val result = runCatching {
                setBootScript(BootScriptSettings(enabled = enabled, stage = stage, content = content))
            }.getOrDefault(false)
            _state.value = _state.value.copy(isSaving = false)
            _events.emit(if (result) BootScriptEvent.Saved else BootScriptEvent.Failed)
        }
    }
}
