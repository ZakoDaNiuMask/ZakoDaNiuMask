// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.FolkMountMode
import com.zakodaniumask.manager.domain.model.FolkMountStatus
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolkMountScreen() {
    val ksuCli: KsuCliRepository = koinInject()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<FolkMountStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        error = null
        status = withContext(Dispatchers.IO) {
            runCatching {
                val raw = ksuCli.mountStatusJson().trim()
                FolkMountStatus.fromJson(JSONObject(raw))
            }.getOrElse {
                error = it.message ?: it.javaClass.simpleName
                null
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val modeLabels = listOf(
        stringResource(R.string.folk_mount_mode_auto),
        stringResource(R.string.folk_mount_mode_builtin),
        stringResource(R.string.folk_mount_mode_metamodule),
    )
    val appliedMessage = stringResource(R.string.folk_mount_applied)
    val enabledText = stringResource(R.string.folk_mount_enabled)
    val disabledText = stringResource(R.string.folk_mount_disabled)

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
                title = { Text(stringResource(R.string.folk_mount_title)) },
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
                SegmentedColumn(title = stringResource(R.string.folk_mount_mode)) {
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Layers,
                            title = stringResource(R.string.folk_mount_mode),
                            description = stringResource(R.string.folk_mount_mode_summary),
                            items = modeLabels,
                            selectedIndex = status?.configuredMode?.ordinal ?: 0,
                            onSelectedIndexChange = { index ->
                                val mode = FolkMountMode.entries[index]
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { ksuCli.setMountMode(mode.token) }
                                            .getOrDefault(false)
                                    }
                                    if (ok) {
                                        reload()
                                        snackbar.showReplacingSnackbar(appliedMessage)
                                    } else {
                                        error = "set-mode failed"
                                    }
                                }
                            },
                        )
                    }
                }
            }

            status?.let { s ->
                item {
                    SegmentedColumn(title = stringResource(R.string.folk_mount_status)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Cloud,
                                title = stringResource(R.string.folk_mount_next_provider),
                                description = s.nextProvider?.token ?: "-",
                                onClick = null,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.folk_mount_metamodule),
                                description = (s.metamoduleId ?: "-") +
                                    " (" + if (s.metamoduleEnabled) enabledText else disabledText + ")",
                                onClick = null,
                            )
                        }
                        item {
                            val targetsText = s.targetCount?.let {
                                stringResource(R.string.folk_mount_targets, it)
                            }
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.folk_mount_boot_result),
                                description = listOfNotNull(
                                    s.bootProvider?.token,
                                    s.bootResult.takeIf { it.isNotBlank() },
                                    targetsText,
                                ).joinToString(" \u00b7 "),
                                onClick = null,
                            )
                        }
                    }
                }
            }

            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

