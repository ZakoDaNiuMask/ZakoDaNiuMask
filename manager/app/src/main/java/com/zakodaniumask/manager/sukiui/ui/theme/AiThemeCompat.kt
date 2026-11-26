package com.zakodaniumask.manager.sukiui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** AI chat uses the regular Material theme until the SukiSU AI UI is ported. */
@Composable
fun AiAssistantMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
