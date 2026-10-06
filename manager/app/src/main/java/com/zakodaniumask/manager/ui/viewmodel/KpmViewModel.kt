package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.domain.model.KpmModule
import com.zakodaniumask.manager.domain.usecase.ControlKpmModuleUseCase
import com.zakodaniumask.manager.domain.usecase.GetKpmModulesUseCase
import com.zakodaniumask.manager.domain.usecase.GetKpmStatusUseCase
import com.zakodaniumask.manager.domain.usecase.LoadKpmModuleUseCase
import com.zakodaniumask.manager.domain.usecase.UnloadKpmModuleUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KpmUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val supported: Boolean = false,
    val version: String = "",
    val modules: List<KpmModule> = emptyList(),
)

sealed interface KpmEvent {
    data object Refreshed : KpmEvent
    data object LoadSucceeded : KpmEvent
    data object LoadFailed : KpmEvent
    data object UnloadSucceeded : KpmEvent
    data object UnloadFailed : KpmEvent
    data class ControlResult(val code: Int) : KpmEvent
}

class KpmViewModel(
    private val getKpmStatus: GetKpmStatusUseCase,
    private val getKpmModules: GetKpmModulesUseCase,
    private val loadKpmModule: LoadKpmModuleUseCase,
    private val unloadKpmModule: UnloadKpmModuleUseCase,
    private val controlKpmModule: ControlKpmModuleUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(KpmUiState())
    val state: StateFlow<KpmUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<KpmEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<KpmEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading && _state.value.version.isNotEmpty()) return
        viewModelScope.launch {
            val refreshing = !_state.value.isLoading
            _state.value = _state.value.copy(isRefreshing = refreshing)
            val status = getKpmStatus()
            val modules = if (status.supported) getKpmModules() else emptyList()
            _state.value = KpmUiState(
                isLoading = false,
                isRefreshing = false,
                supported = status.supported,
                version = status.version,
                modules = modules,
            )
            _events.emit(KpmEvent.Refreshed)
        }
    }

    fun loadModule(path: String, args: String?) {
        viewModelScope.launch {
            val ok = loadKpmModule(path, args)
            _events.emit(if (ok) KpmEvent.LoadSucceeded else KpmEvent.LoadFailed)
            if (ok) refresh()
        }
    }

    fun unloadModule(name: String) {
        viewModelScope.launch {
            val ok = unloadKpmModule(name)
            _events.emit(if (ok) KpmEvent.UnloadSucceeded else KpmEvent.UnloadFailed)
            if (ok) refresh()
        }
    }

    fun controlModule(name: String, args: String) {
        viewModelScope.launch {
            val code = controlKpmModule(name, args)
            _events.emit(KpmEvent.ControlResult(code))
        }
    }
}
