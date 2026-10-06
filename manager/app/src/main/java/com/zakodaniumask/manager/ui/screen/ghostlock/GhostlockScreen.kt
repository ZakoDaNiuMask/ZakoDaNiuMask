// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.ghostlock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ghostlock.domain.model.RootlessStatus
import com.zakodaniumask.manager.ghostlock.ui.DialogType
import com.zakodaniumask.manager.ghostlock.ui.DocumentRequest
import com.zakodaniumask.manager.ghostlock.ui.GhostlockActions
import com.zakodaniumask.manager.ghostlock.ui.GhostlockEffect
import com.zakodaniumask.manager.ghostlock.ui.GhostlockUiState
import com.zakodaniumask.manager.ghostlock.ui.GhostlockViewModel
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.zakodaniumask.manager.ghostlock.R as GR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GhostlockScreen() {
    val viewModel: GhostlockViewModel = koinViewModel()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()

    var pendingRequest by remember { mutableStateOf<DocumentRequest?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val request = pendingRequest
        pendingRequest = null
        if (uri != null && request != null) viewModel.onDocumentResult(request, uri.toString())
    }
    val documentsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val request = pendingRequest
        pendingRequest = null
        if (uris.isNotEmpty() && request != null) {
            viewModel.onDocumentsResult(request, uris.map(Uri::toString))
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> viewModel.onDebugExportLocationPicked(uri?.path) }
    val profileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? -> viewModel.onExportProfileDocumentPicked(uri?.toString()) }

    LaunchedEffect(Unit) { viewModel.initialize() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GhostlockEffect.PickDocument -> {
                    pendingRequest = effect.request
                    val mime = when (effect.request) {
                        DocumentRequest.ImportOffsetsHocon ->
                            arrayOf("text/plain", "application/octet-stream")

                        DocumentRequest.ImportOffsetsJson ->
                            arrayOf("application/json", "text/plain", "application/octet-stream")

                        else -> arrayOf("*/*")
                    }
                    if (effect.request == DocumentRequest.ImportOffsetsHocon ||
                        effect.request == DocumentRequest.ImportOffsetsJson
                    ) {
                        documentsPicker.launch(mime)
                    } else {
                        documentPicker.launch(mime)
                    }
                }

                GhostlockEffect.PickDebugFolder -> folderPicker.launch(null)

                is GhostlockEffect.CreateProfileDocument ->
                    profileCreator.launch(effect.suggestedName)

                is GhostlockEffect.Share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(effect.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }

                is GhostlockEffect.Toast -> scope.launch {
                    snackBarHost.showReplacingSnackbar(context.getString(effect.resourceId))
                }

                is GhostlockEffect.Clipboard -> {
                    runCatching {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("ghostlock-log", effect.text))
                    }
                    scope.launch {
                        snackBarHost.showReplacingSnackbar(context.getString(GR.string.action_copy))
                    }
                }

                is GhostlockEffect.KeepScreenAwake ->
                    activity?.window?.let { window ->
                        if (effect.enabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                GhostlockEffect.OpenRootless -> navigator.push(Route.Axeron)
            }
        }
    }

    val actions = remember(viewModel) { ghostlockActions(viewModel) }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    var showCpuDialog by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.ghostlock_title)) },
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
            ) {
                when {
                    state.builtinScreenVisible -> {
                        item { BackRow(stringResource(GR.string.action_back)) { actions.onCloseBuiltinProfiles() } }
                        item { SectionTitle(stringResource(GR.string.load_config_title)) }
                        items(state.builtinProfiles) { release ->
                            SelectRow(release, release == state.activeBuiltinProfile) {
                                actions.onSelectBuiltinProfile(release)
                                actions.onCloseBuiltinProfiles()
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
                                actions.onCloseBuiltinProfiles()
                            }
                        }
                    }

                    state.userProfileDetail != null -> {
                        item { BackRow(stringResource(GR.string.action_back)) { actions.onCloseUserProfileDetail() } }
                        item { SectionTitle(state.userProfileDetail ?: "") }
                        item { ActionRow(GR.string.user_profile_load) { actions.onLoadUserProfile(state.userProfileDetail!!) } }
                        item { ActionRow(GR.string.user_profile_unload) { actions.onUnloadUserProfile() } }
                        item { ActionRow(GR.string.user_profile_edit) { actions.onEditUserProfile(state.userProfileDetail!!) } }
                        item { ActionRow(GR.string.user_profile_export) { actions.onUserProfileExport(state.userProfileDetail!!) } }
                        item { ActionRow(GR.string.user_profile_rename) { actions.onUserProfileRename(state.userProfileDetail!!) } }
                        item { ActionRow(GR.string.user_profile_convert) { actions.onConvertUserProfile(state.userProfileDetail!!) } }
                        item { ActionRow(GR.string.user_profile_delete) { actions.onUserProfileDelete(state.userProfileDetail!!) } }
                    }

                    state.parametersVisible -> {
                        item { BackRow(stringResource(GR.string.action_back)) { actions.onCloseParameters() } }
                        item { SectionTitle(stringResource(GR.string.parameters)) }
                        items(state.profileOverrideRoots) { node ->
                            if (!node.isGroup) {
                                OverrideRow(
                                    label = node.name,
                                    value = state.profileOverrideEditing[node.path] ?: node.value?.toString().orEmpty(),
                                    onValueChange = { actions.onProfileOverrideChanged(node.path, it) },
                                )
                            }
                        }
                    }

                    state.profileOverrideVisible || state.advancedOverrideVisible -> {
                        item {
                            BackRow(stringResource(GR.string.action_back)) {
                                if (state.advancedOverrideVisible) actions.onCloseAdvancedOverrides()
                                else actions.onCloseProfileOverrides()
                            }
                        }
                        item { SectionTitle(stringResource(GR.string.debug_profile_override)) }
                        items(state.profileOverrideRoots) { node ->
                            if (!node.isGroup) {
                                OverrideRow(
                                    label = node.name,
                                    value = state.profileOverrideEditing[node.path] ?: node.value?.toString().orEmpty(),
                                    onValueChange = { actions.onProfileOverrideChanged(node.path, it) },
                                )
                            }
                        }
                    }

                    state.advancedScreenVisible -> {
                        item { BackRow(stringResource(GR.string.action_back)) { actions.onCloseAdvanced() } }
                        item { SectionTitle(stringResource(GR.string.advanced_settings)) }
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
                        item { ActionRow(GR.string.debug_profile_override) { actions.onOpenProfileOverrides() } }
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

                    else -> {
                        item { StatusCard(state) }
                        item { Spacer(Modifier.height(12.dp)) }
                        item { ControlCard(state, actions, onPickCpu = { showCpuDialog = true }) }
                        item { Spacer(Modifier.height(12.dp)) }
                        item { ActionsCard(actions) }
                        item { Spacer(Modifier.height(12.dp)) }
                        item { ProfileCard(state, actions) }
                        item { Spacer(Modifier.height(12.dp)) }
                        item { LogCard(state, actions) }
                    }
                }
            }
        }
    }

    if (showCpuDialog) {
        ListDialog(
            title = stringResource(GR.string.cpu_pair_label),
            items = state.cpuPairLabels,
            selectedIndex = state.cpuPairIndex,
            onDismiss = { showCpuDialog = false },
            onSelect = { index ->
                showCpuDialog = false
                actions.onCpuPairSelected(index)
            },
        )
    }

    if (state.dialogVisible) DynamicDialog(state, actions)

    if (state.overwriteDialogVisible) {
        AlertDialog(
            onDismissRequest = { actions.onOverwriteDismiss() },
            title = { Text(stringResource(GR.string.overwrite_title)) },
            text = { Text(state.overwriteMessage) },
            confirmButton = {
                TextButton(onClick = { actions.onOverwriteConfirm() }) {
                    Text(stringResource(GR.string.overwrite_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onOverwriteDismiss() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )
    }

    if (state.userProfileDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { actions.onUserProfileDeleteDismiss() },
            title = { Text(stringResource(GR.string.user_profile_delete)) },
            text = { Text(state.userProfileDeleteTarget ?: "") },
            confirmButton = {
                TextButton(onClick = { actions.onUserProfileDeleteConfirm() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onUserProfileDeleteDismiss() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )
    }

    if (state.aboutVisible) {
        AlertDialog(
            onDismissRequest = { actions.onCloseAbout() },
            title = { Text(stringResource(GR.string.about)) },
            text = { Text(stringResource(GR.string.opensource_info)) },
            confirmButton = {
                TextButton(onClick = { actions.onCloseAbout() }) {
                    Text(stringResource(GR.string.about_source_code))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onCloseAbout() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )
    }
}

private fun ghostlockActions(viewModel: GhostlockViewModel): GhostlockActions =
    object : GhostlockActions {
        override fun onRun() = viewModel.onRun()
            override fun onProfileInvalid() = viewModel.onProfileInvalid()
            override fun onStatusClick() = viewModel.onStatusClick()
            override fun onCloseExecutionSheet() = viewModel.onCloseExecutionSheet()
            override fun onCopyLogs() = viewModel.copyLogs()
            override fun onImportOffsetsHocon() = viewModel.importOffsetsHocon()
            override fun onImportOffsetsJson() = viewModel.importOffsetsJson()
            override fun onDocumentsResult(request: DocumentRequest, uris: List<String>) =
                viewModel.onDocumentsResult(request, uris)

            override fun onParseOta() = viewModel.promptParseUrl()
            override fun onParseImage() = viewModel.parseOffsets()
            override fun onCpuPairSelected(index: Int) = viewModel.selectCpuPair(index)
            override fun onSafeModeChanged(enabled: Boolean) = viewModel.toggleSafeMode(enabled)
            override fun onForceAttackTestChanged(enabled: Boolean) =
                viewModel.toggleForceAttackTest(enabled)

            override fun onRootlessChanged(enabled: Boolean) = viewModel.toggleShizuku(enabled)
            override fun onDialogItemSelected(index: Int) = viewModel.onDialogItemSelected(index)
            override fun onDialogInputChange(value: String) = viewModel.onDialogInputChange(value)
            override fun onDialogConfirm(value: String) = viewModel.onDialogConfirm(value)
            override fun onDialogDismiss() = viewModel.onDialogDismiss()
            override fun onDialogDismissFinished() = viewModel.onDialogDismissFinished()
            override fun onOverwriteConfirm() = viewModel.onOverwriteConfirm()
            override fun onOverwriteDismiss() = viewModel.onOverwriteDismiss()
            override fun onExecutionFieldChanged(path: String, value: String) =
                viewModel.updateExecutionField(path, value)

            override fun onRouteChanged(index: Int) = viewModel.onRouteChanged(index)
            override fun onFallbackChanged(index: Int) = viewModel.onFallbackChanged(index)
            override fun onExportProfile() = viewModel.onExportProfile()
            override fun onSaveProfileEdits() = viewModel.onSaveProfileEdits()
            override fun onSaveProfileAs() = viewModel.onSaveProfileAs()
            override fun onExportProfileEdits() = viewModel.onExportProfileEdits()
            override fun onRevertProfileEdits() = viewModel.onRevertProfileEdits()
            override fun onOpenAdvanced() = viewModel.onOpenAdvanced()
            override fun onCloseAdvanced() = viewModel.onCloseAdvanced()
            override fun onShowAbout() = viewModel.onShowAbout()
            override fun onCloseAbout() = viewModel.onCloseAbout()
            override fun onDebugExportChanged(enabled: Boolean) = viewModel.onDebugExportChanged(enabled)
            override fun onDebugExportLocationPick() = viewModel.onDebugExportLocationPick()
            override fun onDebugKernelLogChanged(enabled: Boolean) =
                viewModel.onDebugKernelLogChanged(enabled)

            override fun onOpenParameters() = viewModel.onOpenParameters()
            override fun onCloseParameters() = viewModel.onCloseParameters()
            override fun onOpenLoadConfig() = viewModel.onOpenLoadConfig()
            override fun onCloseLoadConfig() = viewModel.onCloseLoadConfig()
            override fun onOpenUserProfileDetail(name: String) =
                viewModel.onOpenUserProfileDetail(name)

            override fun onCloseUserProfileDetail() = viewModel.onCloseUserProfileDetail()
            override fun onLoadUserProfile(name: String) = viewModel.onLoadUserProfile(name)
            override fun onUnloadUserProfile() = viewModel.onUnloadUserProfile()
            override fun onEditUserProfile(name: String) = viewModel.onEditUserProfile(name)
            override fun onUserProfileRename(name: String) = viewModel.onUserProfileRename(name)
            override fun onUserProfileExport(name: String) = viewModel.onUserProfileExport(name)
            override fun onConvertUserProfile(name: String) = viewModel.onConvertUserProfile(name)
            override fun onUserProfileDelete(name: String) = viewModel.onUserProfileDelete(name)
            override fun onUserProfileDeleteConfirm() = viewModel.onUserProfileDeleteConfirm()
            override fun onUserProfileDeleteDismiss() = viewModel.onUserProfileDeleteDismiss()
            override fun onOpenBuiltinProfiles() = viewModel.onOpenBuiltinProfiles()
            override fun onCloseBuiltinProfiles() = viewModel.onCloseBuiltinProfiles()
            override fun onSelectBuiltinProfile(release: String?) =
                viewModel.onSelectBuiltinProfile(release)

            override fun onOpenProfileOverrides() = viewModel.onOpenProfileOverrides()
            override fun onCloseProfileOverrides() = viewModel.onCloseProfileOverrides()
            override fun onOpenAdvancedOverrides() = viewModel.onOpenAdvancedOverrides()
            override fun onCloseAdvancedOverrides() = viewModel.onCloseAdvancedOverrides()
            override fun onProfileOverrideChanged(path: String, value: String) =
                viewModel.onProfileOverrideChanged(path, value)
    }

@Composable
private fun StatusCard(state: GhostlockUiState) {
    val status = when (state.rootlessStatus) {
        RootlessStatus.READY -> stringResource(GR.string.shizuku_status_ready)
        RootlessStatus.ACTIVATION_REQUIRED -> stringResource(GR.string.shizuku_status_permission_required)
        RootlessStatus.NOT_RUNNING -> stringResource(GR.string.shizuku_status_not_running)
        RootlessStatus.NOT_REQUIRED -> stringResource(GR.string.shizuku_summary)
    }
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.PlayArrow,
                iconSize = 18.dp,
                title = state.kernelRelease.ifEmpty { stringResource(GR.string.kernel_label) },
                description = listOf(state.deviceName, state.socName, status)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                onClick = null,
            )
        }
    }
}

@Composable
private fun ControlCard(
    state: GhostlockUiState,
    actions: GhostlockActions,
    onPickCpu: () -> Unit,
) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(GR.string.shizuku_label),
                description = stringResource(GR.string.shizuku_summary),
                checked = state.rootlessEnabled,
                onCheckedChange = actions::onRootlessChanged,
            )
        }
        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(GR.string.safe_mode_label),
                description = stringResource(GR.string.safe_mode_summary),
                checked = state.safeModeEnabled,
                onCheckedChange = actions::onSafeModeChanged,
            )
        }
        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(GR.string.force_attack_test_label),
                description = stringResource(GR.string.force_attack_test_summary),
                checked = state.forceAttackTestEnabled,
                onCheckedChange = actions::onForceAttackTestChanged,
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(GR.string.cpu_pair_label),
                description = state.cpuPairLabels.getOrNull(state.cpuPairIndex)
                    ?: stringResource(GR.string.cpu_pair_custom),
                onClick = { onPickCpu() },
            )
        }
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(GR.string.action_run),
                enabled = !state.running,
                onClick = { actions.onRun() },
            )
        }
    }
}

