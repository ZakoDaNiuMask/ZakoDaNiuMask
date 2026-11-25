package com.zakodaniumask.manager.ksuui.ui.component.material

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.zakodaniumask.manager.ksuui.ui.component.KsuSendLogDialog

/** Placeholder until the KernelSU bugreport sender is ported. */
@Composable
fun SendLogBottomSheet(
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    KsuSendLogDialog(show = true, onDismiss = onDismiss)
}
