package org.bakasu.bakasu.data.theme

import java.util.Locale

/**
 * 明文主题包的固定布局与上限。
 *
 * ```
 * <id>.zip
 * ├── theme.json
 * ├── background_light.webp
 * ├── background_dark.webp
 * ├── card_light.webp
 * └── card_dark.webp
 * ```
 *
 * 不含密码、签名或网络请求；任何超出下列上限的包直接拒绝，不做截断。
 */
object ThemePackageFormat {
    const val CURRENT_VERSION = 3
    const val MIN_SUPPORTED_VERSION = 2

    const val THEME_ENTRY = "theme.json"
    const val BACKGROUND_LIGHT_ENTRY = "background_light.webp"
    const val BACKGROUND_DARK_ENTRY = "background_dark.webp"
    const val CARD_LIGHT_ENTRY = "card_light.webp"
    const val CARD_DARK_ENTRY = "card_dark.webp"

    const val MAX_MANIFEST_BYTES = 64L * 1024
    const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 12L * 1024 * 1024
    const val MAX_ENTRIES = 16
    const val MAX_IMAGE_DIMENSION = 4096
    const val MAX_ENTRY_NAME_LENGTH = 128

    val IMAGE_ENTRIES: Set<String> = setOf(
        BACKGROUND_LIGHT_ENTRY,
        BACKGROUND_DARK_ENTRY,
        CARD_LIGHT_ENTRY,
        CARD_DARK_ENTRY,
    )

    private val IMAGE_EXTENSIONS = setOf("webp", "png", "jpg", "jpeg")

    fun isSupportedVersion(version: Int): Boolean = version in MIN_SUPPORTED_VERSION..CURRENT_VERSION

    fun isImageEntry(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
}
