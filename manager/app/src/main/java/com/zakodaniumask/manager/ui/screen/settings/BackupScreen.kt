// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Storage
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.backup.BackupKind
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsTextFieldWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.BackupViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen() {
    val viewModel: BackupViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.agent_settings_saved)

    var target by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(0) }
    val url = remember { TextFieldState(viewModel.webDavUrl()) }
    val user = remember { TextFieldState(viewModel.webDavUser()) }
    val pass = remember { TextFieldState(viewModel.webDavPass()) }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.backup_title)) },
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
                SegmentedColumn(title = stringResource(R.string.backup_target)) {
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Storage,
                            title = stringResource(R.string.backup_target),
                            items = listOf(
                                stringResource(R.string.backup_target_local),
                                stringResource(R.string.backup_target_webdav),
                            ),
                            selectedIndex = target,
                            onSelectedIndexChange = { target = it },
                        )
                    }
                }
            }

            if (target == 1) {
                item {
                    SegmentedColumn(title = stringResource(R.string.backup_target_webdav)) {
                        item {
                            SettingsTextFieldWidget(
                                state = url,
                                title = stringResource(R.string.backup_webdav_url),
                                useLabelAsPlaceholder = true,
                                leadingContent = { androidx.compose.material3.Icon(Icons.TwoTone.Cloud, null) },
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                        }
                        item {
                            SettingsTextFieldWidget(
                                state = user,
                                title = stringResource(R.string.backup_webdav_user),
                                useLabelAsPlaceholder = true,
                                leadingContent = { androidx.compose.material3.Icon(Icons.TwoTone.Storage, null) },
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                        }
                        item {
                            SettingsTextFieldWidget(
                                state = pass,
                                title = stringResource(R.string.backup_webdav_pass),
                                useLabelAsPlaceholder = true,
                                leadingContent = { androidx.compose.material3.Icon(Icons.TwoTone.Key, null) },
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Save,
                                title = stringResource(R.string.backup_save_webdav),
                                onClick = {
                                    viewModel.saveWebDav(
                                        url.text.toString(),
                                        user.text.toString(),
                                        pass.text.toString(),
                                    )
                                    scope.launch { snackbar.showReplacingSnackbar(savedMessage) }
                                },
                            )
                        }
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.backup_kind)) {
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Storage,
                            title = stringResource(R.string.backup_kind),
                            items = listOf(
                                stringResource(R.string.backup_kind_allowlist),
                                stringResource(R.string.backup_kind_module),
                                stringResource(R.string.backup_kind_boot),
                            ),
                            selectedIndex = kind,
                            onSelectedIndexChange = { kind = it },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.backup_title)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Backup,
                            title = stringResource(R.string.backup_now),
                            enabled = !state.busy,
                            onClick = { viewModel.backup(BackupKind.entries[kind], target == 1) },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Restore,
                            title = stringResource(R.string.backup_restore),
                            enabled = !state.busy,
                            onClick = { viewModel.restore(target == 1) },
                        )
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

