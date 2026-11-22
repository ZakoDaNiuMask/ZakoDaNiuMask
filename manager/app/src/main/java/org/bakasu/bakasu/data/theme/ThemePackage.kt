package org.bakasu.bakasu.data.theme

import kotlinx.serialization.Serializable

/**
 * 单个明暗变体携带的外观字段。全部可空：缺失表示「不覆盖当前设置」，而不是重置成默认值。
 */
@Serializable
data class ThemeVariantData(
    val themeMode: Int? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val enableBlur: Boolean? = null,
    val enableProgressiveBlur: Boolean? = null,
    val amoled: Boolean? = null,
)

/**
 * `theme.json` 的完整内容。
 *
 * - v2（[adaptive] 非 `true`）：外观字段写在顶层，单套配色；
 * - v3（[adaptive] `== true`）：[light] / [dark] 各描述一套完整外观，图片按模式分开存放。
 *
 * 读取端忽略未知字段、缺失字段为 `null`；写入端只写当前版本已知字段，不写 `null`。
 */
@Serializable
data class ThemePackageData(
    val version: Int = ThemePackageFormat.CURRENT_VERSION,
    val name: String? = null,
    val author: String? = null,
    val adaptive: Boolean? = null,
    val themeMode: Int? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val enableBlur: Boolean? = null,
    val enableProgressiveBlur: Boolean? = null,
    val amoled: Boolean? = null,
    val light: ThemeVariantData? = null,
    val dark: ThemeVariantData? = null,
) {
    /** 仅当声明了自适应且两套变体齐全时才按明暗拆分，避免读成半个包。 */
    val isAdaptive: Boolean
        get() = adaptive == true && light != null && dark != null

    /** v3 包按当前模式取变体；v2 包返回 `null`，由调用方使用 [sharedVariant]。 */
    fun variantFor(dark: Boolean): ThemeVariantData? = when {
        !isAdaptive -> null
        dark -> this.dark
        else -> this.light
    }

    /** v2 顶层字段；v3 包调用时返回全空，调用方应优先用 [variantFor]。 */
    fun sharedVariant(): ThemeVariantData = ThemeVariantData(
        themeMode = themeMode,
        keyColor = keyColor,
        colorStyle = colorStyle,
        cardBackgroundAlphaPercent = cardBackgroundAlphaPercent,
        customBackgroundBlurPercent = customBackgroundBlurPercent,
        customBackgroundScrimPercent = customBackgroundScrimPercent,
        hasBackground = hasBackground,
        hasCardBackground = hasCardBackground,
        enableBlur = enableBlur,
        enableProgressiveBlur = enableProgressiveBlur,
        amoled = amoled,
    )
}
