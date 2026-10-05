package com.zakodaniumask.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakodaniumask.manager.data.bootloader.BootloaderDetector
import com.zakodaniumask.manager.data.bootloader.BootloaderReport
import com.zakodaniumask.manager.data.detection.DetectorStatus
import com.zakodaniumask.manager.data.detection.RootProbeClientRepository
import com.zakodaniumask.manager.data.detection.SuReport
import com.zakodaniumask.manager.data.detection.SuStage
import com.zakodaniumask.manager.data.detection.severityRank
import com.zakodaniumask.manager.data.kernel.KernelCheckDetector
import com.zakodaniumask.manager.data.kernel.KernelCheckReport
import com.zakodaniumask.manager.data.properties.SystemPropertiesDetector
import com.zakodaniumask.manager.data.properties.SystemPropertiesReport
import com.zakodaniumask.manager.data.selinux.SelinuxDetector
import com.zakodaniumask.manager.data.selinux.SelinuxReport
import com.zakodaniumask.manager.data.tee.TeeDetector
import com.zakodaniumask.manager.data.tee.TeeReport
import com.zakodaniumask.manager.domain.model.DetectorAction
import com.zakodaniumask.manager.domain.model.DetectorActionKind
import com.zakodaniumask.manager.domain.model.DetectorActionStates
import com.zakodaniumask.manager.domain.model.DetectorSection
import com.zakodaniumask.manager.domain.usecase.ApplyDetectorActionUseCase
import com.zakodaniumask.manager.domain.usecase.GetDetectorActionStatesUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetectorState(
    val su: SuReport? = null,
    val bootloader: BootloaderReport? = null,
    val tee: TeeReport? = null,
    val systemProperties: SystemPropertiesReport? = null,
    val kernelCheck: KernelCheckReport? = null,
    val selinux: SelinuxReport? = null,
    val actions: Map<DetectorSection, List<DetectorAction>> = emptyMap(),
    val isScanning: Boolean = false,
) {
    val isReady: Boolean
        get() = su != null || bootloader != null || tee != null ||
            systemProperties != null || kernelCheck != null || selinux != null

    /** Worst status across every detector; drives the combined home preview. */
    val overall: DetectorStatus
        get() {
            val statuses = buildList {
                su?.let { add(suStatus(it)) }
                bootloader?.let { add(it.status) }
                tee?.let { add(it.status) }
                systemProperties?.let { add(it.status) }
                kernelCheck?.let { add(it.status) }
                selinux?.let { add(it.status) }
            }
            return statuses.minByOrNull { it.severityRank() } ?: DetectorStatus.INFO
        }

    companion object {
        fun suStatus(report: SuReport): DetectorStatus = when {
            report.stage == SuStage.FAILED -> DetectorStatus.ERROR
            report.stage == SuStage.LOADING -> DetectorStatus.INFO
            report.hasRootIndicators -> DetectorStatus.DANGER
            else -> DetectorStatus.CLEAR
        }
    }
}

