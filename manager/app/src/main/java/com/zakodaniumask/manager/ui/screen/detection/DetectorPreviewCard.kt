// SPDX-License-Identifier: GPL-3.0-or-later
// Home preview for the detector; result shown as a green check or a red cross.

package com.zakodaniumask.manager.ui.screen.detection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.detection.SuReport
import com.zakodaniumask.manager.data.detection.SuStage
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget

enum class DetectorPreviewStatus { WAITING, CLEAR, DANGER, SUPPORT, ERROR }

@Composable
fun DetectorPreviewCard(
    report: SuReport?,
    onClick: () -> Unit,
) {
    val status = when {
        report == null -> DetectorPreviewStatus.WAITING
        report.stage == SuStage.FAILED -> DetectorPreviewStatus.ERROR
        report.hasRootIndicators -> DetectorPreviewStatus.DANGER
        report.unobservablePathCount > 0 || !report.nativeAvailable -> DetectorPreviewStatus.SUPPORT
        else -> DetectorPreviewStatus.CLEAR
    }

    val description = when (status) {
        DetectorPreviewStatus.WAITING -> stringResource(R.string.detector_waiting)
        DetectorPreviewStatus.ERROR ->
            report?.errorMessage?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.root_detection_verdict_failed)
        DetectorPreviewStatus.DANGER -> stringResource(R.string.root_detection_verdict_detected)
        DetectorPreviewStatus.SUPPORT -> stringResource(R.string.root_detection_verdict_support)
        DetectorPreviewStatus.CLEAR -> stringResource(R.string.root_detection_verdict_clean)
    }

    val icon = when (status) {
        DetectorPreviewStatus.CLEAR -> Icons.TwoTone.CheckCircle
        DetectorPreviewStatus.DANGER, DetectorPreviewStatus.ERROR -> Icons.TwoTone.Cancel
        DetectorPreviewStatus.WAITING, DetectorPreviewStatus.SUPPORT -> Icons.TwoTone.Info
    }

    val container = when (status) {
        DetectorPreviewStatus.CLEAR -> MaterialTheme.colorScheme.primaryContainer
        DetectorPreviewStatus.DANGER, DetectorPreviewStatus.ERROR ->
            MaterialTheme.colorScheme.errorContainer
        DetectorPreviewStatus.WAITING, DetectorPreviewStatus.SUPPORT ->
            MaterialTheme.colorScheme.secondaryContainer
    }

    SettingsBaseWidget(
        icon = icon,
        iconSize = 18.dp,
        title = stringResource(R.string.detector),
        description = description,
        containerColor = container,
        onClick = { onClick() },
    )
}
