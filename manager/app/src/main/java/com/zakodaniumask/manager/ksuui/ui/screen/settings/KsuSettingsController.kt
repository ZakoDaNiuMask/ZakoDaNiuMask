package com.zakodaniumask.manager.ksuui.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.ksuui.ManagerUiMode
import com.zakodaniumask.manager.ksuui.ManagerUiModeController
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.viewmodel.SettingsUiAction
import com.zakodaniumask.manager.ui.viewmodel.SettingsUiState as CurrentSettingsUiState
import com.zakodaniumask.manager.ui.viewmodel.SettingsViewModel
import org.koin.compose.koinInject

@Immutable
data class KsuSettingsController(
    val state: SettingsUiState,
    val actions: SettingsScreenActions,
)

@Composable
fun rememberKsuSettingsController(
    viewModel: SettingsViewModel,
    currentState: CurrentSettingsUiState,
    navigator: Navigator,
): KsuSettingsController {
    val managerUiMode: ManagerUiModeController = koinInject()
    val settingsRepository: SettingsRepository = koinInject()

    val state = SettingsUiState(
        uiMode = if (managerUiMode.mode == ManagerUiMode.KSU_MATERIAL) "material" else "miuix",
        checkUpdate = currentState.checkManagerUpdate,
        checkModuleUpdate = currentState.checkModuleUpdate,
        themeMode = currentState.themeMode,
        miuixMonet = settingsRepository.miuixMonet,
        keyColor = settingsRepository.keyColor,
        colorStyle = settingsRepository.colorStyle,
        colorSpec = settingsRepository.colorSpec,
        enablePredictiveBack = true,
        enableSwipeDismiss = currentState.enableSwipeDismiss,
        pagerInterceptionMode = currentState.pagerInterceptionMode,
        enableBlur = settingsRepository.enableBlur,
        enableFloatingBottomBar = settingsRepository.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = settingsRepository.enableFloatingBottomBarBlur,
        enableNavigationBadge = true,
        pageScale = 1f,
        moduleDescriptionMaxLines = currentState.moduleDescriptionMaxLines,
        enableWebDebugging = currentState.enableWebDebugging,
        suCompatStatus = currentState.suStatus,
        suCompatMode = currentState.suCompatMode,
        isSuEnabled = currentState.suCompatMode == 0,
        kernelUmountStatus = currentState.kernelUmountStatus,
        isKernelUmountEnabled = currentState.isKernelUmountEnabled,
        selinuxHideStatus = currentState.selinuxHideStatus,
        isSelinuxHideEnabled = currentState.isSelinuxHideEnabled,
        sulogStatus = currentState.sulogStatus,
        isSulogEnabled = currentState.isSuLogEnabled,
        isDefaultUmountModules = currentState.defaultUmountModules,
        adbRootStatus = currentState.adbRootStatus,
        isAdbRootEnabled = currentState.isAdbRootEnabled,
        shellSuAdbOnlyStatus = "unsupported",
        isShellSuAdbOnlyEnabled = false,
        isLkmMode = runCatching { Natives.isLkmMode }.getOrDefault(false),
        isLateLoadMode = runCatching { Natives.isLateLoadMode }.getOrDefault(false),
        autoJailbreak = currentState.autoJailbreakEnabled,
        useSoftReboot = currentState.useSoftReboot,
    )

    val actions = remember(viewModel, navigator, managerUiMode.mode) {
        SettingsScreenActions(
            onSetCheckUpdate = {
                viewModel.dispatch(SettingsUiAction.SetManagerUpdateCheck(it))
            },
            onSetCheckModuleUpdate = {
                viewModel.dispatch(SettingsUiAction.SetModuleUpdateCheck(it))
            },
            onOpenTheme = { navigator.push(Route.ThemeSettings) },
            onSetUiModeIndex = { index ->
                managerUiMode.set(
                    if (index == 0) ManagerUiMode.KSU_MIUIX else ManagerUiMode.KSU_MATERIAL,
                )
            },
            onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
            onSetSuCompatMode = { viewModel.dispatch(SettingsUiAction.SetSuCompatMode(it)) },
            onSetKernelUmountEnabled = {
                viewModel.dispatch(SettingsUiAction.SetKernelUmount(it))
            },
            onSetSelinuxHideEnabled = {
                viewModel.dispatch(SettingsUiAction.SetSelinuxHide(it))
            },
            onSetSulogEnabled = { viewModel.dispatch(SettingsUiAction.SetSuLog(it)) },
            onSetAdbRootEnabled = { viewModel.dispatch(SettingsUiAction.SetAdbRoot(it)) },
            onSetShellSuAdbOnlyEnabled = {},
            onSetDefaultUmountModules = {
                viewModel.dispatch(SettingsUiAction.SetDefaultUmountModules(it))
            },
            onSetEnableWebDebugging = {
                viewModel.dispatch(SettingsUiAction.SetWebDebugging(it))
            },
            onSetAutoJailbreak = {
                viewModel.dispatch(SettingsUiAction.SetAutoJailbreak(it))
            },
            onSetUseSoftReboot = {
                viewModel.dispatch(SettingsUiAction.SetUseSoftReboot(it))
            },
            onOpenAbout = { navigator.push(Route.About) },
        )
    }

    return remember(state, actions) { KsuSettingsController(state, actions) }
}
