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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.VisibilityOff
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.zakodaniumask.manager.data.kernel.KernelCheckReport
import com.zakodaniumask.manager.data.properties.SystemPropertiesReport
import com.zakodaniumask.manager.data.selinux.SelinuxMode
import com.zakodaniumask.manager.data.selinux.SelinuxReport
import com.zakodaniumask.manager.data.tee.TeeReport
import com.zakodaniumask.manager.domain.model.DetectorAction
import com.zakodaniumask.manager.domain.model.DetectorActionKind
import com.zakodaniumask.manager.domain.model.DetectorSection
import com.zakodaniumask.manager.ui.component.SwipeableSnackbarHost
import com.zakodaniumask.manager.ui.component.WarningCard
import com.zakodaniumask.manager.ui.component.settings.SegmentedColumn
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsJumpPageWidget
import com.zakodaniumask.manager.ui.component.settings.SettingsSwitchWidget
import com.zakodaniumask.manager.ui.component.settings.lazySegmentColumn
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route
import com.zakodaniumask.manager.ui.screen.LabelText
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.theme.blurSource
import com.zakodaniumask.manager.ui.util.LocalSnackbarHost
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.util.showReplacingSnackbar
import com.zakodaniumask.manager.ui.viewmodel.DetectorViewModel
import com.zakodaniumask.manager.ui.viewmodel.toReportText
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
    val navigator = LocalNavigator.current
    val copiedText = stringResource(R.string.root_detection_copied)

    val onQuickToggle: (DetectorActionKind, Boolean) -> Unit = { kind, enabled ->
        viewModel.onDetectorAction(kind, enabled)
    }
    val onQuickJump: (DetectorActionKind) -> Unit = {
        navigator.push(Route.SuSFSConfig)
    }

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
                quickSettings(
                    actions = state.actions[DetectorSection.SU].orEmpty(),
                    onToggle = onQuickToggle,
                    onJump = onQuickJump,
                )
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

            state.systemProperties?.let { properties ->
                item {
                    SectionTitle(stringResource(R.string.detector_system_properties))
                    SystemPropertiesSection(properties)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            state.kernelCheck?.let { kernel ->
                item {
                    SectionTitle(stringResource(R.string.detector_kernel_check))
                    KernelCheckSection(kernel)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                quickSettings(
                    actions = state.actions[DetectorSection.KERNEL_CHECK].orEmpty(),
                    onToggle = onQuickToggle,
                    onJump = onQuickJump,
                )
            }

            state.selinux?.let { selinux ->
                item {
                    SectionTitle(stringResource(R.string.detector_selinux))
                    SelinuxSection(selinux)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                quickSettings(
                    actions = state.actions[DetectorSection.SELINUX].orEmpty(),
                    onToggle = onQuickToggle,
                    onJump = onQuickJump,
                )
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
        val osVersion = report.osVersion
        if (osVersion != null) {
            item { ValueRow(stringResource(R.string.bootloader_row_os_version), osVersion) }
        }
        val osPatch = report.osPatchLevel
        if (osPatch != null) {
            item { ValueRow(stringResource(R.string.bootloader_row_os_patch), osPatch) }
        }
        val properties = report.properties.filter { it.value != null }
        if (properties.isNotEmpty()) {
            item {
                ValueRow(
                    label = stringResource(R.string.bootloader_row_properties),
                    value = properties.size.toString(),
                    detail = properties.joinToString("\n") { "${it.name} = ${it.value}" },
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
        val attestationTier = report.attestationTier
        if (attestationTier != null) {
            item { ValueRow(stringResource(R.string.tee_row_attestation_tier), attestationTier.name) }
        }
        val keymasterTier = report.keymasterTier
        if (keymasterTier != null) {
            item { ValueRow(stringResource(R.string.tee_row_keymaster_tier), keymasterTier.name) }
        }
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
private fun SystemPropertiesSection(report: SystemPropertiesReport) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(report.status.toRootStatus()),
                iconSize = 18.dp,
                title = stringResource(systemPropertiesVerdict(report)),
                description = report.suspiciousFingerprint?.let { pattern ->
                    stringResource(R.string.system_properties_row_fingerprint) + ": " + pattern
                },
                containerColor = statusContainer(report.status.toRootStatus()),
                onClick = null,
            )
        }
        item {
            ValueRow(
                label = stringResource(R.string.system_properties_row_observed),
                value = "${report.observedRuleCount}/${report.checkedRuleCount}",
            )
        }
        report.signals.forEach { signal ->
            item {
                ValueRow(
                    label = signal.property,
                    value = signal.value ?: unknown(),
                    detail = signal.description,
                )
            }
        }
    }
}

@Composable
private fun systemPropertiesVerdict(report: SystemPropertiesReport): Int = when {
    report.status == DetectorStatus.DANGER -> R.string.system_properties_verdict_danger
    report.status == DetectorStatus.INFO || report.status == DetectorStatus.UNKNOWN ->
        R.string.system_properties_verdict_unreadable
    else -> R.string.system_properties_verdict_ok
}

@Composable
private fun KernelCheckSection(report: KernelCheckReport) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(report.status.toRootStatus()),
                iconSize = 18.dp,
                title = stringResource(
                    when (report.status) {
                        DetectorStatus.DANGER -> R.string.kernel_verdict_danger
                        DetectorStatus.CLEAR -> R.string.kernel_verdict_ok
                        else -> R.string.kernel_verdict_partial
                    }
                ),
                description = report.error,
                containerColor = statusContainer(report.status.toRootStatus()),
                onClick = null,
            )
        }
        if (report.unameOutput.isNotBlank()) {
            item { ValueRow(stringResource(R.string.kernel_row_uname), report.unameOutput) }
        }
        if (report.procVersion.isNotBlank()) {
            item { ValueRow(stringResource(R.string.kernel_row_proc_version), report.procVersion) }
        }
        if (report.procCmdline.isNotBlank()) {
            item { ValueRow(stringResource(R.string.kernel_row_cmdline), report.procCmdline.take(160)) }
        }
        report.kptrRestrict?.let { kptr ->
            item { ValueRow(stringResource(R.string.kernel_row_kptr), kptr) }
        }
        report.findings.forEach { finding ->
            item {
                ValueRow(
                    label = finding.label,
                    value = finding.value,
                    detail = finding.detail,
                )
            }
        }
    }
}

