package com.zakodaniumask.manager.ksuui.ui.component.uninstalldialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.navigation.Route

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!show) return
    val navigator = LocalNavigator.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Uninstall manager") },
        text = { Text("Open the manager uninstall flow?") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    navigator.push(Route.Flash.uninstall())
                },
            ) {
                Text("Uninstall")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}
