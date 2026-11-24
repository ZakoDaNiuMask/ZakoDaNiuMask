package com.zakodaniumask.manager.ksuui.ui.component.uninstalldialog

import androidx.compose.runtime.Composable

/** Placeholder for the KernelSU manager uninstall flow. */
@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (show) onDismissRequest()
}
