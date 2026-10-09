// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyboxScreen() {
    val navigator = LocalNavigator.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_management_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            }

            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Build,
                            title = stringResource(R.string.keybox_ts_targets_title),
                            description = stringResource(R.string.keybox_ts_targets_summary),
                            onClick = { navigator.push(Route.TrickyTargets) },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Tune,
                            title = stringResource(R.string.keybox_ts_policy_title),
                            description = stringResource(R.string.keybox_ts_policy_summary),
                            onClick = { navigator.push(Route.TrickyPolicy) },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Settings,
                            title = stringResource(R.string.keybox_ts_props_title),
                            description = stringResource(R.string.keybox_ts_props_summary),
                            onClick = { navigator.push(Route.TrickyProps) },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Key,
                            title = stringResource(R.string.keybox_wb_title),
                            description = stringResource(R.string.keybox_wb_summary),
                            onClick = { navigator.push(Route.KeyboxWorkbench) },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Security,
                            title = stringResource(R.string.keybox_rkp_title),
                            description = stringResource(R.string.keybox_rkp_summary),
                            onClick = { navigator.push(Route.KeyboxRkp) },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Badge,
                            title = stringResource(R.string.device_id_title),
                            description = stringResource(R.string.device_id_summary),
                            onClick = { navigator.push(Route.DeviceId) },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 12.dp))
            }
        }
    }
}
