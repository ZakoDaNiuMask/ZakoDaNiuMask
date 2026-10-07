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
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.DataObject
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.agent.AgentProviderProfile
import com.zakodaniumask.manager.data.agent.AgentProviderStore
import com.zakodaniumask.manager.data.agent.applyProfile
import com.zakodaniumask.manager.data.agent.llm.AgentModelsApi
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsTextFieldWidget
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
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    var snapshot by remember { mutableStateOf(store.seeded(viewModel.settings())) }

    fun activate(profile: AgentProviderProfile) {
        snapshot = store.setActive(snapshot, profile.id)
        viewModel.saveSettings(applyProfile(viewModel.settings(), profile))
    }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val activeLabel = stringResource(R.string.agent_provider_active)
    val setActiveLabel = stringResource(R.string.agent_provider_set_active)
    val deleteLabel = stringResource(R.string.agent_provider_delete)
    val deleteConfirm = stringResource(R.string.agent_provider_delete_confirm)

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
                SegmentedColumn(title = stringResource(R.string.agent_provider_manage)) {
                    snapshot.providers.forEach { profile ->
                        item(key = profile.id) {
                            val active = profile.id == snapshot.activeId
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Cloud,
                                title = profile.name,
                                description = buildString {
                                    append(profile.type.label)
                                    profile.effectiveModel.takeIf { it.isNotBlank() }?.let {
                                        append(" · ")
                                        append(it)
                                    }
                                },
                                selected = active,
                                onClick = {
                                    navigator.push(Route.AgentProviderEdit(profile.id))
                                },
                                trailingContent = {
                                    if (active) {
                                        Icon(
                                            Icons.TwoTone.CheckCircle,
                                            contentDescription = activeLabel,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        IconButton(onClick = { activate(profile) }) {
                                            Icon(
                                                Icons.TwoTone.CheckCircle,
                                                contentDescription = setActiveLabel,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val result = confirmDialog.awaitConfirm(
                                                        title = profile.name,
                                                        content = deleteConfirm,
                                                    )
                                                    if (result == ConfirmResult.Confirmed) {
                                                        snapshot = store.delete(snapshot, profile.id)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.TwoTone.Delete,
                                                contentDescription = deleteLabel,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.Add,
                            title = stringResource(R.string.agent_provider_add),
                            onClick = { navigator.push(Route.AgentProviderEdit("")) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
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

    val name = remember { TextFieldState(existing?.name.orEmpty()) }
    val endpoint = remember { TextFieldState(existing?.endpoint.orEmpty()) }
    val apiKey = remember { TextFieldState(existing?.apiKey.orEmpty()) }
    val apiPath = remember { TextFieldState(existing?.apiPath.orEmpty()) }
    val userAgent = remember { TextFieldState(existing?.userAgent.orEmpty()) }
    val extraHeaders = remember { TextFieldState(existing?.extraHeaders.orEmpty()) }
    val model = remember { TextFieldState(existing?.selectedModel.orEmpty()) }

    var type by remember { mutableStateOf(existing?.type ?: LlmProviderType.OPENAI) }
    var models by remember { mutableStateOf(existing?.models ?: emptyList()) }
    var fetching by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.agent_settings_saved)
    val fetchFailed = stringResource(R.string.agent_provider_fetch_failed)
    val fetched = stringResource(R.string.agent_provider_fetched)

    fun build(): AgentProviderProfile = AgentProviderProfile(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.text.toString().trim().ifBlank { type.label },
        type = type,
        endpoint = endpoint.text.toString().trim(),
        apiKey = apiKey.text.toString().trim(),
        apiPath = apiPath.text.toString().trim(),
        userAgent = userAgent.text.toString().trim(),
        extraHeaders = extraHeaders.text.toString().trim(),
        models = models,
        selectedModel = model.text.toString().trim(),
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
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = {
                    Text(
                        if (isNew) {
                            stringResource(R.string.agent_provider_add)
                        } else {
                            stringResource(R.string.agent_provider)
                        }
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
                        SettingsTextFieldWidget(
                            state = name,
                            title = stringResource(R.string.agent_provider_name),
                            useLabelAsPlaceholder = true,
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Cloud,
                            title = stringResource(R.string.agent_provider_type),
                            items = LlmProviderType.entries.map { it.label },
                            selectedIndex = type.ordinal,
                            onSelectedIndexChange = { type = LlmProviderType.entries[it] },
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = endpoint,
                            title = stringResource(R.string.agent_endpoint),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Link, contentDescription = null) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = apiKey,
                            title = stringResource(R.string.agent_api_key),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Key, contentDescription = null) },
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
                            leadingContent = { Icon(Icons.TwoTone.Badge, contentDescription = null) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = extraHeaders,
                            title = stringResource(R.string.agent_extra_headers),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.DataObject, contentDescription = null) },
                            lineLimits = TextFieldLineLimits.FourLines,
                        )
                    }
                    item {
                        SettingsTextFieldWidget(
                            state = model,
                            title = stringResource(R.string.agent_model),
                            useLabelAsPlaceholder = true,
                            leadingContent = { Icon(Icons.TwoTone.Memory, contentDescription = null) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                    if (models.isNotEmpty()) {
                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.agent_provider_fetch_models),
                                items = models,
                                selectedIndex = models.indexOf(model.text.toString())
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    model.edit { replace(0, length, models[index]) }
                                },
                            )
                        }
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Autorenew,
                            title = stringResource(R.string.agent_provider_fetch_models),
                            enabled = !fetching,
                            onClick = {
                                fetching = true
                                scope.launch {
                                    runCatching { AgentModelsApi.fetch(build()) }
                                        .onSuccess {
                                            models = it
                                            if (model.text.isBlank()) {
                                                it.firstOrNull()?.let { first ->
                                                    model.edit { replace(0, length, first) }
                                                }
                                            }
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
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Save,
                            title = stringResource(R.string.agent_save),
                            onClick = { save() },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp)) }
        }
    }
}

private fun Icon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
) {
    androidx.compose.material3.Icon(
        imageVector = image,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
