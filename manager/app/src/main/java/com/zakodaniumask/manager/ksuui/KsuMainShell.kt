package com.zakodaniumask.manager.ksuui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository
import com.zakodaniumask.manager.ksuui.ui.LocalUiMode
import com.zakodaniumask.manager.ksuui.ui.UiMode
import com.zakodaniumask.manager.ksuui.ui.screen.home.HomePager
import com.zakodaniumask.manager.ksuui.ui.screen.module.ModulePager
import com.zakodaniumask.manager.ksuui.ui.screen.settings.SettingPager
import com.zakodaniumask.manager.ksuui.ui.screen.superuser.SuperUserPager
import com.zakodaniumask.manager.ksuui.ui.theme.KernelSUTheme as KsuTheme
import com.zakodaniumask.manager.ksuui.ui.theme.ThemeController
import com.zakodaniumask.manager.ui.activity.PermissionRequestInterface
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.screen.AppProfileScreen
import com.zakodaniumask.manager.ui.screen.SulogScreen
import com.zakodaniumask.manager.ui.util.LocalBlurState
import com.zakodaniumask.manager.ui.util.LocalPermissionRequestInterface
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem as MiuixNavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

private enum class KsuTab {
    Home,
    SuperUser,
    Module,
    Settings,
}

/**
 * Phase 1 shell: KernelSU Home is native, the remaining tabs use the current manager pages until
 * their KernelSU equivalents are ported. This keeps every current feature reachable in KSU mode.
 */
@Composable
fun KsuMainShell(
    mode: ManagerUiMode,
    onExit: () -> Unit,
) {
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings = ThemeController.getAppSettings(settingsRepository)
    val navigator = remember { Navigator(Route.Main) }
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionRequester = rememberPermissionRequester()
    val ksuMode = mode.toKsuUiMode()

    var selectedTab by rememberSaveable { mutableIntStateOf(KsuTab.Home.ordinal) }

    CompositionLocalProvider(
        LocalUiMode provides ksuMode,
        LocalNavigator provides navigator,
        LocalSnackbarHost provides snackbarHostState,
        LocalPermissionRequestInterface provides permissionRequester,
        LocalBlurState provides null,
    ) {
        KsuTheme(
            appSettings = appSettings,
            uiMode = ksuMode,
        ) {
            val topRoute = navigator.backStack.lastOrNull()
            BackHandler(enabled = topRoute != Route.Main) { navigator.pop() }
            when (topRoute) {
                is Route.AppProfile -> AppProfileScreen(topRoute.uid, topRoute.packageName)
                is Route.Sulog -> SulogScreen()
                else -> {
                    val bottomBar: @Composable () -> Unit = {
                        KsuBottomBar(
                            uiMode = ksuMode,
                            selected = selectedTab,
                            onSelect = { selectedTab = it },
                        )
                    }
                    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            when (KsuTab.entries.getOrElse(selectedTab) { KsuTab.Home }) {
                                KsuTab.Home -> HomePager(
                                    navigator = navigator,
                                    bottomInnerPadding = 0.dp,
                                )

                                KsuTab.SuperUser -> SuperUserPager(
                                    navigator = navigator,
                                    bottomInnerPadding = 0.dp,
                                )

                                KsuTab.Module -> ModulePager(bottomInnerPadding = 0.dp)
                                KsuTab.Settings -> SettingPager(
                                    navigator = navigator,
                                    bottomInnerPadding = 0.dp,
                                )
                            }
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                            if (mode.isSuki) {
                                androidx.compose.material3.Text(
                                    text = "SukiSU UI WIP",
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 8.dp, start = 8.dp),
                                )
                            }
                            androidx.compose.material3.TextButton(
                                onClick = onExit,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp),
                            ) {
                                Text("Baka MD3E")
                            }
                        }
                    }

                    if (ksuMode == UiMode.Miuix) {
                        MiuixScaffold(bottomBar = bottomBar, content = content)
                    } else {
                        Scaffold(bottomBar = bottomBar, content = content)
                    }
                }
            }
        }
    }
}

@Composable
private fun KsuBottomBar(
    uiMode: UiMode,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val tabs = listOf(
        KsuTab.Home to R.string.home,
        KsuTab.SuperUser to R.string.superuser,
        KsuTab.Module to R.string.module,
        KsuTab.Settings to R.string.settings,
    )
    val materialIcons = listOf(
        Icons.Filled.Home to Icons.Outlined.Home,
        Icons.Filled.Shield to Icons.Outlined.Shield,
        Icons.Filled.Extension to Icons.Outlined.Extension,
        Icons.Filled.Settings to Icons.Outlined.Settings,
    )

    if (uiMode == UiMode.Miuix) {
        val items = tabs.map { (tab, labelRes) ->
            MiuixNavigationItem(
                label = stringResource(labelRes),
                icon = iconFor(tab),
            )
        }
        MiuixNavigationBar {
            items.forEachIndexed { index, item ->
                MiuixNavigationBarItem(
                    icon = item.icon,
                    label = item.label,
                    selected = selected == index,
                    onClick = { onSelect(index) },
                )
            }
        }
    } else {
        NavigationBar {
            tabs.forEachIndexed { index, (tab, labelRes) ->
                val (selectedIcon, unselectedIcon) = materialIcons[index]
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            imageVector = if (selected == index) selectedIcon else unselectedIcon,
                            contentDescription = stringResource(labelRes),
                        )
                    },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
    }
}

private fun iconFor(tab: KsuTab): ImageVector = when (tab) {
    KsuTab.Home -> Icons.Filled.Home
    KsuTab.SuperUser -> Icons.Filled.Shield
    KsuTab.Module -> Icons.Filled.Extension
    KsuTab.Settings -> Icons.Filled.Settings
}

@Composable
private fun rememberPermissionRequester(): PermissionRequestInterface {
    var pendingCallback by remember {
        mutableStateOf<((Map<String, Boolean>) -> Unit)?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        pendingCallback?.invoke(result)
        pendingCallback = null
    }

    return remember(launcher) {
        object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String,
            ) {
                pendingCallback = { result -> callback(result[permission] == true) }
                launcher.launch(arrayOf(permission))
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>,
            ) {
                pendingCallback = callback
                launcher.launch(permissions)
            }
        }
    }
}
