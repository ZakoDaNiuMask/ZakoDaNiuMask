package com.zakodaniumask.manager.ui.screen.bootscript

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.zakodaniumask.manager.domain.model.BootScriptSettings
import com.zakodaniumask.manager.domain.model.BootScriptStage
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsChooseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsTextFieldWidget
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.BootScriptEvent
import com.zakodaniumask.manager.ui.viewmodel.BootScriptViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BootScriptScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: BootScriptViewModel = koinViewModel()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val contentState = rememberTextFieldState()
    var enabled by remember { mutableStateOf(false) }
    var stageIndex by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.boot_script_saved)
    val failedMessage = stringResource(R.string.boot_script_save_failed)
    val stageLabels = BootScriptStage.entries.map { stageLabel(it) }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !initialized) {
            enabled = uiState.enabled
            stageIndex = uiState.stage.ordinal
            contentState.setTextAndPlaceCursorAtEnd(uiState.content)
            initialized = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                BootScriptEvent.Saved -> snackBarHost.showReplacingSnackbar(savedMessage)
                BootScriptEvent.Failed -> snackBarHost.showReplacingSnackbar(failedMessage)
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
                title = { Text(stringResource(R.string.boot_script_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
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
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            }

            if (!uiState.isRootAvailable) {
                item {
                    WarningCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        message = stringResource(R.string.boot_script_root_required),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    )
                }
            } else {
                item {
                    WarningCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        message = stringResource(R.string.boot_script_summary),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
            }

            item {
                SegmentedColumn {
                    item {
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.PowerSettingsNew,
                            title = stringResource(R.string.boot_script_enable),
                            description = stringResource(R.string.boot_script_enable_desc),
                            enabled = uiState.isRootAvailable,
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                    }
                    item {
                        SettingsChooseWidget(
                            icon = Icons.TwoTone.Schedule,
                            title = stringResource(R.string.boot_script_stage),
                            items = stageLabels,
                            selectedIndex = stageIndex,
                            enabled = uiState.isRootAvailable,
                            onSelectedIndexChange = { stageIndex = it },
                        )
                    }
                }
            }

            item {
                SegmentedColumn {
                    item {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            SettingsTextFieldWidget(
                                modifier = Modifier.fillMaxWidth(),
                                renderBackgroundBlur = false,
                                state = contentState,
                                title = stringResource(R.string.boot_script_content),
                                enabled = uiState.isRootAvailable,
                                lineLimits = TextFieldLineLimits.MultiLine(
                                    minHeightInLines = 8,
                                    maxHeightInLines = 18,
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isRootAvailable && !uiState.isSaving,
                        onClick = {
                            viewModel.save(
                                enabled = enabled,
                                stage = BootScriptStage.entries[stageIndex],
                                content = contentState.text.toString(),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.boot_script_save))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isRootAvailable && !uiState.isSaving,
                        onClick = {
                            contentState.setTextAndPlaceCursorAtEnd(
                                BootScriptSettings.DEFAULT_BOOT_SCRIPT
                            )
                        },
                    ) {
                     Text(stringResource(R.string.boot_script_reset))
                     }
                 }
             }

            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
private fun stageLabel(stage: BootScriptStage): String = when (stage) {
    BootScriptStage.POST_FS_DATA -> stringResource(R.string.boot_script_stage_post_fs_data)
    BootScriptStage.SERVICE -> stringResource(R.string.boot_script_stage_service)
    BootScriptStage.BOOT_COMPLETED -> stringResource(R.string.boot_script_stage_boot_completed)
}
