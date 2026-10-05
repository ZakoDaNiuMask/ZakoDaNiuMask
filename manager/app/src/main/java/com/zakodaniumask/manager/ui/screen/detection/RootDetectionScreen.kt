// SPDX-License-Identifier: GPL-3.0-or-later
// Portions ported from Duck-Detector-Refactoring (Apache-2.0),
// https://github.com/eltavine/Duck-Detector-Refactoring

package com.zakodaniumask.manager.ui.screen.detection

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.detection.SuMethodOutcome
import com.zakodaniumask.manager.data.detection.SuReport
import com.zakodaniumask.manager.data.detection.SuRepository
import com.zakodaniumask.manager.data.detection.SuStage
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.screen.LabelText
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RootDetectionScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val repository: SuRepository = koinInject()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var report by remember { mutableStateOf<SuReport?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    fun scan() {
        scope.launch {
            isScanning = true
            report = repository.scan()
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        scan()
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
                title = { Text(stringResource(R.string.root_detection)) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
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
        if (isScanning && report == null) {
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

        val data = report
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
            item {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = stringResource(R.string.root_detection_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (data != null) {
                item {
                    VerdictCard(data)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    SegmentedBlock {
                        FactRow(
                            label = stringResource(R.string.root_detection_fact_artifacts),
                            value = if (data.suBinaries.isEmpty()) {
                                stringResource(R.string.root_detection_none)
                            } else {
                                data.suBinaries.size.toString()
                            },
                            tone = if (data.suBinaries.isEmpty()) Tone.Clean else Tone.Danger,
                        )
                        FactRow(
                            label = stringResource(R.string.root_detection_fact_daemons),
                            value = if (data.daemons.isEmpty()) {
                                stringResource(R.string.root_detection_none)
                            } else {
                                data.daemons.joinToString("/") { it.name }
                            },
                            tone = if (data.daemons.isEmpty()) Tone.Clean else Tone.Danger,
                        )
                        FactRow(
                            label = stringResource(R.string.root_detection_fact_context),
                            value = when {
                                data.selfContextAbnormal ->
                                    stringResource(R.string.root_detection_context_abnormal)
                                data.selfContext.isNotBlank() ->
                                    stringResource(R.string.root_detection_context_normal)
                                else -> stringResource(R.string.root_detection_unknown)
                            },
                            tone = when {
                                data.selfContextAbnormal -> Tone.Danger
                                data.selfContext.isNotBlank() -> Tone.Clean
                                else -> Tone.Support
                            },
                        )
                        FactRow(
                            label = stringResource(R.string.root_detection_fact_processes),
                            value = if (!data.nativeAvailable) {
                                stringResource(R.string.root_detection_na)
                            } else {
                                data.suspiciousProcesses.size.toString()
                            },
                            tone = when {
                                !data.nativeAvailable -> Tone.Support
                                data.suspiciousProcesses.isEmpty() -> Tone.Clean
                                else -> Tone.Danger
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                lazySegmentColumn(report?.methods.orEmpty(), key = { _, it -> it.label }) { _, method ->
                    SettingsBaseWidget(
                        title = methodLabel(method.label),
                        description = method.summary,
                        trailingContent = {
                            LabelText(
                                label = outcomeLabel(method.outcome),
                                containerColor = outcomeColor(method.outcome),
                            )
                        },
                    )
                }

                if (data.suBinaries.isNotEmpty() || data.daemons.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SegmentedBlock {
                            data.suBinaries.forEach { path ->
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    title = path,
                                    onClick = null,
                                )
                            }
                            data.daemons.forEach { daemon ->
                                SettingsBaseWidget(
                                    iconPlaceholder = false,
                                    title = "${daemon.name}: ${daemon.path}",
                                    onClick = null,
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SegmentedBlock {
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Refresh,
                            title = stringResource(R.string.root_detection_refresh),
                            onClick = { scan() },
                        )
                    }
                }
            }
        }
    }
}

private enum class Tone { Clean, Danger, Support }

@Composable
private fun VerdictCard(data: SuReport) {
    val (textRes, tone) = when (data.stage) {
        SuStage.FAILED -> R.string.root_detection_verdict_failed to Tone.Danger
        SuStage.LOADING -> R.string.root_detection_scanning to Tone.Support
        SuStage.READY -> when {
            data.hasRootIndicators -> R.string.root_detection_verdict_detected to Tone.Danger
            data.unobservablePathCount > 0 || !data.nativeAvailable ->
                R.string.root_detection_verdict_support to Tone.Support
            else -> R.string.root_detection_verdict_clean to Tone.Clean
        }
    }
    WarningCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        message = stringResource(textRes),
        shape = RoundedCornerShape(16.dp),
        color = when (tone) {
            Tone.Danger -> MaterialTheme.colorScheme.errorContainer
            Tone.Support -> MaterialTheme.colorScheme.secondaryContainer
            Tone.Clean -> MaterialTheme.colorScheme.primaryContainer
        },
    )
}

@Composable
private fun SegmentedBlock(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        com.zakodaniumask.manager.ui.component.settings.SegmentedColumn {
            item { content() }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, tone: Tone) {
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = label,
        description = value,
        onClick = null,
        trailingContent = {
            LabelText(
                label = when (tone) {
                    Tone.Danger -> stringResource(R.string.root_detection_state_detected)
                    Tone.Support -> stringResource(R.string.root_detection_state_partial)
                    Tone.Clean -> stringResource(R.string.root_detection_state_clean)
                },
                containerColor = when (tone) {
                    Tone.Danger -> MaterialTheme.colorScheme.errorContainer
                    Tone.Support -> MaterialTheme.colorScheme.secondaryContainer
                    Tone.Clean -> MaterialTheme.colorScheme.primaryContainer
                },
            )
        },
    )
}

@Composable
private fun methodLabel(label: String): String = when (label) {
    "daemonScan" -> stringResource(R.string.root_detection_method_daemon)
    "fileScan" -> stringResource(R.string.root_detection_method_file)
    "nativeSyscall" -> stringResource(R.string.root_detection_method_syscall)
    "nativeLibrary" -> stringResource(R.string.root_detection_method_library)
    else -> label
}

@Composable
private fun outcomeLabel(outcome: SuMethodOutcome): String = when (outcome) {
    SuMethodOutcome.DETECTED -> stringResource(R.string.root_detection_state_detected)
    SuMethodOutcome.SUPPORT -> stringResource(R.string.root_detection_state_partial)
    SuMethodOutcome.CLEAN -> stringResource(R.string.root_detection_state_clean)
}

@Composable
private fun outcomeColor(outcome: SuMethodOutcome): Color = when (outcome) {
    SuMethodOutcome.DETECTED -> MaterialTheme.colorScheme.errorContainer
    SuMethodOutcome.SUPPORT -> MaterialTheme.colorScheme.secondaryContainer
    SuMethodOutcome.CLEAN -> MaterialTheme.colorScheme.primaryContainer
}
