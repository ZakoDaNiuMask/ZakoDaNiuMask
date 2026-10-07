// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentProviderProfile
import com.zakodaniumask.manager.data.agent.AgentProviderStore
import com.zakodaniumask.manager.data.agent.applyProfile
import com.zakodaniumask.manager.data.agent.llm.AgentModelsApi
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
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
import java.util.UUID
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentProvidersScreen() {
    val store: AgentProviderStore = koinInject()
    val viewModel: AgentViewModel = koinViewModel()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current

    var snapshot by remember { mutableStateOf(store.seeded(viewModel.settings())) }

    fun activate(profile: AgentProviderProfile) {
        snapshot = store.setActive(snapshot, profile.id)
        viewModel.saveSettings(applyProfile(viewModel.settings(), profile))
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
                title = { Text(stringResource(R.string.agent_providers)) },
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
                        Text(
                            text = stringResource(R.string.agent_provider_manage_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    snapshot.providers.forEach { profile ->
                        item(key = profile.id) {
                            ProviderRow(
                                profile = profile,
                                active = profile.id == snapshot.activeId,
                                onSelect = { navigator.push(Route.AgentProviderEdit(profile.id)) },
                                onActivate = { activate(profile) },
                                onDelete = { snapshot = store.delete(snapshot, profile.id) },
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { navigator.push(Route.AgentProviderEdit("")) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(stringResource(R.string.agent_provider_add))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding())) }
        }
    }
}

@Composable
private fun ProviderRow(
    profile: AgentProviderProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = profile.type.label +
                    profile.effectiveModel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.agent_provider_active),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onActivate) {
                Text(stringResource(R.string.agent_provider_set_active))
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.agent_provider_delete))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentProviderEditScreen(profileId: String) {
    val store: AgentProviderStore = koinInject()
    val viewModel: AgentViewModel = koinViewModel()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val existing = remember { store.load().providers.firstOrNull { it.id == profileId } }
    val isNew = existing == null

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: LlmProviderType.OPENAI) }
    var endpoint by remember { mutableStateOf(existing?.endpoint.orEmpty()) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var apiPath by remember { mutableStateOf(existing?.apiPath.orEmpty()) }
    var userAgent by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var extraHeaders by remember { mutableStateOf(existing?.extraHeaders.orEmpty()) }
    var model by remember { mutableStateOf(existing?.selectedModel.orEmpty()) }
    var models by remember { mutableStateOf(existing?.models ?: emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    val fetchFailed = stringResource(R.string.agent_provider_fetch_failed)
    val fetched = stringResource(R.string.agent_provider_fetched)
    val savedMessage = stringResource(R.string.agent_settings_saved)

    fun build(): AgentProviderProfile = AgentProviderProfile(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.ifBlank { type.label },
        type = type,
        endpoint = endpoint.trim(),
        apiKey = apiKey.trim(),
        apiPath = apiPath.trim(),
        userAgent = userAgent.trim(),
        extraHeaders = extraHeaders.trim(),
        models = models,
        selectedModel = model.trim(),
    )

    fun save() {
        val profile = build()
        val snapshot = store.load()
        val wasEmpty = snapshot.providers.isEmpty()
        store.upsert(snapshot, profile)
        if (wasEmpty) store.setActive(store.load(), profile.id)
        viewModel.saveSettings(applyProfile(viewModel.settings(), profile))
        scope.launch { snackbar.showReplacingSnackbar(savedMessage) }
        navigator.pop()
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
                title = {
                    Text(
                        if (isNew) stringResource(R.string.agent_provider_add)
                        else stringResource(R.string.agent_provider)
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Spacer(Modifier.height(paddingValues.calculateTopPadding()))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SegmentedColumn {
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_provider_name),
                                value = name,
                                onValueChange = { name = it },
                            )
                        }
                        item {
                            ChoiceField(
                                label = stringResource(R.string.agent_provider_type),
                                value = type.label,
                                options = LlmProviderType.entries.map { it.label },
                                onSelect = {
                                    type = LlmProviderType.entries[it]
                                    if (endpoint.isBlank()) endpoint = ""
                                },
                            )
                        }
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_endpoint),
                                value = endpoint,
                                placeholder = type.defaultEndpoint,
                                onValueChange = { endpoint = it },
                            )
                        }
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_api_key),
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                isPassword = true,
                            )
                        }
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_api_path),
                                value = apiPath,
                                onValueChange = { apiPath = it },
                            )
                        }
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_user_agent),
                                value = userAgent,
                                onValueChange = { userAgent = it },
                            )
                        }
                        item {
                            LabeledField(
                                label = stringResource(R.string.agent_extra_headers),
                                value = extraHeaders,
                                onValueChange = { extraHeaders = it },
                                singleLine = false,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LabeledField(
                                        label = stringResource(R.string.agent_model),
                                        value = model,
                                        onValueChange = { model = it },
                                    )
                                }
                                if (models.isNotEmpty()) {
                                    Box {
                                        TextButton(onClick = { modelMenu = true }) {
                                            Text("\u25be")
                                        }
                                        DropdownMenu(
                                            expanded = modelMenu,
                                            onDismissRequest = { modelMenu = false },
                                        ) {
                                            models.forEach { candidate ->
                                                DropdownMenuItem(
                                                    text = { Text(candidate) },
                                                    onClick = {
                                                        model = candidate
                                                        modelMenu = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    if (fetching) return@Button
                                    fetching = true
                                    scope.launch {
                                        runCatching { AgentModelsApi.fetch(build()) }
                                            .onSuccess {
                                                models = it
                                                if (model.isBlank()) model = it.firstOrNull().orEmpty()
                                                snackbar.showReplacingSnackbar(fetched.format(it.size))
                                            }
                                            .onFailure {
                                                snackbar.showReplacingSnackbar(
                                                    "$fetchFailed: ${it.message.orEmpty()}"
                                                )
                                            }
                                        fetching = false
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(stringResource(R.string.agent_provider_fetch_models))
                            }
                        }
                        item {
                            Button(
                                onClick = { save() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(stringResource(R.string.agent_save))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(paddingValues.calculateBottomPadding())) }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
            singleLine = singleLine,
            visualTransformation = if (isPassword) PasswordVisualTransformation()
            else VisualTransformation.None,
        )
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
