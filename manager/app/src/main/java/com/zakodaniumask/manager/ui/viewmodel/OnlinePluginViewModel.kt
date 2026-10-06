package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.domain.model.OnlinePlugin
import com.zakodaniumask.manager.domain.usecase.GetOnlinePluginsUseCase
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnlinePluginUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val plugins: List<OnlinePlugin> = emptyList(),
    val error: String? = null,
)

class OnlinePluginViewModel(
    private val getOnlinePlugins: GetOnlinePluginsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(OnlinePluginUiState())
    val state: StateFlow<OnlinePluginUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRefreshing = !_state.value.isLoading,
                error = null,
            )
            val language = if (Locale.getDefault().language == "zh") "zh" else "en"
            val result = getOnlinePlugins(language)
            _state.value = OnlinePluginUiState(
                isLoading = false,
                isRefreshing = false,
                plugins = result.getOrElse { emptyList() },
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}
