package com.zakodaniumask.manager.data.theme

/**
 * 主题包导入失败的具体原因；每个分支都对应一句可展示给用户的说明，不存在静默失败。
 */
sealed class ThemePackageException(message: String) : Exception(message) {
    class MissingManifest : ThemePackageException("缺少 theme.json")

    class UnsupportedVersion(val version: Int) : ThemePackageException("不支持的 theme.json 版本：$version")

    class MalformedManifest(detail: String) : ThemePackageException("theme.json 解析失败：$detail")

    class EntryRejected(val entryName: String, val reason: String) : ThemePackageException("拒绝条目 $entryName：$reason")

    class TooLarge(detail: String) : ThemePackageException("主题包超出限制：$detail")

    class InvalidImage(val entryName: String, val reason: String) : ThemePackageException("图片 $entryName 不可用：$reason")

    class InvalidId(val id: String) : ThemePackageException("主题 id 非法：$id")

    /** `.sukisu` 只读兼容：SKTH 头缺失、密文损坏或 GCM tag 校验失败。 */
    class IntegrityFailure(detail: String) : ThemePackageException("主题包校验失败：$detail")
}
