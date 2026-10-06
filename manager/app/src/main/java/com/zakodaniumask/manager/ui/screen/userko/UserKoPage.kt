// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.ui.screen.userko

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.domain.model.UserKoModule
import com.zakodaniumask.manager.domain.model.UserKoStages
import com.zakodaniumask.manager.ui.component.ConfirmResult
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.rememberConfirmDialog
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.UserKoEvent
import com.zakodaniumask.manager.ui.viewmodel.UserKoViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserKoPage() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: UserKoViewModel = koinViewModel()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var stageDialogVisible by remember { mutableStateOf(false) }
    val onlyKoMsg = stringResource(R.string.user_ko_only_ko)
    val unloadConfirmTemplate = stringResource(R.string.user_ko_unload_confirm)
    val deleteConfirmTemplate = stringResource(R.string.user_ko_delete_confirm)

    val pickModule = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = queryDisplayName(context, uri).orEmpty()
            if (!displayName.lowercase().endsWith(".ko")) {
                scope.launch { snackBarHost.showReplacingSnackbar(onlyKoMsg) }
            } else {
                viewModel.import(uri.toString(), displayName)
            }
        }
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UserKoEvent.Result -> {
                    if (event.message.isNotBlank()) {
                        snackBarHost.showReplacingSnackbar(event.message)
                    }
                }
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
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.user_ko_title)) },
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
            if (!uiState.rootAvailable) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.user_ko_root_required),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = stringResource(R.string.user_ko_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    item {
                        SettingsJumpPageWidget(
                            icon = Icons.TwoTone.PlayArrow,
                            title = stringResource(R.string.user_ko_stage),
                            description = stageLabel(uiState.stage),
                            enabled = uiState.rootAvailable,
                            onClick = { stageDialogVisible = true },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Add,
                            title = stringResource(R.string.user_ko_import),
                            enabled = uiState.rootAvailable,
                            onClick = { pickModule.launch(arrayOf("application/octet-stream", "*/*")) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.modules.isEmpty()) {
                item {
                    WarningCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        message = stringResource(R.string.user_ko_empty),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            } else {
                item {
                    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                        uiState.modules.forEach { module ->
                            item(key = "header_${module.id}") {
                                UserKoModuleRow(
                                    module = module,
                                    enabled = uiState.rootAvailable && !uiState.isRefreshing,
                                    onLoad = { viewModel.load(module.id) },
                                    onUnload = {
                                        scope.launch {
                                            val result = confirmDialog.awaitConfirm(
                                                title = unloadConfirmTemplate.format(module.name),
                                                content = "",
                                            )
                                            if (result == ConfirmResult.Confirmed) {
                                                viewModel.unload(module.id)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            val result = confirmDialog.awaitConfirm(
                                                title = deleteConfirmTemplate.format(module.name),
                                                content = "",
                                            )
                                            if (result == ConfirmResult.Confirmed) {
                                                viewModel.delete(module.id)
                                            }
                                        }
                                    },
                                )
                            }
                            item(key = "auto_${module.id}") {
                                SettingsSwitchWidget(
                                    iconPlaceholder = false,
                                    title = stringResource(R.string.user_ko_auto_load),
                                    description = stringResource(R.string.user_ko_auto_load_summary),
                                    enabled = uiState.rootAvailable,
                                    checked = module.autoLoad,
                                    onCheckedChange = { viewModel.setAutoLoad(module.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (stageDialogVisible) {
        val postFs = stringResource(R.string.user_ko_stage_post_fs_data)
        val service = stringResource(R.string.user_ko_stage_service)
        AlertDialog(
            onDismissRequest = { stageDialogVisible = false },
            title = { Text(stringResource(R.string.user_ko_stage)) },
            text = {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            title = postFs,
                            selected = uiState.stage == UserKoStages.POST_FS_DATA,
                            onClick = {
                                stageDialogVisible = false
                                viewModel.setStage(UserKoStages.POST_FS_DATA)
                            },
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            title = service,
                            selected = uiState.stage == UserKoStages.SERVICE,
                            onClick = {
                                stageDialogVisible = false
                                viewModel.setStage(UserKoStages.SERVICE)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { stageDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UserKoModuleRow(
    module: UserKoModule,
    enabled: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    val loadedLabel = stringResource(
        if (module.loaded) R.string.user_ko_loaded else R.string.user_ko_not_loaded
    )
    val description = listOf(
        module.moduleName.ifBlank { stringResource(R.string.value_unknown) },
        loadedLabel,
    ).joinToString(" · ")

    SettingsBaseWidget(
        iconPlaceholder = false,
        title = module.name,
        description = description,
        enabled = enabled,
        onClick = null,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLoad, enabled = enabled) {
                    Icon(Icons.TwoTone.PlayArrow, contentDescription = stringResource(R.string.user_ko_load))
                }
                IconButton(onClick = onUnload, enabled = enabled) {
                    Icon(
                        Icons.TwoTone.PowerSettingsNew,
                        contentDescription = stringResource(R.string.user_ko_unload),
                    )
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        Icons.TwoTone.Delete,
                        contentDescription = stringResource(R.string.user_ko_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun stageLabel(stage: String): String = stringResource(
    if (stage == UserKoStages.SERVICE) R.string.user_ko_stage_service
    else R.string.user_ko_stage_post_fs_data
)

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()
