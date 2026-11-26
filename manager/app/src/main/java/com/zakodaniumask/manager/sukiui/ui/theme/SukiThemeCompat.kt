package com.zakodaniumask.manager.sukiui.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalBackgroundImagePath = staticCompositionLocalOf { "" }
val LocalCardBackgroundAlpha = staticCompositionLocalOf { 0.6f }

@Composable
fun backgroundAwareColor(
    color: Color,
    backgroundAlpha: Float = LocalCardBackgroundAlpha.current,
): Color = if (LocalBackgroundImagePath.current.isNotEmpty()) color.copy(alpha = backgroundAlpha) else color

@Composable
fun backgroundAwareCardColors(
    color: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer,
    contentColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceContainer,
    backgroundAlpha: Float = LocalCardBackgroundAlpha.current,
): top.yukonga.miuix.kmp.basic.CardColors {
    val adjusted = if (LocalBackgroundImagePath.current.isNotEmpty()) {
        color.copy(alpha = backgroundAlpha)
    } else {
        color
    }
    return top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
        color = adjusted,
        contentColor = contentColor,
    )
}
