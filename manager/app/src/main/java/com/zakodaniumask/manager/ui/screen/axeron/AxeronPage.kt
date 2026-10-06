// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.axeron

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.ShellUtils
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.axeron.AxClient
import com.zakodaniumask.manager.axeron.AxSettings
import com.zakodaniumask.manager.axeron.AxStarter
import com.zakodaniumask.manager.axeron.adb.AdbPairingService
import com.zakodaniumask.manager.data.privilege.PrivilegeManager
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.PrivBackend
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
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
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxeronPage() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val ksuCliRepository: KsuCliRepository = koinInject()
    val privilegeManager: PrivilegeManager = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var running by remember { mutableStateOf(AxClient.isRunning()) }
    var versionText by remember { mutableStateOf("") }
    var uidText by remember { mutableStateOf("") }
    var tcpMode by remember { mutableStateOf(AxSettings.getTcpMode()) }
    var activeOnBoot by remember { mutableStateOf(AxSettings.isActiveOnBoot()) }
    var backend by remember { mutableStateOf(PrivBackend.NONE) }

    val startedMsg = stringResource(R.string.axeron_started)
    val startFailedMsg = stringResource(R.string.axeron_start_failed)
    val copiedMsg = stringResource(R.string.axeron_command_copied)

    fun refreshStatus() {
        running = AxClient.isRunning()
        versionText = AxClient.getVersion()?.toString().orEmpty()
        uidText = AxClient.getUid()?.toString().orEmpty()
        backend = privilegeManager.current()
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        refreshStatus()
    }

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
                title = { Text(stringResource(R.string.axeron_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { refreshStatus() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.axeron_refresh))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 5.dp,
                    bottom = paddingValues.calculateBottomPadding() + 12.dp,
                ),
            ) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.axeron_summary),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        item {
                            SettingsBaseWidget(
                                icon = if (running) Icons.TwoTone.CheckCircle else Icons.TwoTone.Cancel,
                                iconSize = 18.dp,
                                title = stringResource(
                                    if (running) R.string.axeron_running else R.string.axeron_not_running
                                ),
                                description = if (running) "version=$versionText uid=$uidText" else null,
                                containerColor = if (running) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                onClick = null,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Code,
                                iconSize = 18.dp,
                                title = stringResource(R.string.axeron_backend),
                                description = stringResource(
                                    when (backend) {
                                        PrivBackend.ROOT -> R.string.axeron_backend_root
                                        PrivBackend.ROOTLESS -> R.string.axeron_backend_rootless
                                        PrivBackend.NONE -> R.string.axeron_backend_none
                                    }
                                ),
                                onClick = null,
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Computer,
                                title = stringResource(R.string.axeron_tcp_mode),
                                description = stringResource(R.string.axeron_tcp_mode_desc),
                                checked = tcpMode,
                                onCheckedChange = { enabled ->
                                    tcpMode = enabled
                                    AxSettings.setTcpMode(enabled)
                                },
                            )
                        }
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.PowerSettingsNew,
                                title = stringResource(R.string.axeron_active_on_boot),
                                description = stringResource(R.string.axeron_active_on_boot_desc),
                                checked = activeOnBoot,
                                onCheckedChange = { enabled ->
                                    activeOnBoot = enabled
                                    AxSettings.setActiveOnBoot(enabled)
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.PowerSettingsNew,
                                title = stringResource(R.string.axeron_start_root),
                                description = stringResource(R.string.axeron_start_root_desc),
                                onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching {
                                                ShellUtils.fastCmd(
                                                    ksuCliRepository.getRootShell(),
                                                    AxStarter.internalCommand(context),
                                                )
                                                true
                                            }.getOrDefault(false)
                                        }
                                        snackBarHost.showReplacingSnackbar(
                                            if (ok) startedMsg else startFailedMsg
                                        )
                                        refreshStatus()
                                    }
                                },
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Computer,
                                    title = stringResource(R.string.axeron_start_wireless),
                                    description = stringResource(R.string.axeron_start_wireless_desc),
                                    onClick = {
                                        runCatching {
                                            ContextCompat.startForegroundService(
                                                context,
                                                AdbPairingService.startIntent(context),
                                            )
                                        }.onFailure {
                                            scope.launch { snackBarHost.showReplacingSnackbar(startFailedMsg) }
                                        }
                                    },
                                )
                            }
                        }
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Save,
                                title = stringResource(R.string.axeron_copy_command),
                                description = stringResource(R.string.axeron_copy_command_desc),
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("axeron", AxStarter.adbCommand(context))
                                    )
                                    scope.launch { snackBarHost.showReplacingSnackbar(copiedMsg) }
                                },
                            )
                        }
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Code,
                                title = stringResource(R.string.axeron_shell),
                                description = stringResource(R.string.axeron_shell_desc),
                                onClick = { navigator.push(Route.AxeronShell) },
                            )
                        }
                    }
                }
            }
        }
    }
}
