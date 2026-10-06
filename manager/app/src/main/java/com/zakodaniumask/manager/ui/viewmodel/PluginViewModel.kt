package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.domain.model.PluginInfo
import com.zakodaniumask.manager.domain.usecase.GetPluginsUseCase
import com.zakodaniumask.manager.domain.usecase.InstallPluginUseCase
import com.zakodaniumask.manager.domain.usecase.RunPluginActionUseCase
import com.zakodaniumask.manager.domain.usecase.RunPluginCallbackUseCase
import com.zakodaniumask.manager.domain.usecase.SetPluginEnabledUseCase
import com.zakodaniumask.manager.domain.usecase.UninstallPluginUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PluginUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val plugins: List<PluginInfo> = emptyList(),
    val error: String? = null,
)

sealed interface PluginEvent {
    data object Installed : PluginEvent
    data object InstallFailed : PluginEvent
    data object Uninstalled : PluginEvent
    data object UninstallFailed : PluginEvent
    data object ToggleFailed : PluginEvent
    data class ActionResult(val ok: Boolean) : PluginEvent
}

class PluginViewModel(
    private val getPlugins: GetPluginsUseCase,
    private val installPlugin: InstallPluginUseCase,
    private val uninstallPlugin: UninstallPluginUseCase,
    private val setPluginEnabled: SetPluginEnabledUseCase,
    private val runPluginCallback: RunPluginCallbackUseCase,
    private val runPluginAction: RunPluginActionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(PluginUiState())
    val state: StateFlow<PluginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PluginEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PluginEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRefreshing = !_state.value.isLoading,
                error = null,
            )
            val result = getPlugins()
            _state.value = PluginUiState(
                isLoading = false,
                isRefreshing = false,
                plugins = result.getOrElse { emptyList() },
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val ok = setPluginEnabled(id, enabled)
            if (ok) {
                _state.value = _state.value.copy(
                    plugins = _state.value.plugins.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    },
                )
            } else {
                _events.emit(PluginEvent.ToggleFailed)
            }
        }
    }

    fun install(zipPath: String) {
        viewModelScope.launch {
            val ok = installPlugin(zipPath)
            _events.emit(if (ok) PluginEvent.Installed else PluginEvent.InstallFailed)
            if (ok) refresh()
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch {
            val ok = uninstallPlugin(id)
            _events.emit(if (ok) PluginEvent.Uninstalled else PluginEvent.UninstallFailed)
            if (ok) refresh()
        }
    }

    fun runAction(id: String) {
        viewModelScope.launch {
            val (ok, _) = runPluginAction(id)
            _events.emit(PluginEvent.ActionResult(ok))
        }
    }

    fun runCallback(id: String, function: String) {
        viewModelScope.launch {
            val (ok, _) = runPluginCallback(id, function)
            _events.emit(PluginEvent.ActionResult(ok))
        }
    }
}
