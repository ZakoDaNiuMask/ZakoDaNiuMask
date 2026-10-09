// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.keybox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.CloudDownload
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.trickystore.KeyboxProvider
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.KeyboxEvent
import com.zakodaniumask.manager.ui.viewmodel.KeyboxWorkbenchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyboxWorkbenchScreen(viewModel: KeyboxWorkbenchViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    val installedMsg = stringResource(R.string.keybox_wb_installed)
    val failedMsg = stringResource(R.string.keybox_wb_failed)
    val copiedMsg = stringResource(R.string.keybox_wb_exported)

    var editing by remember { mutableStateOf<KeyboxProvider?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is KeyboxEvent.Installed -> snackBarHost.showReplacingSnackbar("$installedMsg: ${event.path}")
                is KeyboxEvent.Failed -> snackBarHost.showReplacingSnackbar("$failedMsg: ${event.detail}")
                is KeyboxEvent.Exported -> {
                    copyToClipboard(context, event.json)
                    snackBarHost.showReplacingSnackbar(copiedMsg)
                }
            }
        }
    }

    val xmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            if (content != null) viewModel.installLocal(content)
        }
    }
    val providerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            if (content != null) viewModel.importProviders(content)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_wb_title)) },
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
                uiState.status?.keybox?.let { keybox ->
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        item {
                            SettingsBaseWidget(
                                title = stringResource(R.string.keybox_ts_keybox),
                                description = "${keybox.path} · ${if (keybox.exists) "${keybox.size} B" else stringResource(R.string.keybox_ts_missing)}",
                            )
                        }
                    }
                }
            }
            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        Button(
                            onClick = { viewModel.installAosp() },
                            enabled = !uiState.working,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(stringResource(R.string.keybox_wb_aosp)) }
                    }
                    item {
                        Button(
                            onClick = { viewModel.generate() },
                            enabled = !uiState.working,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(stringResource(R.string.keybox_wb_generate)) }
                    }
                    item {
                        Button(
                            onClick = { xmlPicker.launch(arrayOf("*/*")) },
                            enabled = !uiState.working,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(stringResource(R.string.keybox_wb_local)) }
                    }
                }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.keybox_wb_providers),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    uiState.providers.forEach { provider ->
                        item(key = provider.name) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(provider.url, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.fetchProvider(provider) }, enabled = !uiState.working) {
                                    Icon(Icons.TwoTone.CloudDownload, contentDescription = null)
                                }
                                IconButton(onClick = { editing = provider; showEditor = true }) {
                                    Icon(Icons.TwoTone.Edit, contentDescription = null)
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = null; showEditor = true }) {
                                Icon(Icons.TwoTone.Add, contentDescription = null)
                                Text(stringResource(R.string.keybox_wb_add_provider))
                            }
                            TextButton(onClick = { providerPicker.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.keybox_wb_import))
                            }
                            TextButton(onClick = { viewModel.exportProviders() }) {
                                Text(stringResource(R.string.keybox_wb_export))
                            }
                            TextButton(onClick = { viewModel.resetProviders() }) {
                                Text(stringResource(R.string.keybox_wb_reset))
                            }
                        }
                    }
                }
            }
            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.CloudDownload,
                            title = stringResource(R.string.keybox_wb_repo),
                            description = stringResource(R.string.keybox_wb_repo_summary),
                            onClick = { navigator.push(Route.KeyboxRepo) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 12.dp)) }
        }
    }

    if (showEditor) {
        ProviderDialog(
            initial = editing,
            onSave = { provider ->
                val list = uiState.providers.filterNot { it.name == editing?.name }.toMutableList()
                list += provider
                viewModel.saveProviders(list)
                showEditor = false
            },
            onDelete = editing?.let {
                {
                    viewModel.saveProviders(uiState.providers.filterNot { provider -> provider.name == it.name })
                    showEditor = false
                }
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun ProviderDialog(
    initial: KeyboxProvider?,
    onSave: (KeyboxProvider) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var decode by remember { mutableStateOf(initial?.decode.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSave(KeyboxProvider(name.trim(), url.trim(), decode.trim())) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text(stringResource(R.string.keybox_ts_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    IconButton(onClick = it) { Icon(Icons.TwoTone.Delete, contentDescription = null) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        title = { Text(stringResource(R.string.keybox_wb_provider_title)) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.keybox_wb_provider_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.keybox_wb_provider_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = decode, onValueChange = { decode = it }, label = { Text(stringResource(R.string.keybox_wb_provider_decode)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("keybox-providers", text))
}
