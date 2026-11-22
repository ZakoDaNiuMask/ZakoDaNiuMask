package org.bakasu.bakasu.ui.screen.themeSettings.component

import androidx.annotation.StringRes
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.theme.ThemePackageException

/**
 * 把导入失败原因映射到 `strings.xml`，界面不展示异常类名、堆栈或内部路径。
 */
@StringRes
fun Throwable.themePackageMessageRes(): Int = when (this) {
    is ThemePackageException.MissingManifest -> R.string.theme_package_error_missing_manifest
    is ThemePackageException.UnsupportedVersion -> R.string.theme_package_error_unsupported_version
    is ThemePackageException.MalformedManifest -> R.string.theme_package_error_malformed_manifest
    is ThemePackageException.EntryRejected -> R.string.theme_package_error_entry_rejected
    is ThemePackageException.TooLarge -> R.string.theme_package_error_too_large
    is ThemePackageException.InvalidImage -> R.string.theme_package_error_invalid_image
    is ThemePackageException.InvalidId -> R.string.theme_package_error_invalid_id
    else -> R.string.theme_package_error_unknown
}
