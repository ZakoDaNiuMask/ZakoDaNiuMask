package com.zakodaniumask.manager.ksuui.ui.screen.superuser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.zakodaniumask.manager.ksuui.ui.LocalUiMode
import com.zakodaniumask.manager.ksuui.ui.UiMode
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.viewmodel.SuperUserUiAction
import com.zakodaniumask.manager.ui.viewmodel.SuperUserViewModel

@Composable
fun SuperUserPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = koinViewModel<SuperUserViewModel>()
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = rememberKsuSuperUserController(viewModel, currentState, navigator)

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasActivated) {
            hasActivated = true
            viewModel.dispatch(SuperUserUiAction.Refresh)
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> SuperUserPagerMiuix(
            uiState = controller.state,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> SuperUserPagerMaterial(
            uiState = controller.state,
            actions = controller.actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
