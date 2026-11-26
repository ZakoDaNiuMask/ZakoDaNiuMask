package com.zakodaniumask.manager.sukiui.ai.api

import androidx.compose.runtime.Composable

/** AI integration is a later SukiSU phase. */
object AiFeatureBridge {
    val isAvailable: Boolean = false

    @Composable
    fun HomeCard(onOpenChat: () -> Unit) = Unit
}
