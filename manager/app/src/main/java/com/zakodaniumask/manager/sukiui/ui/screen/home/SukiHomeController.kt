package com.zakodaniumask.manager.sukiui.ui.screen.home

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.domain.model.HomeDashboardState
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository
import com.zakodaniumask.manager.ksuui.ui.util.module.LatestVersionInfo
import com.zakodaniumask.manager.sukiui.ui.util.HomeCardImageStyle
import com.zakodaniumask.manager.sukiui.ui.util.HomeCardSticker
import com.zakodaniumask.manager.sukiui.ui.util.HomeLayout
import com.zakodaniumask.manager.sukiui.ui.util.HomeStatusAlignment
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.viewmodel.HomeUiAction
import com.zakodaniumask.manager.ui.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SukiHomeMaterialScreen(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    onOpenSuperuser: () -> Unit,
    onOpenModule: () -> Unit,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val dashboard by viewModel.uiState.collectAsStateWithLifecycle()
    val (state, actions) = rememberSukiHomeState(
        dashboard = dashboard,
        navigator = navigator,
        onOpenSuperuser = onOpenSuperuser,
        onOpenModule = onOpenModule,
    )

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeUiAction.AwaitInitialData)
    }

    HomePagerMaterial(
        state = state,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
    )
}

@Composable
private fun rememberSukiHomeState(
    dashboard: HomeDashboardState,
    navigator: Navigator,
    onOpenSuperuser: () -> Unit,
    onOpenModule: () -> Unit,
): Pair<HomeUiState, HomeActions> {
    val uriHandler = LocalUriHandler.current
    val settingsRepository: SettingsRepository = koinInject()
    val status = dashboard.systemStatus
    val info = dashboard.systemInfo
    val update = dashboard.stableManagerUpdate
    val latestVersion = LatestVersionInfo(
        versionCode = update?.versionCode ?: 0,
        downloadUrl = "",
        changelog = update?.changelog ?: "",
    )

    val state = HomeUiState(
        kernelVersion = status.kernelVersion,
        ksuVersion = status.ksuVersion,
        managerUAPIVersion = status.managerUAPIVersion,
        kernelUAPIVersion = status.kernelUAPIVersion,
        lkmMode = status.lkmMode,
        isLkmBundled = runCatching { Natives.isLkmBundled }.getOrDefault(false),
        isManager = status.isManager,
        isManagerPrBuild = BuildConfig.IS_PR_BUILD,
        isKernelPrBuild = status.isPrBuild,
        requiresNewKernel = status.ksuVersion != null &&
            status.managerUAPIVersion > (status.kernelUAPIVersion ?: 0),
        requiresNewManager = status.ksuVersion != null &&
            status.managerUAPIVersion < (status.kernelUAPIVersion ?: 0),
        isRootAvailable = status.isRootAvailable,
        isSafeMode = status.isSafeMode,
        isLateLoadMode = status.isLateLoadMode,
        checkUpdateEnabled = runCatching { settingsRepository.checkUpdate }.getOrDefault(true),
        latestVersionInfo = latestVersion,
        currentManagerVersionCode = BuildConfig.VERSION_CODE.toLong(),
        systemInfo = SystemInfo(
            kernelVersion = info.kernelRelease,
            managerVersion = "${info.managerVersion.first} (${info.managerVersion.second}-${info.managerVersion.third})",
            deviceModel = info.deviceModel,
            kernelFullVersion = status.ksuFullVersion,
            fingerprint = Build.FINGERPRINT,
            selinuxStatus = info.selinuxStatus,
            seccompStatus = info.seccompStatus,
            zygiskImplementation = info.zygiskImplement,
        ),
        showFullStatus = dashboard.showFullStatus,
        superuserCount = info.superuserCount,
        moduleCount = info.moduleCount,
        isPreviewMode = status.ksuVersion == null,
        showAiHomeCard = false,
        customHomeTitle = "",
        homeCardImagePath = "",
        hideHomeCardStatusText = false,
        hideHomeCardDecoration = false,
        homeCardStickers = emptyList<HomeCardSticker>(),
        homeLayout = HomeLayout.CLASSIC,
        homeStatusAlignment = HomeStatusAlignment.START,
        homeStatusScale = 1f,
        homePillScale = 1f,
        homeStatusWidthFraction = 1f,
        homeCardImageStyle = HomeCardImageStyle.BANNER,
        showHomeCardImageWithoutBackground = false,
    )

    val actions = HomeActions(
        onInstallClick = { navigator.push(Route.Install(null)) },
        onOpenUrl = uriHandler::openUri,
        onSuperuserClick = onOpenSuperuser,
        onModuleClick = onOpenModule,
    )

    return remember(state, actions) { state to actions }
}
