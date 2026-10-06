// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.ghostlock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ghostlock.domain.model.ProfileConfig
import com.zakodaniumask.manager.ghostlock.ui.GhostlockViewModel
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import org.koin.compose.koinInject
import com.zakodaniumask.manager.ghostlock.R as GR

private const val GHOSTLOCK_PROJECT_URL = "https://github.com/YuKongA/ghostlock-app"

@Composable
fun GhostlockAdvancedScreen() {
    val viewModel: GhostlockViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) { viewModel.onOpenAdvanced() }

    var showRouteDialog by remember { mutableStateOf(false) }
    var showFallbackDialog by remember { mutableStateOf(false) }

    GhostlockScaffold(title = stringResource(GR.string.advanced_settings)) {
        item { SectionTitle(stringResource(GR.string.advanced_settings)) }
        item {
            SettingsJumpPageWidget(
                title = stringResource(R.string.ghostlock_route),
                description = state.profileRoute ?: stringResource(R.string.ghostlock_route_auto),
                onClick = { showRouteDialog = true },
            )
        }
        item {
            SettingsJumpPageWidget(
                title = stringResource(R.string.ghostlock_fallback),
                description = state.profileFallback
                    ?.takeIf { it != "none" }
                    ?: stringResource(R.string.ghostlock_fallback_none),
                onClick = { showFallbackDialog = true },
            )
        }
        items(state.executionFields) { field ->
            OverrideRow(
                label = field.path,
                value = state.executionEditing[field.path] ?: field.value.toString(),
                onValueChange = { actions.onExecutionFieldChanged(field.path, it) },
            )
        }
        item { ActionRow(GR.string.profile_save) { actions.onSaveProfileEdits() } }
        item { ActionRow(GR.string.profile_save_as) { actions.onSaveProfileAs() } }
        item { ActionRow(GR.string.profile_revert) { actions.onRevertProfileEdits() } }
        item { ActionRow(GR.string.override_export) { actions.onExportProfileEdits() } }
        item { ActionRow(GR.string.debug_profile_override) { navigator.push(Route.GhostlockParameters) } }
        item {
            SettingsSwitchWidget(
                title = stringResource(GR.string.debug_export_log),
                description = stringResource(GR.string.debug_export_log_summary),
                checked = state.debugExportEnabled,
                onCheckedChange = { actions.onDebugExportChanged(it) },
            )
        }
        item {
            SettingsJumpPageWidget(
                title = stringResource(GR.string.debug_export_location),
                description = state.debugExportLocation,
                onClick = { actions.onDebugExportLocationPick() },
            )
        }
        item {
            SettingsSwitchWidget(
                title = stringResource(GR.string.debug_kernel_log),
                description = stringResource(GR.string.debug_kernel_log_summary),
                checked = state.debugKernelLogEnabled,
                onCheckedChange = { actions.onDebugKernelLogChanged(it) },
            )
        }
    }

    if (showRouteDialog) {
        val auto = stringResource(R.string.ghostlock_route_auto)
        val options = listOf(auto) + ProfileConfig.Routes
        val selected = state.profileRoute?.let { ProfileConfig.Routes.indexOf(it) + 1 } ?: 0
        ListDialog(
            title = stringResource(R.string.ghostlock_route),
            items = options,
            selectedIndex = selected,
            onDismiss = { showRouteDialog = false },
            onSelect = { index ->
                showRouteDialog = false
                actions.onRouteChanged(index)
            },
        )
    }

    if (showFallbackDialog) {
        val none = stringResource(R.string.ghostlock_fallback_none)
        val options = listOf(none) + ProfileConfig.Routes
        val selected = state.profileFallback
            ?.takeIf { it != "none" }
            ?.let { ProfileConfig.Routes.indexOf(it) + 1 } ?: 0
        ListDialog(
            title = stringResource(R.string.ghostlock_fallback),
            items = options,
            selectedIndex = selected,
            onDismiss = { showFallbackDialog = false },
            onSelect = { index ->
                showFallbackDialog = false
                actions.onFallbackChanged(index)
            },
        )
    }
}

@Composable
fun GhostlockParametersScreen() {
    val viewModel: GhostlockViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }

    LaunchedEffect(Unit) { viewModel.onOpenParameters() }

    GhostlockScaffold(title = stringResource(GR.string.parameters)) {
        item { SectionTitle(stringResource(GR.string.parameters)) }
        items(state.profileOverrideRoots) { node ->
            if (!node.isGroup) {
                OverrideRow(
                    label = node.name,
                    value = state.profileOverrideEditing[node.path]
                        ?: node.value?.toString().orEmpty(),
                    onValueChange = { actions.onProfileOverrideChanged(node.path, it) },
                )
            }
        }
    }
}

@Composable
fun GhostlockBuiltinScreen() {
    val viewModel: GhostlockViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) { viewModel.onOpenBuiltinProfiles() }

    GhostlockScaffold(title = stringResource(GR.string.load_config_title)) {
        item { SectionTitle(stringResource(GR.string.load_config_title)) }
        items(state.builtinProfiles) { release ->
            SelectRow(release, release == state.activeBuiltinProfile) {
                actions.onSelectBuiltinProfile(release)
                navigator.pop()
            }
        }
        if (state.builtinTemplates.isNotEmpty()) {
            item { SectionTitle(stringResource(GR.string.templates_section)) }
            items(state.builtinTemplates) { release ->
                SelectRow(release, false) { actions.onSelectBuiltinProfile(release) }
            }
        }
        item {
            SelectRow(stringResource(GR.string.load_builtin_auto), state.activeBuiltinProfile == null) {
                actions.onSelectBuiltinProfile(null)
                navigator.pop()
            }
        }
    }
}

@Composable
fun GhostlockUserProfileScreen(name: String) {
    val viewModel: GhostlockViewModel = koinInject()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }

    LaunchedEffect(name) { viewModel.onOpenUserProfileDetail(name) }

    GhostlockScaffold(title = name) {
        item { SectionTitle(name) }
        item { ActionRow(GR.string.user_profile_load) { actions.onLoadUserProfile(name) } }
        item { ActionRow(GR.string.user_profile_unload) { actions.onUnloadUserProfile() } }
        item { ActionRow(GR.string.user_profile_edit) { actions.onEditUserProfile(name) } }
        item { ActionRow(GR.string.user_profile_export) { actions.onUserProfileExport(name) } }
        item { ActionRow(GR.string.user_profile_rename) { actions.onUserProfileRename(name) } }
        item { ActionRow(GR.string.user_profile_convert) { actions.onConvertUserProfile(name) } }
        item { ActionRow(GR.string.user_profile_delete) { actions.onUserProfileDelete(name) } }
    }
}

@Composable
fun GhostlockAboutScreen() {
    val uriHandler = LocalUriHandler.current

    GhostlockScaffold(title = stringResource(GR.string.about)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(GR.string.opensource_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.ghostlock_about_thanks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsJumpPageWidget(
                title = stringResource(R.string.ghostlock_about_original),
                description = GHOSTLOCK_PROJECT_URL,
                onClick = { uriHandler.openUri(GHOSTLOCK_PROJECT_URL) },
            )
        }
    }
}
