/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring): single-source property rule audit.
 * The native property-area (prop_area) parsing and multi-source cross-checks are not ported.
 */

package com.zakodaniumask.manager.data.properties

import com.zakodaniumask.manager.data.attestation.SystemPropertiesReader
import com.zakodaniumask.manager.data.detection.DetectorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PropertySeverity { DANGER, WARNING, SAFE, NEUTRAL }

data class PropertySignal(
    val property: String,
    val description: String,
    val category: SystemPropertyCategory,
    val value: String?,
    val severity: PropertySeverity,
)

data class SystemPropertiesReport(
    val status: DetectorStatus,
    val signals: List<PropertySignal>,
    val info: List<PropertySignal>,
    val checkedRuleCount: Int,
    val observedRuleCount: Int,
    val suspiciousFingerprint: String? = null,
    val error: String? = null,
) {
    val dangerSignals: List<PropertySignal>
        get() = signals.filter { it.severity == PropertySeverity.DANGER }

    val warningSignals: List<PropertySignal>
        get() = signals.filter { it.severity == PropertySeverity.WARNING }

    companion object {
        fun failed(message: String): SystemPropertiesReport = SystemPropertiesReport(
            status = DetectorStatus.ERROR,
            signals = emptyList(),
            info = emptyList(),
            checkedRuleCount = 0,
            observedRuleCount = 0,
            error = message,
        )
    }
}

class SystemPropertiesDetector {

    suspend fun scan(): SystemPropertiesReport = withContext(Dispatchers.IO) {
        runCatching { scanInternal() }
            .getOrElse { throwable ->
                SystemPropertiesReport.failed(throwable.message ?: "System properties scan failed.")
            }
    }

    private fun scanInternal(): SystemPropertiesReport {
        val signals = SystemPropertiesCatalog.rules.map { rule ->
            val value = SystemPropertiesReader.read(rule.property).value
            PropertySignal(
                property = rule.property,
                description = rule.description,
                category = rule.category,
                value = value,
                severity = evaluate(rule, value),
            )
        }

        val info = SystemPropertiesCatalog.infoProperties.map { name ->
            PropertySignal(
                property = name,
                description = name,
                category = SystemPropertyCategory.INFO,
                value = SystemPropertiesReader.read(name).value,
                severity = PropertySeverity.NEUTRAL,
            )
        }

        val fingerprint = info.firstOrNull { it.property == "ro.build.fingerprint" }?.value
        val suspiciousFingerprint = fingerprint?.let { value ->
            SystemPropertiesCatalog.suspiciousFingerprintPatterns
                .firstOrNull { pattern -> value.contains(pattern, ignoreCase = true) }
        }

        val observed = signals.count { it.value != null }
        val danger = signals.any { it.severity == PropertySeverity.DANGER }
        val warning = signals.any { it.severity == PropertySeverity.WARNING }

        val status = when {
            danger || suspiciousFingerprint != null -> DetectorStatus.DANGER
            warning -> DetectorStatus.SUPPORT
            observed == 0 -> DetectorStatus.INFO
            else -> DetectorStatus.CLEAR
        }

        return SystemPropertiesReport(
            status = status,
            signals = signals.filter { it.severity == PropertySeverity.DANGER || it.severity == PropertySeverity.WARNING },
            info = info.filter { it.value != null },
            checkedRuleCount = SystemPropertiesCatalog.rules.size,
            observedRuleCount = observed,
            suspiciousFingerprint = suspiciousFingerprint,
        )
    }

    private fun evaluate(rule: SystemPropertyRule, value: String?): PropertySeverity {
        if (value == null) return PropertySeverity.NEUTRAL
        if (rule.dangerousValues.contains("*")) return PropertySeverity.DANGER
        if (rule.dangerousValues.any { it.equals(value, ignoreCase = true) }) {
            return PropertySeverity.DANGER
        }
        if (rule.warningValues.any { it.equals(value, ignoreCase = true) }) {
            return PropertySeverity.WARNING
        }
        if (rule.expectedSafeValue != null && value.equals(rule.expectedSafeValue, ignoreCase = true)) {
            return PropertySeverity.SAFE
        }
        return PropertySeverity.NEUTRAL
    }
}
