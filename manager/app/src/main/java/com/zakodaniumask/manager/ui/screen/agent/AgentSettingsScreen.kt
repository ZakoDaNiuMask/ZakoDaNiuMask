// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentMcpPolicyRepository
import com.zakodaniumask.manager.data.agent.AgentMode
import com.zakodaniumask.manager.data.agent.AgentSettings
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.data.agent.mcp.AgentTool
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

private val KNOWN_DOMAINS = listOf(
    "ksu", "flash", "module", "feature", "sepolicy", "profile", "susfs",
    "umount_config", "kpm", "plugin", "debug", "kernel", "manager",
    "insmod", "resetprop", "soft_reboot", "anykernel3",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentSettingsScreen() {
    val viewModel: AgentViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    var apiPath by remember { mutableStateOf(initial.apiPath) }
    var userAgent by remember { mutableStateOf(initial.userAgent) }
    var extraHeaders by remember { mutableStateOf(initial.extraHeaders) }
    var temperature by remember { mutableStateOf(initial.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var maxIterations by remember { mutableStateOf(initial.maxIterations.toString()) }
    var mode by remember { mutableStateOf(initial.mode) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
    var disabledDomains by remember { mutableStateOf(initial.disabledDomains) }

    var policy by remember {
        mutableStateOf(AgentMcpPolicyRepository.McpPolicy())
    }
    var tools by remember { mutableStateOf<List<AgentTool>>(emptyList()) }

    LaunchedEffect(Unit) {
        policy = viewModel.loadPolicy()
    }

    fun currentSettings(): AgentSettings = initial.copy(
        provider = provider,
        endpoint = endpoint.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        apiPath = apiPath.trim(),
        userAgent = userAgent.trim(),
        extraHeaders = extraHeaders.trim(),
        temperature = temperature.toDoubleOrNull() ?: 0.3,
        maxTokens = maxTokens.toIntOrNull() ?: 2048,
        maxIterations = maxIterations.toIntOrNull() ?: 8,
        mode = mode,
        systemPrompt = systemPrompt,
        disabledDomains = disabledDomains,
    )

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val savedMessage = stringResource(R.string.agent_settings_saved)
    val modeLabels = listOf(
        stringResource(R.string.agent_mode_read_only),
        stringResource(R.string.agent_mode_write),
        stringResource(R.string.agent_mode_full_auto),
    )
    val tierLabels = listOf(
        stringResource(R.string.agent_tier_read),
        stringResource(R.string.agent_tier_write),
        stringResource(R.string.agent_tier_danger),
    )
    val tierIds = listOf("read", "write", "danger")

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
                            label = stringResource(R.string.agent_api_path),
                            value = apiPath,
                            onValueChange = { apiPath = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_user_agent),
                            value = userAgent,
                            onValueChange = { userAgent = it },
                        )
                    }
                    item {
                        TextFieldRow(
                            label = stringResource(R.string.agent_extra_headers),
                            value = extraHeaders,
                            onValueChange = { extraHeaders = it },
                            singleLine = false,
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
                                viewModel.saveSettings(currentSettings())
                                scope.launch { snackbar.showReplacingSnackbar(savedMessage) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(stringResource(R.string.agent_save))
                        }
                    }
                }
            }

            // --- Test connection ---
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.agent_test),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.testConnection(currentSettings()) },
                        enabled = !state.isTesting,
                    ) {
                        Text(
                            if (state.isTesting) stringResource(R.string.agent_testing)
                            else stringResource(R.string.agent_test)
                        )
                    }
                    state.testResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (result.llmOk) {
                                stringResource(R.string.agent_test_llm_ok)
                            } else {
                                stringResource(
                                    R.string.agent_test_llm_fail,
                                    result.llmError ?: "",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = if (result.toolError == null) {
                                stringResource(R.string.agent_test_tools_ok, result.toolCount)
                            } else {
                                stringResource(R.string.agent_test_tools_fail, result.toolError)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // --- Tool domains ---
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.agent_domains_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.agent_domains_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    KNOWN_DOMAINS.forEach { domain ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = domain !in disabledDomains,
                                onCheckedChange = { enabled ->
                                    disabledDomains = if (enabled) {
                                        disabledDomains - domain
                                    } else {
                                        disabledDomains + domain
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // --- Kernel MCP policy ---
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.agent_policy_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.agent_policy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val currentTier = policy.maxTier
                    ChoiceField(
                        label = stringResource(R.string.agent_policy_max_tier),
                        value = tierLabels.getOrElse(tierIds.indexOf(currentTier)) { tierLabels[0] },
                        options = tierLabels,
                        onSelect = { index ->
                            val tier = tierIds[index]
                            scope.launch {
                                viewModel.setPolicyMaxTier(tier)
                                policy = viewModel.loadPolicy()
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            scope.launch { tools = viewModel.loadTools() }
                        }) {
                            Text(stringResource(R.string.agent_policy_load_tools))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                viewModel.resetPolicy()
                                policy = viewModel.loadPolicy()
                            }
                        }) {
                            Text(stringResource(R.string.agent_policy_reset))
                        }
                    }
                    if (tools.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.agent_policy_tools_count, tools.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        tools.forEach { tool ->
                            val current = when (tool.name) {
                                in policy.deny -> 2
                                in policy.allow -> 1
                                else -> 0
                            }
                            ToolPolicyRow(
                                name = tool.name,
                                selection = current,
                                onSelect = { choice ->
                                    scope.launch {
                                        when (choice) {
                                            1 -> viewModel.allowPolicyTool(tool.name)
                                            2 -> viewModel.denyPolicyTool(tool.name)
                                            else -> viewModel.clearPolicyTool(tool.name)
                                        }
                                        policy = viewModel.loadPolicy()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToolPolicyRow(
    name: String,
    selection: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf(
        stringResource(R.string.agent_policy_default),
        stringResource(R.string.agent_policy_allow),
        stringResource(R.string.agent_policy_deny),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { expanded = true }) { Text(labels[selection]) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
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
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}
