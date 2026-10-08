// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.settings

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.applock.AppLockRepository
import com.zakodaniumask.manager.data.applock.AppLockTimeout
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLockSettingsScreen() {
    val repository: AppLockRepository = koinInject()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val deviceSecure = remember {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
    var settings by remember { mutableStateOf(repository.state.value) }

    val timeoutLabels = listOf(
        stringResource(R.string.app_lock_timeout_immediate),
        stringResource(R.string.app_lock_timeout_30s),
        stringResource(R.string.app_lock_timeout_1m),
        stringResource(R.string.app_lock_timeout_5m),
        stringResource(R.string.app_lock_timeout_never),
    )

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
                title = { Text(stringResource(R.string.app_lock_title)) },
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
            item { Spacer(Modifier.height(paddingValues.calculateTopPadding())) }

            item {
                SegmentedColumn(title = stringResource(R.string.app_lock_title)) {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Lock,
                            title = stringResource(R.string.app_lock_enable),
                            description = stringResource(R.string.app_lock_enable_summary),
                            enabled = deviceSecure,
                            checked = settings.enabled,
                            onCheckedChange = {
                                repository.setEnabled(it)
                                settings = repository.state.value
                            },
                        )
                    }
                    if (!deviceSecure) {
                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.app_lock_no_credentials),
                                onClick = null,
                            )
                        }
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Timer,
                            title = stringResource(R.string.app_lock_timeout),
                            items = timeoutLabels,
                            selectedIndex = settings.timeout.ordinal,
                            enabled = settings.enabled,
                            onSelectedIndexChange = { index ->
                                repository.setTimeout(AppLockTimeout.entries[index])
                                settings = repository.state.value
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}
