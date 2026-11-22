package org.bakasu.bakasu.ui.theme

import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.theme.ResolvedTheme
import org.bakasu.bakasu.data.theme.ThemeApplier
import org.bakasu.bakasu.data.theme.ThemeExporter
import org.bakasu.bakasu.data.theme.ThemePackageArchive
import org.bakasu.bakasu.data.theme.ThemePackageData
import org.bakasu.bakasu.data.theme.ThemePackageFormat
import org.bakasu.bakasu.data.theme.ThemePackageStorage
import org.bakasu.bakasu.data.theme.ThemeRepository

/**
 * 把主题包落到 [ThemeConfig] / [CardConfig] / [BackgroundManager]：读取与解析在 IO，
 * 状态与 prefs 写入在主线程，深浅色切换时只重算变体不重复落盘。
 */
class ThemePackageApplier(
    private val context: Context,
    private val storage: ThemePackageStorage,
    private val themeConfig: ThemeConfig,
    private val cardConfig: CardConfig,
    private val backgroundManager: BackgroundManager,
    private val themeRepository: ThemeRepository,
) {
    suspend fun apply(id: String, dark: Boolean): Boolean = withContext(Dispatchers.IO) {
        val manifest = readManifest(id) ?: return@withContext false
        val resolved = ThemeApplier.resolve(manifest, storage.dirOf(id), dark)
        withContext(Dispatchers.Main) {
            themeConfig.appliedThemeId = id
            themeConfig.appliedThemeData = manifest
            applyResolved(resolved, persist = true)
        }
        true
    }

    /** 深浅色切换：只按 [dark] 重算变体与背景，不改来源 id。 */
    suspend fun reapplyForMode(dark: Boolean) {
        val manifest = themeConfig.appliedThemeData ?: return
        val id = themeConfig.appliedThemeId ?: return
        val resolved = withContext(Dispatchers.IO) { ThemeApplier.resolve(manifest, storage.dirOf(id), dark) }
        withContext(Dispatchers.Main) { applyResolved(resolved, persist = false) }
    }

    /** 冷启动后按已保存的 id 恢复 manifest；包已被删则自动解绑。 */
    suspend fun restoreApplied() {
        val id = themeConfig.appliedThemeId ?: return
        val manifest = withContext(Dispatchers.IO) { readManifest(id) }
        withContext(Dispatchers.Main) {
            if (manifest == null) {
                detach()
            } else {
                themeConfig.appliedThemeData = manifest
            }
        }
    }

    fun detach() {
        themeConfig.detachAppliedTheme()
        backgroundManager.applyThemeBackground(null)
        backgroundManager.saveAppliedThemeId(null)
    }

    fun resetAppearance() {
        themeConfig.reset()
        backgroundManager.applyThemeBackground(null)
        backgroundManager.saveAppliedThemeId(null)
        cardConfig.save()
    }

    /** 把当前外观导出成明文单套 zip；图片超出上限时退化为无背景。 */
    suspend fun exportCurrent(target: Uri, dark: Boolean, name: String): Boolean = withContext(Dispatchers.IO) {
        val background = readBackgroundBytes()
        val packageData = ThemeExporter.build(
            name = name,
            author = null,
            dark = dark,
            seedColor = themeConfig.seedColor,
            colorStyle = themeConfig.dynamicPaletteStyle.name,
            cardAlphaPercent = (cardConfig.cardAlpha * 100).roundToInt(),
            blurPercent = if (themeConfig.isEnableBlur) 50 else 0,
            scrimPercent = (themeConfig.backgroundDim * 100).roundToInt(),
            hasBackground = background != null,
            amoled = themeConfig.amoledBackground,
        )
        val images = if (background != null) {
            mapOf(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to background)
        } else {
            emptyMap()
        }
        runCatching {
            val out = context.contentResolver.openOutputStream(target) ?: return@runCatching false
            out.use { ThemePackageArchive.write(packageData, images, it) }
            true
        }.getOrDefault(false)
    }

    private fun applyResolved(resolved: ResolvedTheme, persist: Boolean) {
        val variant = resolved.variant
        variant.keyColor?.let {
            themeConfig.seedColor = it
            themeConfig.monetCompatSeedColor = it
            if (persist) themeRepository.saveSeedColor(it)
        }
        variant.cardBackgroundAlphaPercent?.let { cardConfig.cardAlpha = it.coerceIn(0, 100) / 100f }
        variant.customBackgroundScrimPercent?.let { themeConfig.backgroundDim = it.coerceIn(0, 100) / 100f }
        variant.enableBlur?.let {
            themeConfig.isEnableBlur = it
            if (persist) backgroundManager.saveEnableBlur(it)
        }
        variant.amoled?.let { themeConfig.amoledBackground = it }

        resolved.backgroundFile?.let { backgroundManager.applyThemeBackground(Uri.fromFile(it)) }

        if (persist) {
            backgroundManager.saveAppliedThemeId(themeConfig.appliedThemeId)
            cardConfig.save()
        }
    }

    private fun readBackgroundBytes(): ByteArray? {
        val uri = themeConfig.customBackgroundUri ?: return null
        return runCatching {
            val bytes = if (uri.scheme == "file") {
                uri.path?.let { File(it).readBytes() }
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.takeIf { it.size <= ThemePackageFormat.MAX_IMAGE_BYTES }
        }.getOrNull()
    }

    private fun readManifest(id: String): ThemePackageData? {
        val file = File(storage.dirOf(id), ThemePackageFormat.THEME_ENTRY)
        if (!file.isFile) return null
        return runCatching { ThemePackageArchive.decodeManifest(file.readBytes()) }.getOrNull()
    }
}
