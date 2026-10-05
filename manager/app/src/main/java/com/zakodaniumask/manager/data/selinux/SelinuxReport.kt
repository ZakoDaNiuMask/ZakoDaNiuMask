/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring).
 */

package com.zakodaniumask.manager.data.selinux

import com.zakodaniumask.manager.data.detection.DetectorStatus

enum class SelinuxMode {
    ENFORCING,
    PERMISSIVE,
    DISABLED,
    UNKNOWN,
}

data class SelinuxCheckResult(
    val method: String,
    val status: String,
    val secure: Boolean?,
    val permissionDenied: Boolean = false,
    val detail: String? = null,
    val readsEnforcing: Boolean = false,
)

data class SelinuxReport(
    val status: DetectorStatus,
    val mode: SelinuxMode,
    val statusLabel: String,
    val paradoxDetected: Boolean,
    val checks: List<SelinuxCheckResult>,
    val processContext: String?,
    val contextType: String?,
    val error: String? = null,
) {
    companion object {
        fun failed(message: String): SelinuxReport = SelinuxReport(
            status = DetectorStatus.ERROR,
            mode = SelinuxMode.UNKNOWN,
            statusLabel = "Failed",
            paradoxDetected = false,
            checks = emptyList(),
            processContext = null,
            contextType = null,
            error = message,
        )
    }
}
