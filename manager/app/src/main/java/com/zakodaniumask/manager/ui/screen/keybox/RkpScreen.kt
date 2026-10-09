// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/rkp; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.keybox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.zakodaniumask.manager.data.rkp.DiceCurve
import com.zakodaniumask.manager.data.rkp.KeySourceKind
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.RkpEvent
import com.zakodaniumask.manager.ui.viewmodel.RkpViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RkpScreen(viewModel: RkpViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val savedMsg = stringResource(R.string.keybox_ts_saved)
    val failedMsg = stringResource(R.string.keybox_rkp_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RkpEvent.Failed -> snackBarHost.showReplacingSnackbar("$failedMsg: ${event.detail}")
                is RkpEvent.Message -> snackBarHost.showReplacingSnackbar(savedMsg)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val profile = uiState.profile

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_rkp_title)) },
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
            modifier = Modifier.fillMaxSize().blurSource().nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item { Spacer(Modifier.height(paddingValues.calculateTopPadding())) }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.keybox_rkp_section_key),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = profile.keySource.kind == KeySourceKind.SEED,
                                onClick = { viewModel.setKeySourceKind(KeySourceKind.SEED) },
                            )
                            Text(stringResource(R.string.keybox_rkp_seed))
                        }
                    }
                    item {
                        Field(
                            label = stringResource(R.string.keybox_rkp_seed_hex),
                            value = profile.keySource.seedHex,
                            onChange = { value -> viewModel.updateKeySource { it.copy(seedHex = value) } },
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = profile.keySource.kind == KeySourceKind.HW_KEY,
                                onClick = { viewModel.setKeySourceKind(KeySourceKind.HW_KEY) },
                            )
                            Text(stringResource(R.string.keybox_rkp_hw_key))
                        }
                    }
                    item {
                        Field(
                            label = stringResource(R.string.keybox_rkp_hw_key_hex),
                            value = profile.keySource.hwKeyHex,
                            onChange = { value -> viewModel.updateKeySource { it.copy(hwKeyHex = value) } },
                        )
                    }
                    item {
                        Field(
                            label = stringResource(R.string.keybox_rkp_kdf_label),
                            value = profile.keySource.kdfLabel,
                            onChange = { value -> viewModel.updateKeySource { it.copy(kdfLabel = value) } },
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.keybox_rkp_curve), modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { viewModel.setCurve(DiceCurve.ED25519) }) {
                                Text("ed25519")
                            }
                            Spacer(Modifier.height(0.dp))
                            OutlinedButton(onClick = { viewModel.setCurve(DiceCurve.P256) }) {
                                Text("p256")
                            }
                        }
                    }
                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.keybox_rkp_current_curve),
                            description = profile.curve.id,
                        )
                    }
                }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.keybox_rkp_section_device),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        Button(
                            onClick = { viewModel.applyDetected() },
                            enabled = !uiState.working,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(stringResource(R.string.keybox_rkp_read_device)) }
                    }
                    item { Field(stringResource(R.string.keybox_rkp_brand), profile.device.brand) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(brand = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_manufacturer), profile.device.manufacturer) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(manufacturer = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_product), profile.device.product) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(product = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_model), profile.device.model) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(model = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_device), profile.device.device) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(device = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_os_version), profile.device.osVersion) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(osVersion = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_security_level), profile.device.securityLevel) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(securityLevel = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_vb_state), profile.device.vbState) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(vbState = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_bootloader_state), profile.device.bootloaderState) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(bootloaderState = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_dice_issuer), profile.device.diceIssuer) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(diceIssuer = v)) } } }
                    item { Field(stringResource(R.string.keybox_rkp_dice_subject), profile.device.diceSubject) { v -> viewModel.updateProfile { it.copy(device = it.device.copy(diceSubject = v)) } } }
                }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.keybox_rkp_section_advanced),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item { Field(stringResource(R.string.keybox_rkp_server), profile.serverUrl) { v -> viewModel.updateProfile { it.copy(serverUrl = v) } } }
                    item { Field(stringResource(R.string.keybox_rkp_fingerprint), profile.fingerprint) { v -> viewModel.updateProfile { it.copy(fingerprint = v) } } }
                    item { Field(stringResource(R.string.keybox_rkp_num_keys), profile.numKeys.toString()) { v -> v.toIntOrNull()?.let(viewModel::setNumKeys) } }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.keybox_ts_save))
                    }
                    Button(onClick = { viewModel.derive() }, enabled = !uiState.working, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.keybox_rkp_derive))
                    }
                    Button(onClick = { viewModel.generateKeybox() }, enabled = !uiState.working, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.keybox_rkp_generate))
                    }
                    OutlinedButton(onClick = { viewModel.clear() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.keybox_rkp_reset))
                    }
                }
            }
            uiState.info?.let { info ->
                item {
                    SegmentedColumn(title = stringResource(R.string.keybox_rkp_info), modifier = Modifier.padding(horizontal = 16.dp)) {
                        item { SettingsBaseWidget(title = stringResource(R.string.keybox_rkp_mode), description = info.mode) }
                        item { SettingsBaseWidget(title = stringResource(R.string.keybox_rkp_public_key), description = info.publicKeyHex) }
                    }
                }
            }
            uiState.keybox?.let { keybox ->
                item {
                    SegmentedColumn(title = stringResource(R.string.keybox_rkp_result), modifier = Modifier.padding(horizontal = 16.dp)) {
                        item { SettingsBaseWidget(title = stringResource(R.string.keybox_rkp_keybox_path), description = keybox.keyboxPath) }
                        item { SettingsBaseWidget(title = stringResource(R.string.keybox_rkp_device_id), description = keybox.deviceId) }
                        item { SettingsBaseWidget(title = stringResource(R.string.keybox_rkp_cert_count), description = keybox.certCount.toString()) }
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 12.dp)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(label, value) { mutableStateOf(value) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
