package com.zakodaniumask.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.screen.detection.DetectorPage
import com.zakodaniumask.manager.ui.screen.main.HomePage
import com.zakodaniumask.manager.ui.screen.main.ModulePage
import com.zakodaniumask.manager.ui.screen.main.SettingsPage
import com.zakodaniumask.manager.ui.screen.main.SuperUserPage

enum class BottomBarDestination(
    val direction: @Composable (bottomPadding: Dp) -> Unit,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(
        { bottomPadding -> HomePage(bottomPadding) },
        R.string.home,
        Icons.TwoTone.Home,
        Icons.TwoTone.Home,
        false
    ),
    Detector(
        { bottomPadding -> DetectorPage(bottomPadding) },
        R.string.detector,
        Icons.TwoTone.Security,
        Icons.TwoTone.Security,
        false
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.TwoTone.AdminPanelSettings,
        Icons.TwoTone.AdminPanelSettings,
        true
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.TwoTone.Extension,
        Icons.TwoTone.Extension,
        true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.TwoTone.Settings,
        Icons.TwoTone.Settings,
        false
    );

    companion object {
        fun getPages(isKsuValid: Boolean): List<BottomBarDestination> {
            return if (isKsuValid) {
                // 全功能管理器
                BottomBarDestination.entries.toList()
            } else {
                BottomBarDestination.entries.filter {
                    !it.rootRequired
                }
            }
        }
    }
}