class DetectorViewModel(
    private val suRepository: RootProbeClientRepository,
    private val bootloaderDetector: BootloaderDetector,
    private val teeDetector: TeeDetector,
    private val systemPropertiesDetector: SystemPropertiesDetector,
    private val kernelCheckDetector: KernelCheckDetector,
    private val selinuxDetector: SelinuxDetector,
    private val getDetectorActionStates: GetDetectorActionStatesUseCase,
    private val applyDetectorAction: ApplyDetectorActionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DetectorState())
    val state: StateFlow<DetectorState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        if (_state.value.isScanning) return
        _state.updateScanning(true)
        viewModelScope.launch {
            val su = async { runCatching { suRepository.scan() }.getOrNull() }
            val bootloader = async { runCatching { bootloaderDetector.scan() }.getOrNull() }
            val tee = async { runCatching { teeDetector.scan() }.getOrNull() }
            val properties = async { runCatching { systemPropertiesDetector.scan() }.getOrNull() }
            val kernel = async { runCatching { kernelCheckDetector.scan() }.getOrNull() }
            val selinux = async { runCatching { selinuxDetector.scan() }.getOrNull() }
            val actions = async { runCatching { getDetectorActionStates() }.getOrDefault(DetectorActionStates()) }

            val actionStates = actions.await()
            val next = DetectorState(
                su = su.await(),
                bootloader = bootloader.await(),
                tee = tee.await(),
                systemProperties = properties.await(),
                kernelCheck = kernel.await(),
                selinux = selinux.await(),
                isScanning = false,
            )
            _state.value = next.copy(actions = buildRemediations(next, actionStates))
        }
    }

    fun onDetectorAction(kind: DetectorActionKind, enabled: Boolean) {
        viewModelScope.launch {
            applyDetectorAction(kind, enabled)
            val actions = runCatching { getDetectorActionStates() }.getOrDefault(DetectorActionStates())
            _state.value = _state.value.copy(actions = buildRemediations(_state.value, actions))
        }
    }

    private fun buildRemediations(
        state: DetectorState,
        states: DetectorActionStates,
    ): Map<DetectorSection, List<DetectorAction>> {
        val result = linkedMapOf<DetectorSection, MutableList<DetectorAction>>()
        fun add(section: DetectorSection, action: DetectorAction) {
            result.getOrPut(section) { mutableListOf() }.add(action)
        }

        if (state.selinux?.status == DetectorStatus.DANGER) {
            if (states.kernelActionsAvailable) {
                add(
                    DetectorSection.SELINUX,
                    DetectorAction(
                        DetectorActionKind.SELINUX_HIDE,
                        supported = true,
                        enabled = states.selinuxHideEnabled,
                    ),
                )
            }
            if (states.susfsAvailable) {
                add(
                    DetectorSection.SELINUX,
                    DetectorAction(
                        DetectorActionKind.SUSFS_AVC_LOG_SPOOFING,
                        supported = true,
                        enabled = states.susfsAvcLogSpoofing,
                    ),
                )
            }
        }

        if (state.kernelCheck?.hasHardIndicators == true && states.susfsAvailable) {
            add(
                DetectorSection.KERNEL_CHECK,
                DetectorAction(
                    DetectorActionKind.SUSFS_ENABLED,
                    supported = true,
                    enabled = states.susfsEnabled,
                ),
            )
            add(
                DetectorSection.KERNEL_CHECK,
                DetectorAction(DetectorActionKind.SUSFS_UNAME_SPOOF, supported = true),
            )
        }

        if (state.su?.let { DetectorState.suStatus(it) } == DetectorStatus.DANGER &&
            states.susfsAvailable
        ) {
            add(
                DetectorSection.SU,
                DetectorAction(
                    DetectorActionKind.SUSFS_ENABLED,
                    supported = true,
                    enabled = states.susfsEnabled,
                ),
            )
        }

        return result
    }

    private fun MutableStateFlow<DetectorState>.updateScanning(scanning: Boolean) {
        value = value.copy(isScanning = scanning)
    }
}

fun DetectorState.toReportText(): String = buildString {
    appendLine("ZakoDaNiuMask detector report")
    su?.let { report ->
        appendLine()
        appendLine("SU: stage=${report.stage} rootIndicators=${report.hasRootIndicators}")
        report.suBinaries.forEach { appendLine("  su: $it") }
        report.daemons.forEach { appendLine("  daemon: ${it.name} ${it.path}") }
        appendLine("  selfContext=${report.selfContext}")
    }
    bootloader?.let { report ->
        appendLine()
        appendLine(
            "Bootloader: status=${report.status} locked=${report.locked} " +
                "verifiedBootState=${report.verifiedBootState} trustRoot=${report.trustRoot} " +
                "chainValid=${report.chainValid}"
        )
        report.properties.filter { it.value != null }
            .forEach { appendLine("  ${it.name} = ${it.value}") }
    }
    tee?.let { report ->
        appendLine()
        appendLine(
            "TEE: status=${report.status} tier=${report.tier} trustRoot=${report.trustRoot} " +
                "chainValid=${report.chainValid} challengeVerified=${report.challengeVerified}"
        )
    }
    systemProperties?.let { report ->
        appendLine()
        appendLine(
            "SystemProperties: status=${report.status} " +
                "rules=${report.observedRuleCount}/${report.checkedRuleCount} " +
                "suspiciousFingerprint=${report.suspiciousFingerprint}"
        )
        report.signals.forEach { signal ->
            appendLine("  ${signal.severity} ${signal.property} = ${signal.value} (${signal.description})")
        }
    }
    kernelCheck?.let { report ->
        appendLine()
        appendLine(
            "KernelCheck: status=${report.status} kptrRestrict=${report.kptrRestrict} " +
                "uname=${report.unameOutput}"
        )
        report.findings.forEach { finding ->
            appendLine("  ${finding.severity} ${finding.label}: ${finding.value}")
        }
    }
    selinux?.let { report ->
        appendLine()
        appendLine(
            "SELinux: status=${report.status} mode=${report.mode} " +
                "label=${report.statusLabel} paradox=${report.paradoxDetected} " +
                "context=${report.processContext}"
        )
        report.checks.forEach { check ->
            appendLine("  ${check.method}: ${check.status}")
        }
    }
}
