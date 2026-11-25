package com.zakodaniumask.manager.ksuui.ui.component.miuix

import androidx.compose.runtime.Composable
import com.zakodaniumask.manager.ksuui.ui.component.KsuSendLogDialog
import com.zakodaniumask.manager.ksuui.ui.component.dialog.LoadingDialogHandle

/** Placeholder until the KernelSU bugreport sender is ported. */
@Composable
fun SendLogDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    loadingDialog: LoadingDialogHandle,
) {
    KsuSendLogDialog(show = show, onDismiss = onDismissRequest)
}
