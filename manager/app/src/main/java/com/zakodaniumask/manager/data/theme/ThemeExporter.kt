package com.zakodaniumask.manager.data.theme

/**
 * 纯逻辑：把当前外观导出成单套（v2）主题包。导出内容只包含公开字段，不写设备信息。
 */
object ThemeExporter {
    fun build(
        name: String,
        author: String?,
        dark: Boolean,
        seedColor: Int,
        colorStyle: String,
        cardAlphaPercent: Int,
        blurPercent: Int,
        scrimPercent: Int,
        hasBackground: Boolean,
        amoled: Boolean,
    ): ThemePackageData = ThemePackageData(
        version = 2,
        name = name,
        author = author,
        adaptive = false,
        themeMode = if (dark) THEME_MODE_DARK else THEME_MODE_LIGHT,
        keyColor = seedColor,
        colorStyle = colorStyle,
        cardBackgroundAlphaPercent = cardAlphaPercent.coerceIn(0, 100),
        customBackgroundBlurPercent = blurPercent.coerceIn(0, 100),
        customBackgroundScrimPercent = scrimPercent.coerceIn(0, 100),
        hasBackground = hasBackground,
        hasCardBackground = hasBackground,
        enableBlur = blurPercent > 0,
        enableProgressiveBlur = false,
        amoled = amoled,
    )

    const val THEME_MODE_LIGHT = 1
    const val THEME_MODE_DARK = 2
}
