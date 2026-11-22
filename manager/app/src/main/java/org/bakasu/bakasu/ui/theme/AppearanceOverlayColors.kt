package org.bakasu.bakasu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * AMOLED 纯黑：只把容器类角色压到纯黑，不动前景色与组件，避免改动渲染管线。
 */
fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme = if (!amoled) {
    this
} else {
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    )
}

/**
 * 应用外观覆盖层：只有显式给出的颜色才替换对应角色。
 *
 * 与主题包字段名解耦——库角色改名时只需改这里，`theme.json` 的 token 名保持不变。
 */
fun ColorScheme.withAppearance(overlay: AppearanceOverlay): ColorScheme {
    if (overlay.isEmpty) return this
    return copy(
        primary = overlay.accent ?: primary,
        onPrimary = overlay.onAccent ?: onPrimary,
        error = overlay.error ?: error,
        onBackground = overlay.textPrimary ?: onBackground,
        onSurface = overlay.textPrimary ?: onSurface,
        onSurfaceVariant = overlay.textSecondary ?: onSurfaceVariant,
        surface = overlay.surface ?: surface,
        background = overlay.surface ?: background,
        surfaceContainer = overlay.card ?: surfaceContainer,
        surfaceContainerLow = overlay.card ?: surfaceContainerLow,
        surfaceContainerHigh = overlay.card ?: surfaceContainerHigh,
        surfaceContainerHighest = overlay.card ?: surfaceContainerHighest,
        outline = overlay.outline ?: outline,
        outlineVariant = overlay.outline ?: outlineVariant,
    )
}