@Composable
private fun ActionsCard(actions: GhostlockActions) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.advanced_settings),
                description = stringResource(GR.string.advanced_settings_summary),
                onClick = { actions.onOpenAdvanced() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.parameters),
                description = stringResource(GR.string.parameters_summary),
                onClick = { actions.onOpenParameters() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.debug_export_log),
                description = stringResource(GR.string.debug_export_log_summary),
                onClick = { actions.onOpenParameters() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.action_import_offsets_conf),
                onClick = { actions.onImportOffsetsHocon() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.action_import_offsets_json),
                onClick = { actions.onImportOffsetsJson() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.action_parse_ota),
                onClick = { actions.onParseOta() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.action_parse),
                onClick = { actions.onParseImage() },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.about),
                description = stringResource(GR.string.about_summary),
                onClick = { actions.onShowAbout() },
            )
        }
    }
}

@Composable
private fun ProfileCard(state: GhostlockUiState, actions: GhostlockActions) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.builtin_profile_label),
                description = state.activeBuiltinProfile,
                onClick = { actions.onOpenBuiltinProfiles() },
            )
        }
        state.userProfiles.forEach { profile ->
            item(key = "user_${profile.name}") {
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.BugReport,
                    title = profile.name,
                    description = profile.releases.joinToString(", "),
                    onClick = { actions.onOpenUserProfileDetail(profile.name) },
                )
            }
        }
    }
}

