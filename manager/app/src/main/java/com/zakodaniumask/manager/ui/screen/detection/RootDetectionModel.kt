/*
 * Presentation model adapted from Duck Detector's SU card
 * (features/su/presentation/SuCardModelMapper.kt and SuEvidenceRows.kt).
 *
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Ported into ZakoDaNiuMask; see LICENSE.
 */

package com.zakodaniumask.manager.ui.screen.detection

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.detection.SuMethodOutcome
import com.zakodaniumask.manager.data.detection.SuReport
import com.zakodaniumask.manager.data.detection.SuStage

/** Verdict/status of a detection item, mirroring Duck Detector's DetectorStatus severities. */
enum class RootStatus { DANGER, CLEAR, SUPPORT, ERROR, INFO }

data class RootFact(
    @param:StringRes val labelRes: Int,
    val value: String,
    val status: RootStatus,
)

data class RootRow(
    @param:StringRes val labelRes: Int,
    val value: String,
    val status: RootStatus,
    val detail: String? = null,
    val monospace: Boolean = true,
)

data class RootImpact(
    val text: String,
    val status: RootStatus,
)

data class RootDetectionModel(
    @param:StringRes val titleRes: Int,
    val subtitle: String,
    val verdict: String,
    val summary: String,
    val status: RootStatus,
    val facts: List<RootFact>,
    val artifactRows: List<RootRow>,
    val contextRows: List<RootRow>,
    val impacts: List<RootImpact>,
    val methodRows: List<RootRow>,
    val scanRows: List<RootRow>,
    val reportText: String,
)

private fun SuMethodOutcome.toRootStatus(): RootStatus = when (this) {
    SuMethodOutcome.DETECTED -> RootStatus.DANGER
    SuMethodOutcome.CLEAN -> RootStatus.CLEAR
    SuMethodOutcome.SUPPORT -> RootStatus.SUPPORT
}

@StringRes
private fun methodLabelRes(label: String): Int = when (label) {
    "daemonScan" -> R.string.root_detection_method_daemon
    "fileScan" -> R.string.root_detection_method_file
    "nativeSyscall" -> R.string.root_detection_method_syscall
    "nativeLibrary" -> R.string.root_detection_method_library
    else -> R.string.root_detection_method_file
}

