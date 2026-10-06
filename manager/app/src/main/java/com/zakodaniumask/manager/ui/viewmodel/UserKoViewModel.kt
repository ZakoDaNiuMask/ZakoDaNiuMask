// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.domain.model.UserKoModule
import com.zakodaniumask.manager.domain.model.UserKoStages
import com.zakodaniumask.manager.domain.usecase.DeleteUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.GetUserKoStateUseCase
import com.zakodaniumask.manager.domain.usecase.ImportUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.LoadUserKoUseCase
import com.zakodaniumask.manager.domain.usecase.SetUserKoAutoLoadUseCase
import com.zakodaniumask.manager.domain.usecase.SetUserKoStageUseCase
import com.zakodaniumask.manager.domain.usecase.UnloadUserKoUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserKoUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val rootAvailable: Boolean = true,
    val stage: String = UserKoStages.POST_FS_DATA,
    val modules: List<UserKoModule> = emptyList(),
)

sealed interface UserKoEvent {
    /** [message] is the raw ksud/rmmod output to surface in a snackbar. */
    data class Result(val success: Boolean, val message: String) : UserKoEvent
}

class UserKoViewModel(
    private val getUserKoState: GetUserKoStateUseCase,
    private val importUserKo: ImportUserKoUseCase,
    private val loadUserKo: LoadUserKoUseCase,
    private val unloadUserKo: UnloadUserKoUseCase,
    private val deleteUserKo: DeleteUserKoUseCase,
    private val setUserKoAutoLoad: SetUserKoAutoLoadUseCase,
    private val setUserKoStage: SetUserKoStageUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(UserKoUiState())
    val state: StateFlow<UserKoUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UserKoEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UserKoEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val refreshing = !_state.value.isLoading
            _state.value = _state.value.copy(isRefreshing = refreshing)
            val snapshot = getUserKoState()
            _state.value = UserKoUiState(
                isLoading = false,
                isRefreshing = false,
                rootAvailable = snapshot.rootAvailable,
                stage = snapshot.stage,
                modules = snapshot.modules,
            )
        }
    }

    fun import(uri: String, displayName: String) {
        viewModelScope.launch {
            val result = importUserKo(uri, displayName)
            _events.emit(UserKoEvent.Result(result.success, result.output))
            refresh()
        }
    }

    fun load(id: String) {
        viewModelScope.launch {
            val result = loadUserKo(id)
            _events.emit(UserKoEvent.Result(result.success, result.output))
            refresh()
        }
    }

    fun unload(id: String) {
        viewModelScope.launch {
            val result = unloadUserKo(id)
            _events.emit(UserKoEvent.Result(result.success, result.output))
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val result = deleteUserKo(id)
            _events.emit(UserKoEvent.Result(result.success, result.output))
            refresh()
        }
    }

    fun setAutoLoad(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = setUserKoAutoLoad(id, enabled)
            if (!result.success) _events.emit(UserKoEvent.Result(false, result.output))
            refresh()
        }
    }

    fun setStage(stage: String) {
        viewModelScope.launch {
            val result = setUserKoStage(stage)
            if (!result.success) _events.emit(UserKoEvent.Result(false, result.output))
            refresh()
        }
    }
}
