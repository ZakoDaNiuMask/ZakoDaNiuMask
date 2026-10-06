// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.ghostlock

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.zakodaniumask.manager.ghostlock.domain.model.ProfileConfig
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
import com.zakodaniumask.manager.ui.navigation.Navigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.zakodaniumask.manager.ghostlock.R as GR

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GhostlockScreen() {
    val viewModel: GhostlockViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }
    val navigator = LocalNavigator.current

    var showCpuDialog by remember { mutableStateOf(false) }
    var showRouteDialog by remember { mutableStateOf(false) }
    var showFallbackDialog by remember { mutableStateOf(false) }

    GhostlockScaffold(title = stringResource(R.string.ghostlock_title)) {
        item { StatusCard(state) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ControlCard(state, actions, onPickCpu = { showCpuDialog = true }) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ActionsCard(actions, navigator) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ProfileCard(state, navigator) }
        item { Spacer(Modifier.height(12.dp)) }
        item { LogCard(state, actions) }
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

/**
 * Shared scaffold for every GhostLock destination: top bar, scroll container, effect handling and
 * the global overlays (dialogs + execution sheet).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GhostlockScaffold(
    title: String,
    content: LazyListScope.() -> Unit,
) {
    val viewModel: GhostlockViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { ghostlockActions(viewModel) }
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current

    GhostlockEffects(viewModel)
    LaunchedEffect(Unit) { viewModel.initialize() }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
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
                title = { Text(title) },
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
                content = content,
            )
        }
    }

    GhostlockOverlays(state, actions)
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun GhostlockEffects(viewModel: GhostlockViewModel) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostlockOverlays(state: GhostlockUiState, actions: GhostlockActions) {
    if (state.executionSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                if (state.executionSheetDismissible) actions.onCloseExecutionSheet()
            },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(GR.string.log_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row {
                        TextButton(onClick = { actions.onCopyLogs() }) {
                            Text(stringResource(GR.string.action_copy))
                        }
                        TextButton(
                            enabled = state.executionSheetDismissible,
                            onClick = { actions.onCloseExecutionSheet() },
                        ) {
                            Text(stringResource(GR.string.action_close))
                        }
                    }
                }
                Text(
                    text = state.logLines.joinToString("\n") { it.text },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    style = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
}

internal fun ghostlockActions(viewModel: GhostlockViewModel): GhostlockActions =
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
        override fun onOpenUserProfileDetail(name: String) = viewModel.onOpenUserProfileDetail(name)

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
private fun ActionsCard(actions: GhostlockActions, navigator: Navigator) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.advanced_settings),
                description = stringResource(GR.string.advanced_settings_summary),
                onClick = { navigator.push(Route.GhostlockAdvanced) },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.parameters),
                description = stringResource(GR.string.parameters_summary),
                onClick = { navigator.push(Route.GhostlockParameters) },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.debug_export_log),
                description = stringResource(GR.string.debug_export_log_summary),
                onClick = { navigator.push(Route.GhostlockAdvanced) },
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
                onClick = { navigator.push(Route.GhostlockAbout) },
            )
        }
    }
}

@Composable
private fun ProfileCard(state: GhostlockUiState, navigator: Navigator) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.BugReport,
                title = stringResource(GR.string.builtin_profile_label),
                description = state.activeBuiltinProfile,
                onClick = { navigator.push(Route.GhostlockBuiltin) },
            )
        }
        state.userProfiles.forEach { profile ->
            item(key = "user_${profile.name}") {
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.BugReport,
                    title = profile.name,
                    description = profile.releases.joinToString(", "),
                    onClick = { navigator.push(Route.GhostlockUserProfile(profile.name)) },
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
internal fun DynamicDialog(state: GhostlockUiState, actions: GhostlockActions) {
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
internal fun ListDialog(
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
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingsBaseWidget(
        title = label,
        selected = selected,
        onClick = { onClick() },
    )
}

@Composable
internal fun ActionRow(resId: Int, onClick: () -> Unit) {
    SettingsBaseWidget(
        title = stringResource(resId),
        onClick = { onClick() },
    )
}

@Composable
internal fun OverrideRow(label: String, value: String, onValueChange: (String) -> Unit) {
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
