// SPDX-License-Identifier: GPL-3.0-or-later
// Home preview for the detector: green check when every detector is clear, red cross when any
// detector reports a risk, info while the scan is still running.

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
import com.zakodaniumask.manager.data.detection.DetectorStatus
import com.zakodaniumask.manager.ui.component.settings.SettingsBaseWidget

enum class DetectorPreviewStatus { WAITING, CLEAR, DANGER, SUPPORT, ERROR }

@Composable
fun DetectorPreviewCard(
    overall: DetectorStatus?,
    onClick: () -> Unit,
) {
    val status = when (overall) {
        null -> DetectorPreviewStatus.WAITING
        DetectorStatus.CLEAR -> DetectorPreviewStatus.CLEAR
        DetectorStatus.DANGER -> DetectorPreviewStatus.DANGER
        DetectorStatus.ERROR -> DetectorPreviewStatus.ERROR
        else -> DetectorPreviewStatus.SUPPORT
    }

    val description = when (status) {
        DetectorPreviewStatus.WAITING -> stringResource(R.string.detector_waiting)
        DetectorPreviewStatus.CLEAR -> stringResource(R.string.root_detection_verdict_clean)
        DetectorPreviewStatus.DANGER -> stringResource(R.string.root_detection_verdict_detected)
        DetectorPreviewStatus.ERROR -> stringResource(R.string.root_detection_verdict_failed)
        DetectorPreviewStatus.SUPPORT -> stringResource(R.string.root_detection_verdict_support)
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
