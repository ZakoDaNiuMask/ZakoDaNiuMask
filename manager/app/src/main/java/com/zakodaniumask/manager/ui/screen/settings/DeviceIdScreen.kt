// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck-ToolBox (MIT), ui/src/features/device-ids; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.deviceid.DeviceIdProvisionResult
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.DeviceIdViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceIdScreen(viewModel: DeviceIdViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current

    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val confirmTitle = stringResource(R.string.device_id_confirm_title)
    val confirmMessage = stringResource(R.string.device_id_confirm)
    val confirmText = stringResource(R.string.confirm)

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.device_id_title)) },
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        val enabled = !uiState.isSubmitting

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
                WarningCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                    message = stringResource(R.string.device_id_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                )
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.device_id_section_main),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_brand),
                            value = uiState.profile.brand,
                            enabled = enabled,
                            onChange = { value -> viewModel.updateProfile { it.copy(brand = value) } },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_device),
                            value = uiState.profile.device,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(device = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_product),
                            value = uiState.profile.product,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(product = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_serial),
                            value = uiState.profile.serial,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(serial = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_manufacturer),
                            value = uiState.profile.manufacturer,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(manufacturer = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_model),
                            value = uiState.profile.model,
                            enabled = enabled,
                            onChange = { value -> viewModel.updateProfile { it.copy(model = value) } },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.device_id_section_advanced),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_imei),
                            value = uiState.profile.imei,
                            enabled = enabled,
                            onChange = { value -> viewModel.updateProfile { it.copy(imei = value) } },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_imei2),
                            value = uiState.profile.imei2,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(imei2 = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_meid),
                            value = uiState.profile.meid,
                            enabled = enabled,
                            onChange = { value -> viewModel.updateProfile { it.copy(meid = value) } },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_meid2),
                            value = uiState.profile.meid2,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(meid2 = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_ta_name),
                            value = uiState.profile.taName,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(taName = value) }
                            },
                        )
                    }
                    item {
                        DeviceIdField(
                            label = stringResource(R.string.device_id_ta_path),
                            value = uiState.profile.taPath,
                            enabled = enabled,
                            onChange = { value ->
                                viewModel.updateProfile { it.copy(taPath = value) }
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsSwitchWidget(
                            title = stringResource(R.string.device_id_dry_run),
                            description = stringResource(R.string.device_id_dry_run_desc),
                            checked = uiState.dryRun,
                            enabled = enabled,
                            onCheckedChange = { viewModel.setDryRun(it) },
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Button(
                        onClick = {
                            if (uiState.dryRun) {
                                viewModel.submit()
                            } else {
                                scope.launch {
                                    val result = confirmDialog.awaitConfirm(
                                        title = confirmTitle,
                                        content = confirmMessage,
                                        confirm = confirmText,
                                    )
                                    if (result == ConfirmResult.Confirmed) viewModel.submit()
                                }
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (uiState.dryRun) R.string.device_id_run_dry
                                else R.string.device_id_provision
                            )
                        )
                    }
                    TextButton(
                        onClick = { viewModel.loadDefaults() },
                        enabled = enabled,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.device_id_reload))
                    }
                }
            }

            uiState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            uiState.result?.let { result ->
                item {
                    DeviceIdResultCard(result)
                }
            }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 12.dp))
            }
        }
    }
}

@Composable
private fun DeviceIdField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            singleLine = true,
            enabled = enabled,
        )
    }
}

@Composable
private fun DeviceIdResultCard(result: DeviceIdProvisionResult) {
    val mode = stringResource(
        if (result.dryRun) R.string.device_id_mode_dry else R.string.device_id_mode_live
    )
    SegmentedColumn(
        title = stringResource(R.string.device_id_result),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item {
            SettingsBaseWidget(
                title = stringResource(R.string.device_id_result_mode),
                description = mode,
            )
        }
        item {
            SettingsBaseWidget(
                title = stringResource(R.string.device_id_result_count),
                description = result.count.toString(),
            )
        }
        result.taVersion?.let { version ->
            item {
                SettingsBaseWidget(
                    title = stringResource(R.string.device_id_result_ta_version),
                    description = version,
                )
            }
        }
        result.loadedLibrary?.let { library ->
            item {
                SettingsBaseWidget(
                    title = stringResource(R.string.device_id_result_library),
                    description = library,
                )
            }
        }
        result.ids.forEach { id ->
            item {
                SettingsBaseWidget(
                    title = id.label,
                    description = id.value,
                )
            }
        }
    }
}
