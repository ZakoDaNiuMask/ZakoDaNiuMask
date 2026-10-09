// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from YukiSU (GPL-3.0); modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
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
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.SuperKeyEvent
import com.zakodaniumask.manager.ui.viewmodel.SuperKeyViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuperKeyScreen(viewModel: SuperKeyViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val okMsg = stringResource(R.string.superkey_ok)
    val failMsg = stringResource(R.string.superkey_failed)
    val emptyMsg = stringResource(R.string.superkey_empty)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SuperKeyEvent.Result -> snackBarHost.showReplacingSnackbar(
                    when {
                        event.success -> okMsg
                        event.message == "empty" -> emptyMsg
                        else -> failMsg
                    },
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var input by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.superkey_title)) },
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
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.superkey_configured),
                            description = yesNo(uiState.configured),
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            title = stringResource(R.string.superkey_authenticated),
                            description = yesNo(uiState.authenticated),
                        )
                    }
                }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.superkey_key),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                viewModel.setInput(it)
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            label = { Text(stringResource(R.string.superkey_input)) },
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.authenticate(save = true) },
                                enabled = !uiState.working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.superkey_save_authenticate)) }
                            OutlinedButton(
                                onClick = { viewModel.authenticate(save = false) },
                                enabled = !uiState.working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.superkey_authenticate_once)) }
                            OutlinedButton(
                                onClick = { viewModel.clear() },
                                enabled = !uiState.working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.superkey_clear)) }
                        }
                    }
                }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.superkey_options),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    item {
                        SettingsSwitchWidget(
                            title = stringResource(R.string.superkey_auto_auth),
                            description = stringResource(R.string.superkey_auto_auth_summary),
                            checked = uiState.autoAuth,
                            onCheckedChange = { viewModel.setAutoAuth(it) },
                        )
                    }
                    item {
                        SettingsSwitchWidget(
                            title = stringResource(R.string.superkey_skip_storage),
                            description = stringResource(R.string.superkey_skip_storage_summary),
                            checked = uiState.skipStorage,
                            onCheckedChange = { viewModel.setSkipStorage(it) },
                        )
                    }
                    item {
                        SettingsSwitchWidget(
                            title = stringResource(R.string.superkey_patch),
                            description = stringResource(R.string.superkey_patch_summary),
                            checked = uiState.patchEnabled,
                            onCheckedChange = { viewModel.setPatchEnabled(it) },
                        )
                    }
                    item {
                        SettingsSwitchWidget(
                            title = stringResource(R.string.superkey_bypass),
                            description = stringResource(R.string.superkey_bypass_summary),
                            checked = uiState.signatureBypass,
                            onCheckedChange = { viewModel.setSignatureBypass(it) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 12.dp)) }
        }
    }
}

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(if (value) R.string.superkey_yes else R.string.superkey_no)
