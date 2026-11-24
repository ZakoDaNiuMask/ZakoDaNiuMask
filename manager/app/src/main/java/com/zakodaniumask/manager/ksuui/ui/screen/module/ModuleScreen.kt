package com.zakodaniumask.manager.ksuui.ui.screen.module

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.ksuui.ui.LocalUiMode
import com.zakodaniumask.manager.ksuui.ui.UiMode
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.viewmodel.ModuleUiAction
import com.zakodaniumask.manager.ui.viewmodel.ModuleViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel

@Composable
fun ModulePager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = koinViewModel<ModuleViewModel>()
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val controller = rememberKsuModuleController(viewModel, currentState, navigator)
    val events = remember { emptyFlow<ModuleEffect>() }

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasActivated) {
            hasActivated = true
            viewModel.dispatch(ModuleUiAction.Refresh(manual = false))
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ModulePagerMiuix(
            uiState = controller.state,
            confirmDialogState = controller.state.confirmDialogState,
            moduleEvent = events,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> ModulePagerMaterial(
            uiState = controller.state,
            confirmDialogState = controller.state.confirmDialogState,
            moduleEvent = events,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
