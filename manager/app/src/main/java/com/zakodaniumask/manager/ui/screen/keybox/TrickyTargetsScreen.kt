// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.keybox

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.trickystore.PackageEntry
import com.zakodaniumask.manager.data.trickystore.PolicyField
import com.zakodaniumask.manager.data.trickystore.TargetMode
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsTextFieldWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.TrickyStoreEvent
import com.zakodaniumask.manager.ui.viewmodel.TrickyStoreViewModel
import androidx.compose.foundation.text.input.TextFieldState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrickyTargetsScreen(viewModel: TrickyStoreViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val savedMsg = stringResource(R.string.keybox_ts_saved)
    val restartMsg = stringResource(R.string.keybox_ts_restart_required)
    val failedMsg = stringResource(R.string.keybox_ts_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrickyStoreEvent.Saved ->
                    snackBarHost.showReplacingSnackbar(if (event.restartRequired) restartMsg else savedMsg)
                is TrickyStoreEvent.Failed ->
                    snackBarHost.showReplacingSnackbar("$failedMsg: ${event.detail}")
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val searchState = remember { TextFieldState(uiState.search) }
    var modeDialogFor by remember { mutableStateOf<PackageEntry?>(null) }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_ts_targets_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = null)
                    }
                },
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
        if (uiState.loading && uiState.status == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        val schema = uiState.status?.schema
        val query = searchState.text.toString()
        LazyColumn(
            modifier = Modifier.fillMaxSize().blurSource().nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item { Spacer(Modifier.height(paddingValues.calculateTopPadding())) }
            item { KeystoreBackendCard(uiState = uiState) }
            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsTextFieldWidget(
                            state = searchState,
                            title = stringResource(R.string.keybox_ts_search),
                            useLabelAsPlaceholder = true,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { viewModel.selectAll(visiblePackages(uiState, query).map { it.packageName }) }) {
                        Text(stringResource(R.string.keybox_ts_select_all))
                    }
                    TextButton(onClick = { viewModel.deselectAll() }) {
                        Text(stringResource(R.string.keybox_ts_deselect_all))
                    }
                }
            }
            items(visiblePackages(uiState, query), key = { it.packageName }) { entry ->
                PackageRow(
                    entry = entry,
                    supportsMode = schema?.supportsAppMode == true,
                    onToggle = { viewModel.toggleTarget(entry.packageName) },
                    onTune = { modeDialogFor = entry },
                )
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !uiState.saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.keybox_ts_save))
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 12.dp)) }
        }
    }

    val dialogEntry = modeDialogFor
    if (dialogEntry != null) {
        ModeDialog(
            entry = dialogEntry,
            supportsMode = uiState.status?.schema?.supportsAppMode == true,
            supportsPerApp = uiState.status?.schema?.supportsPerAppPolicy == true,
            perAppFields = uiState.status?.schema?.defaultPolicy.orEmpty(),
            currentPolicy = uiState.perAppPolicy[dialogEntry.packageName].orEmpty(),
            onMode = { viewModel.setMode(dialogEntry.packageName, it) },
            onPolicy = { key, value -> viewModel.setPerAppPolicy(dialogEntry.packageName, key, value) },
            onDismiss = { modeDialogFor = null },
        )
    }
}

private fun visiblePackages(
    state: com.zakodaniumask.manager.ui.viewmodel.TrickyStoreUiState,
    rawQuery: String,
): List<PackageEntry> {
    val query = rawQuery.trim().lowercase()
    return state.packages.filter { entry ->
        state.targets.any { it.packageName == entry.packageName } ||
            query.isEmpty() ||
            entry.label.lowercase().contains(query) ||
            entry.packageName.lowercase().contains(query)
    }
}

@Composable
private fun PackageRow(
    entry: PackageEntry,
    supportsMode: Boolean,
    onToggle: () -> Unit,
    onTune: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = entry.selected, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (entry.selected && supportsMode && entry.mode != TargetMode.AUTO) {
            Text(
                text = entry.mode.marker,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(onClick = onTune, enabled = entry.selected) {
            Icon(Icons.TwoTone.Tune, contentDescription = null)
        }
    }
}

@Composable
private fun ModeDialog(
    entry: PackageEntry,
    supportsMode: Boolean,
    supportsPerApp: Boolean,
    perAppFields: List<PolicyField>,
    currentPolicy: Map<String, String>,
    onMode: (TargetMode) -> Unit,
    onPolicy: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(entry.label) },
        text = {
            Column {
                if (supportsMode) {
                    TargetMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = entry.mode == mode, onClick = { onMode(mode) })
                            Text(
                                when (mode) {
                                    TargetMode.AUTO -> stringResource(R.string.keybox_ts_mode_auto)
                                    TargetMode.GENERATE -> stringResource(R.string.keybox_ts_mode_generate)
                                    TargetMode.HACK -> stringResource(R.string.keybox_ts_mode_hack)
                                }
                            )
                        }
                    }
                }
                if (supportsPerApp) {
                    perAppFields.forEach { field ->
                        PerAppPolicyField(
                            field = field,
                            initial = currentPolicy[field.key].orEmpty(),
                            onChange = onPolicy,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PerAppPolicyField(field: PolicyField, initial: String, onChange: (String, String) -> Unit) {
    var value by remember(field.key, initial) { mutableStateOf(initial) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(field.label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(field.key, it)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { field.placeholder?.let { Text(it) } },
        )
    }
}

@Composable
private fun KeystoreBackendCard(uiState: com.zakodaniumask.manager.ui.viewmodel.TrickyStoreUiState) {
    val status = uiState.status
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                title = stringResource(R.string.keybox_ts_backend),
                description = status?.active?.let { "${it.backend.identity} · ${it.name ?: it.moduleId} · ${it.version ?: "?"}" }
                    ?: stringResource(R.string.keybox_ts_no_backend),
            )
        }
        status?.keybox?.let { keybox ->
            item {
                SettingsBaseWidget(
                    title = stringResource(R.string.keybox_ts_keybox),
                    description = "${keybox.path} · ${if (keybox.exists) "${keybox.size} B" else stringResource(R.string.keybox_ts_missing)}",
                )
            }
        }
        status?.configError?.let { error ->
            item { SettingsBaseWidget(title = stringResource(R.string.keybox_ts_config_error), description = error) }
        }
    }
}