@Composable
fun rememberRootDetectionModel(report: SuReport): RootDetectionModel {
    val noneText = stringResource(R.string.root_detection_none)
    val unavailableText = stringResource(R.string.root_detection_unavailable)
    val unknownText = stringResource(R.string.root_detection_unknown)
    val normalText = stringResource(R.string.root_detection_context_normal)
    val abnormalText = stringResource(R.string.root_detection_context_abnormal)
    val isolatedProbe = stringResource(R.string.root_detection_isolated_probe)
    val naText = stringResource(R.string.root_detection_na)

    val daemonNames = report.daemons.map { it.name }.distinct()
    val daemonValue = when {
        daemonNames.isEmpty() -> noneText
        daemonNames.size <= 2 -> daemonNames.joinToString("/")
        else -> daemonNames.take(2).joinToString("/") + " +${daemonNames.size - 2}"
    }
    val contextValue = when {
        report.selfContextAbnormal -> abnormalText
        report.selfContext.isNotBlank() -> normalText
        else -> unknownText
    }

    // Pass/fail is driven by visible root indicators only; incomplete coverage is reported as a
    // detail below, not as the verdict.
    val status = when (report.stage) {
        SuStage.FAILED -> RootStatus.ERROR
        SuStage.LOADING -> RootStatus.INFO
        SuStage.READY -> if (report.hasRootIndicators) RootStatus.DANGER else RootStatus.CLEAR
    }

    val verdict = stringResource(
        when {
            report.stage == SuStage.FAILED -> R.string.root_detection_verdict_failed
            report.stage == SuStage.LOADING -> R.string.root_detection_scanning
            report.hasRootIndicators -> R.string.root_detection_verdict_detected
            else -> R.string.root_detection_verdict_clean
        }
    )

    val summary = when {
        report.stage == SuStage.FAILED ->
            report.errorMessage ?: stringResource(R.string.root_detection_verdict_failed)
        report.stage == SuStage.LOADING -> stringResource(R.string.root_detection_scanning)
        report.daemons.isNotEmpty() -> stringResource(R.string.root_detection_summary_daemons)
        report.selfContextAbnormal || report.suspiciousProcesses.isNotEmpty() ->
            stringResource(R.string.root_detection_summary_context)
        report.suBinaries.isNotEmpty() -> stringResource(R.string.root_detection_summary_binaries)
        !report.nativeAvailable -> stringResource(R.string.root_detection_summary_partial)
        else -> stringResource(R.string.root_detection_summary_clean)
    }

    val subtitle = stringResource(
        R.string.root_detection_subtitle,
        report.checkedSuPathCount,
        report.checkedDaemonPathCount,
        isolatedProbe,
    )

    val facts = listOf(
        RootFact(
            labelRes = R.string.root_detection_fact_artifacts,
            value = if (report.suBinaries.isEmpty()) noneText else report.suBinaries.size.toString(),
            status = if (report.suBinaries.isEmpty()) RootStatus.CLEAR else RootStatus.DANGER,
        ),
        RootFact(
            labelRes = R.string.root_detection_fact_daemons,
            value = if (report.daemons.isEmpty()) noneText else daemonValue,
            status = if (report.daemons.isEmpty()) RootStatus.CLEAR else RootStatus.DANGER,
        ),
        RootFact(
            labelRes = R.string.root_detection_fact_context,
            value = contextValue,
            status = when {
                report.selfContextAbnormal -> RootStatus.DANGER
                report.selfContext.isNotBlank() -> RootStatus.CLEAR
                else -> RootStatus.SUPPORT
            },
        ),
        RootFact(
            labelRes = R.string.root_detection_fact_processes,
            value = if (!report.nativeAvailable) naText else report.suspiciousProcesses.size.toString(),
            status = when {
                !report.nativeAvailable -> RootStatus.SUPPORT
                report.suspiciousProcesses.isEmpty() -> RootStatus.CLEAR
                else -> RootStatus.DANGER
            },
        ),
    )

    val artifactRows = listOf(
        RootRow(
            labelRes = R.string.root_detection_row_daemons,
            value = daemonValue,
            status = if (report.daemons.isEmpty()) RootStatus.CLEAR else RootStatus.DANGER,
            detail = report.daemons
                .joinToString(separator = "\n") { "${it.name}: ${it.path}" }
                .ifBlank { null },
        ),
        RootRow(
            labelRes = R.string.root_detection_row_binaries,
            value = if (report.suBinaries.isEmpty()) noneText else report.suBinaries.size.toString(),
            status = if (report.suBinaries.isEmpty()) RootStatus.CLEAR else RootStatus.DANGER,
            detail = report.suBinaries.joinToString(separator = "\n").ifBlank { null },
        ),
    )

    val noSelfContext = stringResource(R.string.root_detection_no_self_context)
    val nativeUnavailableDetail = stringResource(R.string.root_detection_native_unavailable)
    val contextRows = listOf(
        RootRow(
            labelRes = R.string.root_detection_row_self_context,
            value = contextValue,
            status = when {
                report.selfContextAbnormal -> RootStatus.DANGER
                report.selfContext.isNotBlank() -> RootStatus.CLEAR
                else -> RootStatus.SUPPORT
            },
            detail = report.selfContext.ifBlank { noSelfContext },
        ),
        RootRow(
            labelRes = R.string.root_detection_row_processes,
            value = when {
                !report.nativeAvailable -> unavailableText
                report.suspiciousProcesses.isEmpty() -> noneText
                else -> report.suspiciousProcesses.size.toString()
            },
            status = when {
                !report.nativeAvailable -> RootStatus.SUPPORT
                report.suspiciousProcesses.isEmpty() -> RootStatus.CLEAR
                else -> RootStatus.DANGER
            },
            detail = if (!report.nativeAvailable) {
                nativeUnavailableDetail
            } else {
                report.suspiciousProcesses.joinToString(separator = "\n").ifBlank { null }
            },
        ),
        RootRow(
            labelRes = R.string.root_detection_row_probe_path,
            value = isolatedProbe,
            status = RootStatus.INFO,
            detail = nativeUnavailableDetail,
        ),
    )

    val impacts = if (report.hasRootIndicators) {
        listOf(
            RootImpact(stringResource(R.string.root_detection_impact_detected_1), RootStatus.DANGER),
            RootImpact(stringResource(R.string.root_detection_impact_detected_2), RootStatus.DANGER),
            RootImpact(stringResource(R.string.root_detection_impact_detected_3), RootStatus.DANGER),
        )
    } else {
        listOf(
            RootImpact(stringResource(R.string.root_detection_impact_clean), RootStatus.CLEAR),
            RootImpact(stringResource(R.string.root_detection_impact_partial), RootStatus.SUPPORT),
            RootImpact(stringResource(R.string.root_detection_impact_not_proof), RootStatus.SUPPORT),
        )
    }

    val methodRows = report.methods.map { method ->
        RootRow(
            labelRes = methodLabelRes(method.label),
            value = method.summary,
            status = method.outcome.toRootStatus(),
            detail = method.detail,
        )
    }

    val scanRows = listOf(
        RootRow(
            labelRes = R.string.root_detection_row_su_paths,
            value = report.checkedSuPathCount.toString(),
            status = RootStatus.INFO,
            monospace = false,
        ),
        RootRow(
            labelRes = R.string.root_detection_row_daemon_paths,
            value = report.checkedDaemonPathCount.toString(),
            status = RootStatus.INFO,
            monospace = false,
        ),
        RootRow(
            labelRes = R.string.root_detection_row_proc_contexts,
            value = if (!report.nativeAvailable) naText else report.checkedProcessCount.toString(),
            status = RootStatus.INFO,
            monospace = false,
        ),
        RootRow(
            labelRes = R.string.root_detection_row_proc_denied,
            value = if (!report.nativeAvailable) naText else report.deniedProcessCount.toString(),
            status = RootStatus.INFO,
            monospace = false,
        ),
    )

    val sectionArtifacts = stringResource(R.string.root_detection_section_artifacts)
    val sectionContext = stringResource(R.string.root_detection_section_context)
    val sectionImpact = stringResource(R.string.root_detection_section_impact)
    val sectionMethods = stringResource(R.string.root_detection_section_methods)
    val sectionScan = stringResource(R.string.root_detection_section_scan)

    val reportText = buildString {
        appendLine(stringResource(R.string.root_detection))
        appendLine(subtitle)
        appendLine("$verdict — $summary")
        appendLine()
        appendLine(sectionArtifacts)
        artifactRows.forEach { row ->
            appendLine("${stringResource(row.labelRes)}: ${row.value}")
            row.detail?.let { appendLine("    $it") }
        }
        appendLine()
        appendLine(sectionContext)
        contextRows.forEach { row ->
            appendLine("${stringResource(row.labelRes)}: ${row.value}")
            row.detail?.let { appendLine("    $it") }
        }
        appendLine()
        appendLine(sectionImpact)
        impacts.forEach { appendLine("- ${it.text}") }
        appendLine()
        appendLine(sectionMethods)
        methodRows.forEach { row ->
            appendLine("${stringResource(row.labelRes)}: ${row.value}")
            row.detail?.let { appendLine("    $it") }
        }
        appendLine()
        appendLine(sectionScan)
        scanRows.forEach { row ->
            appendLine("${stringResource(row.labelRes)}: ${row.value}")
            row.detail?.let { appendLine("    $it") }
        }
    }

    return RootDetectionModel(
        titleRes = R.string.root_detection,
        subtitle = subtitle,
        verdict = verdict,
        summary = summary,
        status = status,
        facts = facts,
        artifactRows = artifactRows,
        contextRows = contextRows,
        impacts = impacts,
        methodRows = methodRows,
        scanRows = scanRows,
        reportText = reportText,
    )
}
