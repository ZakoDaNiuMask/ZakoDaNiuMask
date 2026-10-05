// SPDX-License-Identifier: GPL-3.0-or-later
// Portions adapted from Duck-Detector-Refactoring (Apache-2.0),
// https://github.com/eltavine/Duck-Detector-Refactoring

package com.zakodaniumask.manager.ui.screen.detection

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.bootloader.BootloaderReport
import com.zakodaniumask.manager.data.detection.DetectorStatus
import com.zakodaniumask.manager.data.tee.TeeReport
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.screen.LabelText
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.DetectorViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetectorPage(bottomPadding: Dp) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel: DetectorViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.root_detection_copied)

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
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.detector)) },
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
        if (!state.isReady && state.isScanning) {
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

        val suModel = state.su?.let { rememberRootDetectionModel(it) }

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
                    message = stringResource(R.string.root_detection_warning),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            suModel?.let { model ->
                item {
                    SectionTitle(stringResource(R.string.detector_su))
                    HeadlineCard(model)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    FactsBlock(model.facts)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                rootSection(R.string.root_detection_section_artifacts, model.artifactRows)
                rootSection(R.string.root_detection_section_context, model.contextRows)
                item {
                    SectionTitle(stringResource(R.string.root_detection_section_impact))
                    ImpactsSection(model.impacts)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                rootSection(R.string.root_detection_section_methods, model.methodRows)
                rootSection(R.string.root_detection_section_scan, model.scanRows)
            }

            state.bootloader?.let { bootloader ->
                item {
                    SectionTitle(stringResource(R.string.detector_bootloader))
                    BootloaderSection(bootloader)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            state.tee?.let { tee ->
                item {
                    SectionTitle(stringResource(R.string.detector_tee))
                    TeeSection(tee)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.scan() },
                        enabled = !state.isScanning,
                    ) {
                        Text(stringResource(R.string.root_detection_refresh))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.setText(AnnotatedString(state.toReportText()))
                            scope.launch { snackBarHost.showReplacingSnackbar(copiedText) }
                        },
                    ) {
                        Text(stringResource(R.string.root_detection_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun BootloaderSection(report: BootloaderReport) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(report.status.toRootStatus()),
                iconSize = 18.dp,
                title = stringResource(
                    when {
                        report.locked == true -> R.string.bootloader_verdict_locked
                        report.locked == false -> R.string.bootloader_verdict_unlocked
                        else -> R.string.bootloader_verdict_unknown
                    }
                ),
                description = report.consistencyDetail.orEmpty(),
                containerColor = statusContainer(report.status.toRootStatus()),
                onClick = null,
            )
        }
        item { ValueRow(stringResource(R.string.bootloader_row_verified_boot), report.verifiedBootState ?: unknown()) }
        item { ValueRow(stringResource(R.string.bootloader_row_device_locked), yesNo(report.locked)) }
        item { ValueRow(stringResource(R.string.bootloader_row_trust_root), report.trustRoot.name) }
        item { ValueRow(stringResource(R.string.bootloader_row_chain), yesNo(report.chainValid)) }
        report.osVersion?.let { item { ValueRow(stringResource(R.string.bootloader_row_os_version), it) } }
        report.osPatchLevel?.let { item { ValueRow(stringResource(R.string.bootloader_row_os_patch), it) } }
        report.properties.firstOrNull { it.value != null }?.let {
            item {
                ValueRow(
                    label = stringResource(R.string.bootloader_row_properties),
                    value = report.properties.count { p -> p.value != null }.toString(),
                    detail = report.properties
                        .filter { p -> p.value != null }
                        .joinToString("\n") { p -> "${p.name} = ${p.value}" },
                )
            }
        }
    }
}

@Composable
private fun TeeSection(report: TeeReport) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(report.status.toRootStatus()),
                iconSize = 18.dp,
                title = stringResource(
                    when {
                        report.status == DetectorStatus.ERROR -> R.string.tee_verdict_error
                        report.tier.name == "SOFTWARE" -> R.string.tee_verdict_software
                        report.trustRoot.name != "GOOGLE" && report.trustRoot.name != "GOOGLE_RKP" ->
                            R.string.tee_verdict_untrusted
                        else -> R.string.tee_verdict_ok
                    }
                ),
                description = report.error.orEmpty(),
                containerColor = statusContainer(report.status.toRootStatus()),
                onClick = null,
            )
        }
        item { ValueRow(stringResource(R.string.tee_row_tier), report.tier.name) }
        report.attestationTier?.let { item { ValueRow(stringResource(R.string.tee_row_attestation_tier), it.name) } }
        report.keymasterTier?.let { item { ValueRow(stringResource(R.string.tee_row_keymaster_tier), it.name) } }
        item { ValueRow(stringResource(R.string.tee_row_attestation_version), report.attestationVersion?.toString() ?: unknown()) }
        item { ValueRow(stringResource(R.string.tee_row_keymaster_version), report.keymasterVersion?.toString() ?: unknown()) }
        item { ValueRow(stringResource(R.string.tee_row_verified_boot), report.verifiedBootState ?: unknown()) }
        item { ValueRow(stringResource(R.string.tee_row_device_locked), yesNo(report.deviceLocked)) }
        item { ValueRow(stringResource(R.string.tee_row_challenge), yesNo(report.challengeVerified)) }
        item { ValueRow(stringResource(R.string.tee_row_trust_root), report.trustRoot.name) }
        item { ValueRow(stringResource(R.string.tee_row_chain), yesNo(report.chainValid)) }
        if (report.certificates.isNotEmpty()) {
            item {
                ValueRow(
                    label = stringResource(R.string.tee_row_certificates),
                    value = report.certificates.size.toString(),
                    detail = report.certificates.joinToString("\n") { "${it.slotLabel}: ${it.subject}" },
                )
            }
        }
    }
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    detail: String? = null,
) {
    val resolved = detail?.takeIf { it.isNotBlank() }
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = label,
        description = value,
        onClick = null,
        descriptionColumnContent = if (resolved != null) {
            {
                Text(
                    text = resolved,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun unknown(): String = stringResource(R.string.value_unknown)

@Composable
private fun yesNo(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.value_yes)
    false -> stringResource(R.string.value_no)
    null -> stringResource(R.string.value_unknown)
}

@Composable
private fun DetectorStatus.toRootStatus(): RootStatus = when (this) {
    DetectorStatus.DANGER -> RootStatus.DANGER
    DetectorStatus.ERROR -> RootStatus.ERROR
    DetectorStatus.SUPPORT -> RootStatus.SUPPORT
    DetectorStatus.INFO -> RootStatus.INFO
    DetectorStatus.CLEAR -> RootStatus.CLEAR
    DetectorStatus.UNKNOWN -> RootStatus.INFO
}

private fun LazyListScope.rootSection(@StringRes titleRes: Int, rows: List<RootRow>) {
    if (rows.isEmpty()) return
    item {
        SectionTitle(stringResource(titleRes))
    }
    lazySegmentColumn(rows, key = { _, row -> row.labelRes }) { _, row ->
        RootRowItem(row)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HeadlineCard(model: RootDetectionModel) {
    WarningCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        message = "${model.verdict}\n${model.summary}",
        shape = RoundedCornerShape(16.dp),
        color = statusContainer(model.status),
    )
    Text(
        text = model.subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun FactsBlock(facts: List<RootFact>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SegmentedColumn {
            facts.forEach { fact ->
                item {
                    SettingsBaseWidget(
                        iconPlaceholder = false,
                        title = stringResource(fact.labelRes),
                        description = fact.value,
                        onClick = null,
                        trailingContent = {
                            LabelText(
                                label = statusLabel(fact.status),
                                containerColor = statusContainer(fact.status),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootRowItem(row: RootRow) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = !row.detail.isNullOrBlank()
    SettingsBaseWidget(
        iconPlaceholder = false,
        title = stringResource(row.labelRes),
        description = row.value,
        onClick = if (hasDetail) {
            { expanded = !expanded }
        } else {
            null
        },
        descriptionColumnContent = {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                LabelText(
                    label = statusLabel(row.status),
                    containerColor = statusContainer(row.status),
                )
                if (expanded && hasDetail) {
                    Text(
                        text = row.detail.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (row.monospace) FontFamily.Monospace else FontFamily.Default
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        trailingContent = if (hasDetail) {
            {
                Icon(
                    imageVector = if (expanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore,
                    contentDescription = null,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ImpactsSection(impacts: List<RootImpact>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        impacts.forEach { impact ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = statusIcon(impact.status),
                    contentDescription = null,
                    tint = statusTint(impact.status),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = impact.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: RootStatus): String = stringResource(
    when (status) {
        RootStatus.DANGER -> R.string.root_detection_state_detected
        RootStatus.CLEAR -> R.string.root_detection_state_clean
        RootStatus.SUPPORT -> R.string.root_detection_state_partial
        RootStatus.ERROR -> R.string.root_detection_state_error
        RootStatus.INFO -> R.string.root_detection_state_info
    }
)

@Composable
private fun statusContainer(status: RootStatus): Color = when (status) {
    RootStatus.DANGER, RootStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
    RootStatus.CLEAR -> MaterialTheme.colorScheme.primaryContainer
    RootStatus.SUPPORT -> MaterialTheme.colorScheme.secondaryContainer
    RootStatus.INFO -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun statusTint(status: RootStatus): Color = when (status) {
    RootStatus.DANGER, RootStatus.ERROR -> MaterialTheme.colorScheme.error
    RootStatus.CLEAR -> MaterialTheme.colorScheme.primary
    RootStatus.SUPPORT -> MaterialTheme.colorScheme.secondary
    RootStatus.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusIcon(status: RootStatus) = when (status) {
    RootStatus.DANGER, RootStatus.ERROR -> Icons.TwoTone.Warning
    RootStatus.CLEAR -> Icons.TwoTone.CheckCircle
    RootStatus.SUPPORT, RootStatus.INFO -> Icons.TwoTone.Info
}