@Composable
private fun SelinuxSection(report: SelinuxReport) {
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(report.status.toRootStatus()),
                iconSize = 18.dp,
                title = stringResource(selinuxVerdict(report)),
                description = report.error,
                containerColor = statusContainer(report.status.toRootStatus()),
                onClick = null,
            )
        }
        item { ValueRow(stringResource(R.string.selinux_row_mode), report.statusLabel) }
        val context = report.processContext
        if (!context.isNullOrBlank()) {
            item { ValueRow(stringResource(R.string.selinux_row_context), context) }
        }
        item { ValueRow(stringResource(R.string.selinux_row_paradox), yesNo(report.paradoxDetected)) }
        report.checks.forEach { check ->
            item { ValueRow(check.method, check.status, detail = check.detail) }
        }
    }
}

@Composable
private fun selinuxVerdict(report: SelinuxReport): Int = when {
    report.paradoxDetected -> R.string.selinux_verdict_paradox
    report.mode == SelinuxMode.PERMISSIVE -> R.string.selinux_verdict_permissive
    report.mode == SelinuxMode.DISABLED -> R.string.selinux_verdict_disabled
    report.mode == SelinuxMode.ENFORCING -> R.string.selinux_verdict_enforcing
    else -> R.string.selinux_verdict_unknown
}

