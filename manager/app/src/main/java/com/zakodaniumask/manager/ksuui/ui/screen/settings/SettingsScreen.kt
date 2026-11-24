package com.zakodaniumask.manager.ksuui.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.ksuui.ui.LocalUiMode
import com.zakodaniumask.manager.ksuui.ui.UiMode
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.viewmodel.SettingsUiAction
import com.zakodaniumask.manager.ui.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = rememberKsuSettingsController(viewModel, currentState, navigator)

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.dispatch(SettingsUiAction.Initialize)
            viewModel.dispatch(SettingsUiAction.LoadFeatureSettings)
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(
            uiState = controller.state,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> SettingPagerMaterial(
            uiState = controller.state,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
