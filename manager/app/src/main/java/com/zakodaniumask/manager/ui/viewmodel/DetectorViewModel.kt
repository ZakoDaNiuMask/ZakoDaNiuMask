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
import com.zakodaniumask.manager.data.tee.TeeDetector
import com.zakodaniumask.manager.data.tee.TeeReport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetectorState(
    val su: SuReport? = null,
    val bootloader: BootloaderReport? = null,
    val tee: TeeReport? = null,
    val isScanning: Boolean = false,
) {
    val isReady: Boolean get() = su != null || bootloader != null || tee != null

    /** Worst status across every detector; drives the combined home preview. */
    val overall: DetectorStatus
        get() {
            val statuses = buildList {
                su?.let { add(suStatus(it)) }
                bootloader?.let { add(it.status) }
                tee?.let { add(it.status) }
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
            _state.value = DetectorState(
                su = su.await(),
                bootloader = bootloader.await(),
                tee = tee.await(),
                isScanning = false,
            )
        }
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
}