private fun LazyListScope.quickSettings(
    actions: List<DetectorAction>,
    onToggle: (DetectorActionKind, Boolean) -> Unit,
    onJump: (DetectorActionKind) -> Unit,
) {
    if (actions.isEmpty()) return
    item {
        SectionTitle(stringResource(R.string.detector_quick_settings))
        SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            actions.forEach { action ->
                item {
                    QuickSettingRow(action = action, onToggle = onToggle, onJump = onJump)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun QuickSettingRow(
    action: DetectorAction,
    onToggle: (DetectorActionKind, Boolean) -> Unit,
    onJump: (DetectorActionKind) -> Unit,
) {
    val title = stringResource(actionTitle(action.kind))
    val description = stringResource(actionDescription(action.kind))
    val icon = actionIcon(action.kind)
    val enabled = action.enabled
    if (enabled == null) {
        SettingsJumpPageWidget(
            icon = icon,
            title = title,
            description = description,
            enabled = action.supported,
            onClick = { onJump(action.kind) },
        )
    } else {
        SettingsSwitchWidget(
            icon = icon,
            title = title,
            description = description,
            enabled = action.supported,
            checked = enabled,
            onCheckedChange = { onToggle(action.kind, it) },
        )
    }
}

private fun actionTitle(kind: DetectorActionKind): Int = when (kind) {
    DetectorActionKind.SELINUX_HIDE -> R.string.detector_action_selinux_hide
    DetectorActionKind.KERNEL_UMOUNT -> R.string.detector_action_kernel_umount
    DetectorActionKind.DEFAULT_UMOUNT_MODULES -> R.string.detector_action_default_umount
    DetectorActionKind.SU_ENABLED -> R.string.detector_action_su_enabled
    DetectorActionKind.SUSFS_ENABLED -> R.string.detector_action_susfs_enabled
    DetectorActionKind.SUSFS_AVC_LOG_SPOOFING -> R.string.detector_action_susfs_avc_log_spoofing
    DetectorActionKind.SUSFS_HIDE_SUS_MNTS -> R.string.detector_action_susfs_hide_sus_mnts
    DetectorActionKind.SUSFS_UNAME_SPOOF -> R.string.detector_action_susfs_uname_spoof
}

private fun actionDescription(kind: DetectorActionKind): Int = when (kind) {
    DetectorActionKind.SELINUX_HIDE -> R.string.detector_action_selinux_hide_desc
    DetectorActionKind.KERNEL_UMOUNT -> R.string.detector_action_kernel_umount_desc
    DetectorActionKind.DEFAULT_UMOUNT_MODULES -> R.string.detector_action_default_umount_desc
    DetectorActionKind.SU_ENABLED -> R.string.detector_action_su_enabled_desc
    DetectorActionKind.SUSFS_ENABLED -> R.string.detector_action_susfs_enabled_desc
    DetectorActionKind.SUSFS_AVC_LOG_SPOOFING -> R.string.detector_action_susfs_avc_log_spoofing_desc
    DetectorActionKind.SUSFS_HIDE_SUS_MNTS -> R.string.detector_action_susfs_hide_sus_mnts_desc
    DetectorActionKind.SUSFS_UNAME_SPOOF -> R.string.detector_action_susfs_uname_spoof_desc
}

private fun actionIcon(kind: DetectorActionKind): ImageVector = when (kind) {
    DetectorActionKind.SELINUX_HIDE -> Icons.TwoTone.Security
    DetectorActionKind.KERNEL_UMOUNT -> Icons.TwoTone.Code
    DetectorActionKind.DEFAULT_UMOUNT_MODULES -> Icons.TwoTone.Code
    DetectorActionKind.SU_ENABLED -> Icons.TwoTone.Security
    DetectorActionKind.SUSFS_ENABLED -> Icons.TwoTone.VisibilityOff
    DetectorActionKind.SUSFS_AVC_LOG_SPOOFING -> Icons.TwoTone.BugReport
    DetectorActionKind.SUSFS_HIDE_SUS_MNTS -> Icons.TwoTone.VisibilityOff
    DetectorActionKind.SUSFS_UNAME_SPOOF -> Icons.TwoTone.Computer
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
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            SettingsBaseWidget(
                icon = statusIcon(model.status),
                iconSize = 18.dp,
                title = model.verdict,
                description = model.summary,
                containerColor = statusContainer(model.status),
                onClick = null,
                trailingContent = {
                    LabelText(
                        label = statusLabel(model.status),
                        containerColor = statusContainer(model.status),
                    )
                },
            )
        }
    }
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
    SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        impacts.forEach { impact ->
            item {
                SettingsBaseWidget(
                    icon = statusIcon(impact.status),
                    iconSize = 18.dp,
                    title = impact.text,
                    containerColor = statusContainer(impact.status),
                    onClick = null,
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

private fun statusIcon(status: RootStatus) = when (status) {
    RootStatus.DANGER, RootStatus.ERROR -> Icons.TwoTone.Warning
    RootStatus.CLEAR -> Icons.TwoTone.CheckCircle
    RootStatus.SUPPORT, RootStatus.INFO -> Icons.TwoTone.Info
}
