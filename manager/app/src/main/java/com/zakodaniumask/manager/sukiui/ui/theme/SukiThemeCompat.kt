package com.zakodaniumask.manager.sukiui.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalBackgroundImagePath = staticCompositionLocalOf { "" }

@Composable
fun backgroundAwareColor(color: Color): Color = color
