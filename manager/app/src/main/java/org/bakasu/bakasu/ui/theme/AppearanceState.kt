package org.bakasu.bakasu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** 明暗模式来源；与现有 `theme_mode` 偏好一一对应。 */
enum class AppearanceColorMode { SYSTEM, LIGHT, DARK }

/**
 * 外观覆盖色：只有非空项才会替换对应角色，未给出的保持动态取色结果。
 *
 * 这里只定义模型与渲染入口；[ThemeConfig] 目前始终给出 [AppearanceOverlay.None]，
 * 由主题包在解析 `theme.json` 后填充这些字段。
 */
@Immutable
data class AppearanceOverlay(
    val accent: Color? = null,
    val onAccent: Color? = null,
    val textPrimary: Color? = null,
    val textSecondary: Color? = null,
    val surface: Color? = null,
    val card: Color? = null,
    val outline: Color? = null,
    val error: Color? = null,
) {
    val isEmpty: Boolean
        get() = accent == null &&
            onAccent == null &&
            textPrimary == null &&
            textSecondary == null &&
            surface == null &&
            card == null &&
            outline == null &&
            error == null

    companion object {
        val None = AppearanceOverlay()
    }
}

/** 背景配置快照；`dimPercent` 与设置页滑块同口径（0–100）。 */
@Immutable
data class AppearanceBackground(
    val uri: String? = null,
    val dimPercent: Int = 0,
)

/**
 * 不可变外观快照：渲染层唯一读配置入口。
 *
 * 取值来自 [ThemeConfig]（SharedPreferences 的镜像状态），迁移期两者必须保持同步；
 * 本类型只做只读派生，不反向写回偏好。
 */
@Immutable
data class AppearanceState(
    val colorMode: AppearanceColorMode = AppearanceColorMode.SYSTEM,
    val keyColor: Int = 0,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val overlay: AppearanceOverlay = AppearanceOverlay.None,
    val background: AppearanceBackground = AppearanceBackground(),
    val cardAlpha: Float = 1f,
)

/**
 * 由现有可变配置派生不可变快照。
 *
 * 旧偏好的键名与取值范围保持不变，因此升级后不会丢设置；写回仍走
 * [ThemeConfig]/`BackgroundManager`，快照本身不持有存储。
 */
fun ThemeConfig.toAppearanceState(cardAlpha: Float = 1f): AppearanceState = AppearanceState(
    colorMode = when (forceDarkMode) {
        true -> AppearanceColorMode.DARK
        false -> AppearanceColorMode.LIGHT
        null -> AppearanceColorMode.SYSTEM
    },
    keyColor = if (useDynamicColor) monetCompatSeedColor else seedColor,
    dynamicColor = useDynamicColor,
    amoled = amoledBackground,
    overlay = appearanceOverlay,
    background = AppearanceBackground(
        uri = customBackgroundUri?.toString(),
        dimPercent = (backgroundDim * 100f).roundToInt().coerceIn(0, 100),
    ),
    cardAlpha = cardAlpha.coerceIn(0f, 1f),
)
