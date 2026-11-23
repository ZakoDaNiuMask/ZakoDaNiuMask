package com.zakodaniumask.manager.ksuui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.ksuui.data.repository.SettingsRepository
import com.zakodaniumask.manager.ksuui.ui.LocalUiMode
import com.zakodaniumask.manager.ksuui.ui.screen.home.HomePager
import com.zakodaniumask.manager.ksuui.ui.theme.KernelSUTheme as KsuTheme
import com.zakodaniumask.manager.ksuui.ui.theme.ThemeController
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import org.koin.compose.koinInject

/**
 * Phase 0 host for the ported KernelSU Home screen. The actual tab shell and the remaining
 * screens are added in later phases; this already proves the runtime UI switch.
 */
@Composable
fun KsuHomePreview(
    mode: ManagerUiMode,
    onExit: () -> Unit,
) {
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings = ThemeController.getAppSettings(settingsRepository)
    val navigator = remember { Navigator(Route.Main) }
    val ksuMode = mode.toKsuUiMode()

    CompositionLocalProvider(LocalUiMode provides ksuMode) {
        KsuTheme(
            appSettings = appSettings,
            uiMode = ksuMode,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomePager(
                    navigator = navigator,
                    bottomInnerPadding = 0.dp,
                )
                TextButton(
                    onClick = onExit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                ) {
                    Text("Zako UI")
                }
            }
        }
    }
}
