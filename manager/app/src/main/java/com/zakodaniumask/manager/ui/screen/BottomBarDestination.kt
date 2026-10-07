package com.zakodaniumask.manager.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.screen.agent.AgentPage
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
    Agent(
        { bottomPadding -> AgentPage(bottomPadding) },
        R.string.agent,
        Icons.TwoTone.AutoAwesome,
        Icons.TwoTone.AutoAwesome,
        false
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

        /**
         * Apply the user's navigation preference on top of the availability filter.
         * [order] holds enum names; unknown entries keep their default relative
         * order at the end. [hidden] names are removed, except Home which is
         * always kept so the bar is never empty. Agent is never force-hidden.
         */
        fun getPages(
            isKsuValid: Boolean,
            order: List<String>,
            hidden: Set<String>,
        ): List<BottomBarDestination> {
            val base = getPages(isKsuValid)
            val visible = base.filter { destination ->
                when (destination) {
                    BottomBarDestination.Home -> true
                    else -> destination.name !in hidden
                }
            }
            val effectiveOrder = if (order.isEmpty()) {
                base.map { it.name }
            } else {
                order
            }
            return visible.sortedBy { destination ->
                val index = effectiveOrder.indexOf(destination.name)
                if (index < 0) Int.MAX_VALUE else index
            }
        }
    }
}
