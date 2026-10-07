// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.spoof

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.theme.cardConfig
import com.zakodaniumask.manager.ui.theme.themeConfig
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.CpuSpoofEvent
import com.zakodaniumask.manager.ui.viewmodel.CpuSpoofViewModel
import org.koin.compose.viewmodel.koinViewModel

private val MIDR_PRESETS = listOf(
    "0x413fd050" to "Cortex-A55",
    "0x413fd0b1" to "Cortex-A76",
    "0x413fd0d2" to "Cortex-A78",
    "0x413fd0d4" to "Cortex-A710",
    "0x413fd0d5" to "Cortex-A715",
    "0x413fd0c1" to "Cortex-X1",
    "0x413fd0e3" to "Cortex-X3",
    "0x413fd0c0" to "Neoverse N1",
    "0x511f804d" to "Kryo 4xx Gold",
    "0x511f805c" to "Kryo 5xx Gold",
    "0x513f8050" to "Kryo 6xx Gold+",
    "0x553f1000" to "Exynos M5",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuSpoofScreen(viewModel: CpuSpoofViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val applySuccessMsg = stringResource(R.string.spoof_cpu_apply_success)
    val applyFailedMsg = stringResource(R.string.spoof_cpu_apply_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CpuSpoofEvent.Result ->
                    snackBarHost.showReplacingSnackbar(
                        if (event.success) applySuccessMsg else applyFailedMsg
                    )
            }
        }
    }

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
                title = { Text(stringResource(R.string.cpu_spoof_title)) },
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
                    message = stringResource(R.string.spoof_cpu_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
            }

            item {
                CpuSpoofForm(
                    currentMidr = uiState.current?.midrHex,
                    currentBogomips = uiState.current?.bogomips ?: 0,
                    currentHwcap = uiState.current?.hwcap,
                    currentHwcap2 = uiState.current?.hwcap2,
                    coreCount = uiState.current?.coreCount ?: 1,
                    isApplying = uiState.isApplying,
                    onApply = { cpuIndex, midr, bogomips, hwcap, hwcap2 ->
                        viewModel.apply(cpuIndex, midr, bogomips, hwcap, hwcap2)
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun CpuSpoofForm(
    currentMidr: String?,
    currentBogomips: Int,
    currentHwcap: String?,
    currentHwcap2: String?,
    coreCount: Int,
    isApplying: Boolean,
    onApply: (Int, String, Int, String, String) -> Unit,
) {
    var midrValue by remember(currentMidr) { mutableStateOf(currentMidr ?: "0x0") }
    var bogomipsValue by remember(currentBogomips) { mutableStateOf(currentBogomips.toString()) }
    var hwcapValue by remember(currentHwcap) { mutableStateOf(currentHwcap ?: "0x0") }
    var hwcap2Value by remember(currentHwcap2) { mutableStateOf(currentHwcap2 ?: "0x0") }
    var applyToAll by remember { mutableStateOf(true) }
    var invalidInput by remember { mutableStateOf(false) }
    var presetDialogVisible by remember { mutableStateOf(false) }

    val coresAllLabel = stringResource(R.string.spoof_cpu_cores_all)
    val applyLabel = stringResource(R.string.spoof_cpu_apply)

    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item {
            OverrideRow(
                label = stringResource(R.string.spoof_cpu_field_midr),
                value = midrValue,
                onValueChange = { midrValue = it },
                enabled = !isApplying,
                trailing = {
                    TextButton(onClick = { presetDialogVisible = true }) {
                        Text(stringResource(R.string.spoof_cpu_midr_preset_label))
                    }
                },
            )
        }
        item {
            OverrideRow(
                label = stringResource(R.string.spoof_cpu_field_bogomips),
                value = bogomipsValue,
                onValueChange = { bogomipsValue = it },
                enabled = !isApplying,
            )
        }
        item {
            OverrideRow(
                label = stringResource(R.string.spoof_cpu_field_hwcap),
                value = hwcapValue,
                onValueChange = { hwcapValue = it },
                enabled = !isApplying,
            )
        }
        item {
            OverrideRow(
                label = stringResource(R.string.spoof_cpu_field_hwcap2),
                value = hwcap2Value,
                onValueChange = { hwcap2Value = it },
                enabled = !isApplying,
            )
        }
        item {
            SettingsSwitchWidget(
                title = coresAllLabel,
                checked = applyToAll,
                enabled = !isApplying,
                onCheckedChange = { applyToAll = it },
            )
        }
        item {
            Button(
                onClick = {
                    val bogomips = bogomipsValue.toIntOrNull()
                    if (midrValue.isBlank() || bogomips == null || bogomips < 0) {
                        invalidInput = true
                        return@Button
                    }
                    invalidInput = false
                    onApply(
                        if (applyToAll) -1 else 0,
                        midrValue.trim(),
                        bogomips,
                        hwcapValue.trim(),
                        hwcap2Value.trim(),
                    )
                },
                enabled = !isApplying,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(applyLabel)
            }
        }
    }

    if (presetDialogVisible) {
        AlertDialog(
            onDismissRequest = { presetDialogVisible = false },
            title = { Text(stringResource(R.string.spoof_cpu_midr_preset_label)) },
            text = {
                LazyColumn {
                    MIDR_PRESETS.forEach { (midr, name) ->
                        item(key = midr) {
                            TextButton(
                                onClick = {
                                    midrValue = midr
                                    presetDialogVisible = false
                                },
                            ) {
                                Text("$name ($midr)")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { presetDialogVisible = false }) {
                    Text(stringResource(R.string.spoof_cpu_cancel))
                }
            },
        )
    }

    if (invalidInput) {
        Text(
            text = stringResource(R.string.spoof_cpu_invalid_input),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun OverrideRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
            trailingIcon = trailing,
        )
    }
}
