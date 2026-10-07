// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.detection

import com.zakodaniumask.manager.data.bootloader.BootloaderDetector
import com.zakodaniumask.manager.data.kernel.KernelCheckDetector
import com.zakodaniumask.manager.data.properties.SystemPropertiesDetector
import com.zakodaniumask.manager.data.selinux.SelinuxDetector
import com.zakodaniumask.manager.data.tee.TeeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Runs the root-detection suite and renders a text report. Shared by the UI
 * (DetectorPage) and the in-process manager MCP server so the agent and the
 * screen see the same data.
 */
class DetectorRepository(
    private val suRepository: RootProbeClientRepository,
    private val bootloaderDetector: BootloaderDetector,
    private val teeDetector: TeeDetector,
    private val systemPropertiesDetector: SystemPropertiesDetector,
    private val kernelCheckDetector: KernelCheckDetector,
    private val selinuxDetector: SelinuxDetector,
) {
    suspend fun scan(): String = withContext(Dispatchers.IO) {
        val (su, bootloader, tee, properties, kernel, selinux) = coroutineScope {
            val su = async { runCatching { suRepository.scan() }.getOrNull() }
            val bootloader = async { runCatching { bootloaderDetector.scan() }.getOrNull() }
            val tee = async { runCatching { teeDetector.scan() }.getOrNull() }
            val properties = async { runCatching { systemPropertiesDetector.scan() }.getOrNull() }
            val kernel = async { runCatching { kernelCheckDetector.scan() }.getOrNull() }
            val selinux = async { runCatching { selinuxDetector.scan() }.getOrNull() }
            Quint(
                su.await(),
                bootloader.await(),
                tee.await(),
                properties.await(),
                kernel.await(),
                selinux.await(),
            )
        }
        buildString {
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
            properties?.let { report ->
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
            kernel?.let { report ->
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
    }

    private data class Quint(
        val su: SuReport?,
        val bootloader: com.zakodaniumask.manager.data.bootloader.BootloaderReport?,
        val tee: com.zakodaniumask.manager.data.tee.TeeReport?,
        val properties: com.zakodaniumask.manager.data.properties.SystemPropertiesReport?,
        val kernel: com.zakodaniumask.manager.data.kernel.KernelCheckReport?,
        val selinux: com.zakodaniumask.manager.data.selinux.SelinuxReport?,
    )
}
