// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.agent

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentMode
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.AgentViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentSettingsScreen() {
    val viewModel: AgentViewModel = koinViewModel()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val initial = remember { viewModel.settings() }
    var provider by remember { mutableStateOf(initial.provider) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var temperature by remember { mutableStateOf(initial.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var maxIterations by remember { mutableStateOf(initial.maxIterations.toString()) }
    var mode by remember { mutableStateOf(initial.mode) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val savedMessage = stringResource(R.string.agent_settings_saved)
    val modeLabels = listOf(
        stringResource(R.string.agent_mode_read_only),
        stringResource(R.string.agent_mode_write),
        stringResource(R.string.agent_mode_full_auto),
    )

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
                title = { Text(stringResource(R.string.agent_settings)) },
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
                SegmentedColumn {
                    item {
                        ChoiceField(
                            label = stringResource(R.string.agent_provider),
                            value = provider.label,
                            options = LlmProviderType.entries.map { it.label },
                            onSelect = { provider = LlmProviderType.entries[it] },
                        )
                    }
                    item {
                        ChoiceField(
                            label = stringResource(R.string.agent_mode),
                            value = modeLabels[mode.ordinal],
                            options = modeLabels,
                            onSelect = { mode = AgentMode.entries[it] },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_endpoint),
                            value = endpoint,
                            onValueChange = { endpoint = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_api_key),
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            isPassword = true,
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_model),
                            value = model,
                            onValueChange = { model = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_temperature),
                            value = temperature,
                            onValueChange = { temperature = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_max_tokens),
                            value = maxTokens,
                            onValueChange = { maxTokens = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_max_iterations),
                            value = maxIterations,
                            onValueChange = { maxIterations = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_system_prompt),
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            singleLine = false,
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                viewModel.saveSettings(
                                    initial.copy(
                                        provider = provider,
                                        endpoint = endpoint.trim(),
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                        temperature = temperature.toDoubleOrNull() ?: 0.3,
                                        maxTokens = maxTokens.toIntOrNull() ?: 2048,
                                        maxIterations = maxIterations.toIntOrNull() ?: 8,
                                        mode = mode,
                                        systemPrompt = systemPrompt,
                                    )
                                )
                                scope.launch { snackbar.showReplacingSnackbar(savedMessage) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(stringResource(R.string.agent_save))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 8,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        )
    }
}
