// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.agent

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
import androidx.compose.material.icons.twotone.Article
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudQueue
import androidx.compose.material.icons.twotone.DataObject
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Hub
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Lan
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.Psychology
import androidx.compose.material.icons.twotone.Public
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Thermostat
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentMcpPolicyRepository
import com.zakodaniumask.manager.data.agent.AgentMode
import com.zakodaniumask.manager.data.agent.AgentSettings
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.data.agent.llm.ThinkingLevel
import com.zakodaniumask.manager.data.agent.mcp.AgentTool
import com.zakodaniumask.manager.data.agent.web.WebFetchReader
import com.zakodaniumask.manager.data.agent.web.WebSearchBackend
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsTextFieldWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
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

/** Fallback tool domains until the live tool list is loaded. */
private val FALLBACK_DOMAINS = listOf(
    "ksu", "flash", "module", "feature", "sepolicy", "profile", "susfs",
    "umount_config", "kpm", "plugin", "debug", "kernel", "manager",
    "detector", "shell", "insmod", "resetprop", "soft_reboot", "anykernel3",
    "prop", "dynamic_manager", "initrc", "web", "file", "su", "appprofile", "browser",
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

    val endpoint = remember { TextFieldState(initial.endpoint) }
    val apiKey = remember { TextFieldState(initial.apiKey) }
    val model = remember { TextFieldState(initial.model) }
    val apiPath = remember { TextFieldState(initial.apiPath) }
    val userAgent = remember { TextFieldState(initial.userAgent) }
    val extraHeaders = remember { TextFieldState(initial.extraHeaders) }
    val temperature = remember { TextFieldState(initial.temperature.toString()) }
    val maxTokens = remember { TextFieldState(initial.maxTokens.toString()) }
    val maxIterations = remember { TextFieldState(initial.maxIterations.toString()) }
    val systemPrompt = remember { TextFieldState(initial.systemPrompt) }

    val webEndpoint = remember { TextFieldState(initial.webSearchEndpoint) }
    val webApiKey = remember { TextFieldState(initial.webSearchApiKey) }
    val webMaxResults = remember { TextFieldState(initial.webSearchMaxResults.toString()) }
    val webFetchApiKey = remember { TextFieldState(initial.webFetchApiKey) }

    var provider by remember { mutableStateOf(initial.provider) }
    var mode by remember { mutableStateOf(initial.mode) }
    var thinking by remember { mutableStateOf(initial.thinking) }
    var allowRootShell by remember { mutableStateOf(initial.allowRootShell) }
    var disabledDomains by remember { mutableStateOf(initial.disabledDomains) }
    var webEnabled by remember { mutableStateOf(initial.webSearchEnabled) }
    var webBackend by remember { mutableStateOf(initial.webSearchBackend) }
    var webReader by remember { mutableStateOf(initial.webFetchReader) }
    var webAllowLocal by remember { mutableStateOf(initial.webFetchAllowLocal) }

    var policy by remember { mutableStateOf(AgentMcpPolicyRepository.McpPolicy()) }
    var tools by remember { mutableStateOf<List<AgentTool>>(emptyList()) }
    var domains by remember { mutableStateOf(FALLBACK_DOMAINS) }

    LaunchedEffect(Unit) {
        policy = viewModel.loadPolicy()
        val loaded = runCatching { viewModel.loadTools() }.getOrDefault(emptyList())
        tools = loaded
        val live = loaded.map { it.name.substringBefore('.') }
            .filter { it.isNotBlank() }.distinct().sorted()
        if (live.isNotEmpty()) domains = live
    }

    fun currentSettings(): AgentSettings = initial.copy(
        provider = provider,
        endpoint = endpoint.text.toString().trim(),
        apiKey = apiKey.text.toString().trim(),
        model = model.text.toString().trim(),
        apiPath = apiPath.text.toString().trim(),
        userAgent = userAgent.text.toString().trim(),
        extraHeaders = extraHeaders.text.toString().trim(),
        temperature = temperature.text.toString().toDoubleOrNull() ?: 0.3,
        maxTokens = maxTokens.text.toString().toIntOrNull() ?: 2048,
        maxIterations = maxIterations.text.toString().toIntOrNull() ?: 8,
        mode = mode,
        systemPrompt = systemPrompt.text.toString(),
        disabledDomains = disabledDomains,
        allowRootShell = allowRootShell,
        thinking = thinking,
        webSearchEnabled = webEnabled,
        webSearchBackend = webBackend,
        webSearchEndpoint = webEndpoint.text.toString().trim(),
        webSearchApiKey = webApiKey.text.toString().trim(),
        webSearchMaxResults = webMaxResults.text.toString().toIntOrNull() ?: 5,
        webFetchReader = webReader,
        webFetchApiKey = webFetchApiKey.text.toString().trim(),
        webFetchAllowLocal = webAllowLocal,
    )

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val savedMessage = stringResource(R.string.agent_settings_saved)
    val policyTitle = stringResource(R.string.agent_policy_title)
    val domainsTitle = stringResource(R.string.agent_domains_title)
    val testDescription = state.testResult?.let { result ->
        val llm = if (result.llmOk) {
            stringResource(R.string.agent_test_llm_ok)
        } else {
            stringResource(R.string.agent_test_llm_fail, result.llmError ?: "")
        }
        val toolsLine = if (result.toolError == null) {
            stringResource(R.string.agent_test_tools_ok, result.toolCount)
        } else {
            stringResource(R.string.agent_test_tools_fail, result.toolError)
        }
        "$llm\n$toolsLine"
    }
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
    val thinkingLabels = listOf(
        stringResource(R.string.agent_thinking_off),
        stringResource(R.string.agent_thinking_low),
        stringResource(R.string.agent_thinking_medium),
        stringResource(R.string.agent_thinking_high),
    )

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
                SegmentedColumn(title = stringResource(R.string.agent_provider)) {
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Cloud,
                            title = stringResource(R.string.agent_provider),
                            items = LlmProviderType.entries.map { it.label },
                            selectedIndex = provider.ordinal,
                            onSelectedIndexChange = { provider = LlmProviderType.entries[it] },
                        )
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.CloudQueue,
                            title = stringResource(R.string.agent_provider_manage),
                            description = stringResource(R.string.agent_provider_manage_summary),
                            onClick = { navigator.push(Route.AgentProviders) },
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Shield,
                            title = stringResource(R.string.agent_mode),
                            items = modeLabels,
                            selectedIndex = mode.ordinal,
                            onSelectedIndexChange = { mode = AgentMode.entries[it] },
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Psychology,
                            title = stringResource(R.string.agent_reasoning_effort),
                            items = thinkingLabels,
                            selectedIndex = thinking.ordinal,
                            onSelectedIndexChange = { thinking = ThinkingLevel.entries[it] },
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = endpoint,
                            title = stringResource(R.string.agent_endpoint),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Link) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = apiKey,
                            title = stringResource(R.string.agent_api_key),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Key) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = model,
                            title = stringResource(R.string.agent_model),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Memory) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = apiPath,
                            title = stringResource(R.string.agent_api_path),
                            useLabelAsPlaceholder = true,
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = userAgent,
                            title = stringResource(R.string.agent_user_agent),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Badge) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = extraHeaders,
                            title = stringResource(R.string.agent_extra_headers),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.DataObject) },
                            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = temperature,
                            title = stringResource(R.string.agent_temperature),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Thermostat) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = maxTokens,
                            title = stringResource(R.string.agent_max_tokens),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.DataUsage) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = maxIterations,
                            title = stringResource(R.string.agent_max_iterations),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Autorenew) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = systemPrompt,
                            title = stringResource(R.string.agent_system_prompt),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Article) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.agent_shell_title)) {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Terminal,
                            title = stringResource(R.string.agent_allow_root_shell),
                            description = stringResource(R.string.agent_allow_root_shell_summary),
                            checked = allowRootShell,
                            onCheckedChange = { allowRootShell = it },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.agent_web_title)) {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Public,
                            title = stringResource(R.string.agent_web_enable),
                            description = stringResource(R.string.agent_web_enable_summary),
                            checked = webEnabled,
                            onCheckedChange = { webEnabled = it },
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Hub,
                            title = stringResource(R.string.agent_web_backend),
                            items = WebSearchBackend.entries.map { it.label },
                            selectedIndex = webBackend.ordinal,
                            enabled = webEnabled,
                            onSelectedIndexChange = { webBackend = WebSearchBackend.entries[it] },
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = webEndpoint,
                            title = stringResource(R.string.agent_web_endpoint),
                            useLabelAsPlaceholder = true,
                            enabled = webEnabled,
                            leadingContent = { Icon(Icons.TwoTone.Link) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = webApiKey,
                            title = stringResource(R.string.agent_web_api_key),
                            useLabelAsPlaceholder = true,
                            enabled = webEnabled,
                            leadingContent = { Icon(Icons.TwoTone.Key) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = webMaxResults,
                            title = stringResource(R.string.agent_web_max_results),
                            useLabelAsPlaceholder = true,
                            enabled = webEnabled,
                            leadingContent = { Icon(Icons.TwoTone.DataUsage) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Article,
                            title = stringResource(R.string.agent_web_fetch_reader),
                            items = WebFetchReader.entries.map { it.label },
                            selectedIndex = webReader.ordinal,
                            enabled = webEnabled,
                            onSelectedIndexChange = { webReader = WebFetchReader.entries[it] },
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = webFetchApiKey,
                            title = stringResource(R.string.agent_web_fetch_api_key),
                            useLabelAsPlaceholder = true,
                            enabled = webEnabled,
                            leadingContent = { Icon(Icons.TwoTone.Key) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Lan,
                            title = stringResource(R.string.agent_web_allow_local),
                            description = stringResource(R.string.agent_web_allow_local_summary),
                            checked = webAllowLocal,
                            onCheckedChange = { webAllowLocal = it },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.agent_test)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Science,
                            title = if (state.isTesting) {
                                stringResource(R.string.agent_testing)
                            } else {
                                stringResource(R.string.agent_test)
                            },
                            description = testDescription,
                            enabled = !state.isTesting,
                            onClick = { viewModel.testConnection(currentSettings()) },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.agent_policy_title)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Policy,
                            title = stringResource(R.string.agent_policy_summary),
                            description = null,
                            onClick = null,
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Shield,
                            title = stringResource(R.string.agent_policy_max_tier),
                            items = tierLabels,
                            selectedIndex = tierIds.indexOf(policy.maxTier).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val tier = tierIds[index]
                                scope.launch {
                                    viewModel.setPolicyMaxTier(tier)
                                    policy = viewModel.loadPolicy()
                                }
                            },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Extension,
                            title = stringResource(R.string.agent_policy_load_tools),
                            description = stringResource(
                                R.string.agent_policy_tools_count,
                                tools.size,
                            ),
                            onClick = { scope.launch { tools = viewModel.loadTools() } },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Autorenew,
                            title = stringResource(R.string.agent_policy_reset),
                            onClick = {
                                scope.launch {
                                    viewModel.resetPolicy()
                                    policy = viewModel.loadPolicy()
                                }
                            },
                        )
                    }
                }
            }

            lazySegmentColumn(
                items = tools,
                title = policyTitle,
                key = { _, tool -> tool.name },
            ) { _, tool ->
                val selection = when (tool.name) {
                    in policy.deny -> 2
                    in policy.allow -> 1
                    else -> 0
                }
                SettingsChooseWidget(
                    iconPlaceholder = false,
                    title = tool.name,
                    items = listOf(
                        stringResource(R.string.agent_policy_default),
                        stringResource(R.string.agent_policy_allow),
                        stringResource(R.string.agent_policy_deny),
                    ),
                    selectedIndex = selection,
                    onSelectedIndexChange = { choice ->
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

            lazySegmentColumn(
                items = domains,
                title = domainsTitle,
                key = { _, domain -> domain },
            ) { _, domain ->
                SettingsSwitchWidget(
                    icon = Icons.TwoTone.Extension,
                    title = domain,
                    description = stringResource(R.string.agent_domains_summary),
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

            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Save,
                            title = stringResource(R.string.agent_save),
                            onClick = {
                                viewModel.saveSettings(currentSettings())
                                scope.launch { snackbar.showReplacingSnackbar(savedMessage) }
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

@Composable
private fun Icon(image: androidx.compose.ui.graphics.vector.ImageVector) {
    androidx.compose.material3.Icon(
        imageVector = image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
