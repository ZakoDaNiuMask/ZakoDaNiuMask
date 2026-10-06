package com.zakodaniumask.manager.ui.screen.kpm

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.domain.model.KpmModule
import com.zakodaniumask.manager.domain.usecase.GetKpmModuleInfoUseCase
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.KpmEvent
import com.zakodaniumask.manager.ui.viewmodel.KpmViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun KpmPage() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: KpmViewModel = koinViewModel()
    val getModuleInfo: GetKpmModuleInfoUseCase = koinInject()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var controlTarget by remember { mutableStateOf<KpmModule?>(null) }
    var controlArgs by remember { mutableStateOf("") }
    var infoTitle by remember { mutableStateOf<String?>(null) }
    var infoBody by remember { mutableStateOf("") }

    val loadSuccess = stringResource(R.string.kpm_load_success)
    val loadFailed = stringResource(R.string.kpm_load_failed)
    val unloadSuccess = stringResource(R.string.kpm_unload_success)
    val unloadFailed = stringResource(R.string.kpm_unload_failed)
    val controlResultMsg = stringResource(R.string.kpm_control_result)

    val pickModule = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = copyToCache(context, uri)
                if (path != null) viewModel.loadModule(path, null) else snackBarHost.showReplacingSnackbar(loadFailed)
            }
        }
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                KpmEvent.LoadSucceeded -> snackBarHost.showReplacingSnackbar(loadSuccess)
                KpmEvent.LoadFailed -> snackBarHost.showReplacingSnackbar(loadFailed)
                KpmEvent.UnloadSucceeded -> snackBarHost.showReplacingSnackbar(unloadSuccess)
                KpmEvent.UnloadFailed -> snackBarHost.showReplacingSnackbar(unloadFailed)
                is KpmEvent.ControlResult ->
                    snackBarHost.showReplacingSnackbar(controlResultMsg.format(event.code))
                KpmEvent.Refreshed -> Unit
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
                title = { Text(stringResource(R.string.kpm_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.kpm_refresh))
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
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
        ) {
            if (!uiState.supported) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.kpm_not_supported_summary),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.kpm_version),
                                description = uiState.version.ifBlank { stringResource(R.string.value_unknown) },
                                onClick = null,
                            )
                        }
                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.kpm_modules),
                                description = stringResource(R.string.kpm_module_count, uiState.modules.size),
                                onClick = null,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    SettingsJumpRow(
                        onClick = {
                            pickModule.launch("application/octet-stream")
                        },
                        title = stringResource(R.string.kpm_load_module),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.modules.isEmpty()) {
                    item {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = stringResource(R.string.kpm_no_modules),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                } else {
                    item {
                        SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                            uiState.modules.forEach { module ->
                                item {
                                    KpmModuleRow(
                                        module = module,
                                        onInfo = {
                                            scope.launch { infoBody = getModuleInfo(module.id) }
                                            infoTitle = module.name
                                        },
                                        onControl = {
                                            controlTarget = module
                                            controlArgs = ""
                                        },
                                        onUnload = {
                                            scope.launch {
                                                val result = confirmDialog.awaitConfirm(
                                                    title = context.getString(R.string.kpm_unload_confirm, module.name),
                                                    content = "",
                                                )
                                                if (result == ConfirmResult.Confirmed) {
                                                    viewModel.unloadModule(module.id)
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
    }

    controlTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { controlTarget = null },
            title = { Text(stringResource(R.string.kpm_control_title)) },
            text = {
                OutlinedTextField(
                    value = controlArgs,
                    onValueChange = { controlArgs = it },
                    label = { Text(stringResource(R.string.kpm_control_args)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.controlModule(target.id, controlArgs)
                    controlTarget = null
                }) {
                    Text(stringResource(R.string.kpm_control_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { controlTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    infoTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { infoTitle = null },
            title = { Text(title) },
            text = {
                Box(modifier = Modifier.height(240.dp)) {
                    LazyColumn {
                        item {
                            Text(
                                text = infoBody,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTitle = null }) {
                    Text(stringResource(R.string.kpm_close))
                }
            },
        )
    }
}

@Composable
private fun KpmModuleRow(
    module: KpmModule,
    onInfo: () -> Unit,
    onControl: () -> Unit,
    onUnload: () -> Unit,
) {
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = module.name,
        description = listOfNotNull(
            module.version.takeIf { it.isNotBlank() }?.let { "v$it" },
            module.author.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = null,
        descriptionColumnContent = {
            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.TwoTone.Info, contentDescription = stringResource(R.string.kpm_info))
                }
                IconButton(onClick = onControl) {
                    Icon(Icons.TwoTone.Code, contentDescription = stringResource(R.string.kpm_control))
                }
                IconButton(onClick = onUnload) {
                    Icon(
                        Icons.TwoTone.Delete,
                        contentDescription = stringResource(R.string.kpm_unload),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun SettingsJumpRow(
    title: String,
    onClick: () -> Unit,
) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Add,
                title = title,
                onClick = { onClick() },
            )
        }
    }
}

private suspend fun copyToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "module.kpm"
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}
