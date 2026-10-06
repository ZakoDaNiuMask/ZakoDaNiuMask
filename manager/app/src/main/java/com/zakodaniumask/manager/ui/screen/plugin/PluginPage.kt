package com.zakodaniumask.manager.ui.screen.plugin

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.domain.model.PluginInfo
import com.zakodaniumask.manager.domain.usecase.GetPluginConfigUseCase
import com.zakodaniumask.manager.domain.usecase.SetPluginConfigUseCase
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.PluginEvent
import com.zakodaniumask.manager.ui.viewmodel.PluginViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PluginPage(bottomPadding: Dp) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: PluginViewModel = koinViewModel()
    val getPluginConfig: GetPluginConfigUseCase = koinInject()
    val setPluginConfig: SetPluginConfigUseCase = koinInject()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var configTarget by remember { mutableStateOf<PluginInfo?>(null) }
    var configValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val installedMsg = stringResource(R.string.plugin_install_success)
    val installFailedMsg = stringResource(R.string.plugin_install_failed)
    val uninstalledMsg = stringResource(R.string.plugin_uninstall_success)
    val uninstallFailedMsg = stringResource(R.string.plugin_uninstall_failed)
    val toggleFailedMsg = stringResource(R.string.plugin_toggle_failed)
    val actionSuccessMsg = stringResource(R.string.plugin_action_success)
    val actionFailedMsg = stringResource(R.string.plugin_action_failed)
    val uninstallConfirmTemplate = stringResource(R.string.plugin_uninstall_confirm)
    val configSavedMsg = stringResource(R.string.plugin_config_saved)

    val pickPlugin = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = copyToCache(context, uri)
                if (path != null) viewModel.install(path) else snackBarHost.showReplacingSnackbar(installFailedMsg)
            }
        }
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                PluginEvent.Installed -> snackBarHost.showReplacingSnackbar(installedMsg)
                PluginEvent.InstallFailed -> snackBarHost.showReplacingSnackbar(installFailedMsg)
                PluginEvent.Uninstalled -> snackBarHost.showReplacingSnackbar(uninstalledMsg)
                PluginEvent.UninstallFailed -> snackBarHost.showReplacingSnackbar(uninstallFailedMsg)
                PluginEvent.ToggleFailed -> snackBarHost.showReplacingSnackbar(toggleFailedMsg)
                is PluginEvent.ActionResult ->
                    snackBarHost.showReplacingSnackbar(if (event.ok) actionSuccessMsg else actionFailedMsg)
            }
        }
    }

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
                title = { Text(stringResource(R.string.plugin_title)) },
                actions = {
                    IconButton(onClick = { navigator.push(Route.OnlinePlugin) }) {
                        Icon(Icons.TwoTone.Cloud, contentDescription = stringResource(R.string.online_plugin_title))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.plugin_refresh))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (themeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickPlugin.launch("application/zip") }) {
                Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.plugin_install))
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 5.dp,
                bottom = paddingValues.calculateBottomPadding() + bottomPadding + 12.dp,
            ),
        ) {
            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = stringResource(R.string.plugin_subtitle),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.plugins.isEmpty()) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.plugin_empty_hint),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        uiState.plugins.forEach { plugin ->
                            item {
                                PluginRow(
                                    plugin = plugin,
                                    onToggle = { viewModel.setEnabled(plugin.id, it) },
                                    onAction = {
                                        val function = plugin.quickAction?.function
                                        if (function != null) {
                                            viewModel.runCallback(plugin.id, function)
                                        } else {
                                            viewModel.runAction(plugin.id)
                                        }
                                    },
                                    onConfig = {
                                        scope.launch {
                                            configValues = plugin.config.associate { field ->
                                                field.key to getPluginConfig(plugin.id, field.key)
                                            }
                                            configTarget = plugin
                                        }
                                    },
                                    onLog = {
                                        navigator.push(Route.PluginLog(plugin.id, plugin.name))
                                    },
                                    onUninstall = {
                                        scope.launch {
                                            val result = confirmDialog.awaitConfirm(
                                                title = uninstallConfirmTemplate.format(plugin.name),
                                                content = "",
                                            )
                                            if (result == ConfirmResult.Confirmed) {
                                                viewModel.uninstall(plugin.id)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    configTarget?.let { plugin ->
        AlertDialog(
            onDismissRequest = { configTarget = null },
            title = { Text(stringResource(R.string.plugin_config_title, plugin.name)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    plugin.config.forEach { field ->
                        OutlinedTextField(
                            value = configValues[field.key].orEmpty(),
                            onValueChange = { value ->
                                configValues = configValues + (field.key to value)
                            },
                            label = { Text(field.label.ifBlank { field.key }) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = plugin
                    val values = configValues
                    configTarget = null
                    scope.launch {
                        target.config.forEach { field ->
                            val value = values[field.key]
                            if (value != null) setPluginConfig(target.id, field.key, value)
                        }
                        snackBarHost.showReplacingSnackbar(configSavedMsg)
                    }
                }) {
                    Text(stringResource(R.string.plugin_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { configTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PluginRow(
    plugin: PluginInfo,
    onToggle: (Boolean) -> Unit,
    onAction: () -> Unit,
    onConfig: () -> Unit,
    onLog: () -> Unit,
    onUninstall: () -> Unit,
) {
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = plugin.name,
        description = listOfNotNull(
            plugin.version.takeIf { it.isNotBlank() }?.let { "v$it" },
            plugin.author.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = null,
        descriptionColumnContent = {
            if (plugin.description.isNotBlank()) {
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plugin.quickAction != null || plugin.hasAction) {
                    IconButton(onClick = onAction) {
                        Icon(Icons.TwoTone.PlayArrow, contentDescription = stringResource(R.string.plugin_action))
                    }
                }
                if (plugin.config.isNotEmpty()) {
                    IconButton(onClick = onConfig) {
                        Icon(Icons.TwoTone.Tune, contentDescription = stringResource(R.string.plugin_config))
                    }
                }
                IconButton(onClick = onLog) {
                    Icon(Icons.TwoTone.Info, contentDescription = stringResource(R.string.plugin_log_view))
                }
                IconButton(onClick = onUninstall) {
                    Icon(
                        Icons.TwoTone.Delete,
                        contentDescription = stringResource(R.string.plugin_uninstall),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Switch(checked = plugin.enabled, onCheckedChange = onToggle)
            }
        },
    )
}

private suspend fun copyToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "plugin.zip"
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}
