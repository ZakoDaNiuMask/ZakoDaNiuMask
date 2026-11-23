package com.zakodaniumask.manager.data.theme

import java.io.File

/** 一个主题包在当前明暗模式下解析出的落地结果。 */
data class ResolvedTheme(
    val variant: ThemeVariantData,
    val backgroundFile: File?,
)

/**
 * 纯逻辑：把 [ThemePackageData] 解析成「该用哪套变体、哪张背景图」。
 *
 * - v3 自适应包按 [dark] 选 `light` / `dark`，背景取 `background_light.webp` / `background_dark.webp`；
 * - v2 单套包用顶层字段，背景取 `background_light.webp`；
 * - 包内声明 `hasBackground = false` 时不套用背景，即使图片文件存在。
 */
object ThemeApplier {
    fun resolve(pkg: ThemePackageData, dir: File, dark: Boolean): ResolvedTheme {
        val variant = pkg.variantFor(dark) ?: pkg.sharedVariant()
        val wantsBackground = if (pkg.isAdaptive) {
            variant.hasBackground != false
        } else {
            pkg.hasBackground != false
        }
        val backgroundName = if (pkg.isAdaptive && dark) {
            ThemePackageFormat.BACKGROUND_DARK_ENTRY
        } else {
            ThemePackageFormat.BACKGROUND_LIGHT_ENTRY
        }
        val background = if (wantsBackground) File(dir, backgroundName).takeIf { it.isFile } else null
        return ResolvedTheme(variant, background)
    }
}