@Composable
private fun LogCard(state: GhostlockUiState, actions: GhostlockActions) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(GR.string.log_title),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = { actions.onCopyLogs() }) {
                Text(stringResource(GR.string.action_copy))
            }
        }
        Text(
            text = state.logLines.joinToString("\n") { it.text },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DynamicDialog(state: GhostlockUiState, actions: GhostlockActions) {
    val title = if (state.dialogTitleRes != 0) stringResource(state.dialogTitleRes) else ""
    val message = when {
        state.dialogMessage.isNotEmpty() -> state.dialogMessage
        state.dialogMessageRes != 0 -> stringResource(state.dialogMessageRes)
        else -> ""
    }
    when (state.dialogType) {
        DialogType.LIST -> AlertDialog(
            onDismissRequest = { actions.onDialogDismiss() },
            title = { Text(title) },
            text = {
                LazyColumn {
                    if (state.dialogItems.isNotEmpty()) {
                        itemsIndexed(state.dialogItems) { index, item ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { actions.onDialogItemSelected(index) },
                            ) { Text(item) }
                        }
                    } else {
                        itemsIndexed(state.dialogItemResIds) { index, resId ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { actions.onDialogItemSelected(index) },
                            ) { Text(stringResource(resId)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actions.onDialogDismiss() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )

        DialogType.INPUT -> AlertDialog(
            onDismissRequest = { actions.onDialogDismiss() },
            title = { Text(title) },
            text = {
                Column {
                    if (message.isNotEmpty()) Text(message)
                    OutlinedTextField(
                        value = state.dialogInput,
                        onValueChange = { actions.onDialogInputChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actions.onDialogConfirm(state.dialogInput) }) {
                    Text(stringResource(state.dialogConfirmLabelRes))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onDialogDismiss() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )

        DialogType.CONFIRM -> AlertDialog(
            onDismissRequest = { actions.onDialogDismiss() },
            title = { Text(title) },
            text = { if (message.isNotEmpty()) Text(message) },
            confirmButton = {
                TextButton(onClick = { actions.onDialogConfirm(state.dialogInput) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { actions.onDialogDismiss() }) {
                    Text(stringResource(GR.string.cancel))
                }
            },
        )

        DialogType.NOTICE -> AlertDialog(
            onDismissRequest = { actions.onDialogDismiss() },
            title = { Text(title) },
            text = { if (message.isNotEmpty()) Text(message) },
            confirmButton = {
                TextButton(onClick = { actions.onDialogDismiss() }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )

        DialogType.NONE -> Unit
    }
}

@Composable
private fun ListDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                itemsIndexed(items) { index, item ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(index) },
                    ) {
                        Text(
                            text = item,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(GR.string.cancel)) }
        },
    )
}

@Composable
private fun BackRow(label: String, onClick: () -> Unit) {
    SettingsJumpPageWidget(
        title = label,
        onClick = { onClick() },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingsBaseWidget(
        title = label,
        selected = selected,
        onClick = { onClick() },
    )
}

@Composable
private fun ActionRow(resId: Int, onClick: () -> Unit) {
    SettingsBaseWidget(
        title = stringResource(resId),
        onClick = { onClick() },
    )
}

@Composable
private fun OverrideRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
