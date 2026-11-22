package org.bakasu.bakasu.data.theme

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 已落盘的主题包：`dir` 下平铺 `theme.json` 与图片。 */
data class StoredTheme(
    val id: String,
    val dir: File,
    val manifest: ThemePackageData,
    val images: Set<String>,
)

/**
 * 主题包在应用私有目录的落盘层：`<rootDir>/<id>/`。
 *
 * 导入先写临时目录、校验通过后整体替换目标目录，失败不留残渣；
 * id 只接受小写字母、数字与 `-`/`_`，杜绝路径穿越落点。
 */
class ThemePackageStorage(private val rootDir: File) {
    init {
        rootDir.mkdirs()
    }

    fun importPackage(input: InputStream, id: String, probe: ImageSizeProbe): StoredTheme {
        requireId(id)
        val staging = File(rootDir, ".staging-$id-${System.nanoTime()}")
        try {
            if (!staging.mkdirs()) throw IOException("无法创建临时目录 ${staging.name}")
            val archive = ThemePackageArchive.read(input, probe)
            File(staging, ThemePackageFormat.THEME_ENTRY)
                .writeBytes(ThemePackageArchive.encodeManifest(archive.manifest))
            archive.images.forEach { (name, bytes) -> File(staging, name).writeBytes(bytes) }

            val target = File(rootDir, id)
            if (target.exists() && !target.deleteRecursively()) {
                throw IOException("无法替换已有主题 $id")
            }
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            return StoredTheme(id, target, archive.manifest, archive.images.keys.toSet())
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    fun exportPackage(id: String, output: OutputStream) {
        requireId(id)
        val dir = File(rootDir, id)
        val manifestFile = File(dir, ThemePackageFormat.THEME_ENTRY)
        if (!manifestFile.isFile) throw ThemePackageException.MissingManifest()
        val manifest = ThemePackageArchive.decodeManifest(manifestFile.readBytes())
        val images = LinkedHashMap<String, ByteArray>()
        ThemePackageFormat.IMAGE_ENTRIES.forEach { name ->
            val file = File(dir, name)
            if (file.isFile) images[name] = file.readBytes()
        }
        ThemePackageArchive.write(manifest, images, output)
    }

    fun list(): List<StoredTheme> = rootDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { dir ->
            val manifest = readManifest(dir) ?: return@mapNotNull null
            StoredTheme(dir.name, dir, manifest, storedImages(dir))
        }

    fun delete(id: String): Boolean {
        requireId(id)
        return File(rootDir, id).deleteRecursively()
    }

    private fun readManifest(dir: File): ThemePackageData? {
        val file = File(dir, ThemePackageFormat.THEME_ENTRY)
        if (!file.isFile) return null
        return runCatching { ThemePackageArchive.decodeManifest(file.readBytes()) }.getOrNull()
    }

    private fun storedImages(dir: File): Set<String> = ThemePackageFormat.IMAGE_ENTRIES.filter { File(dir, it).isFile }.toSet()

    private fun requireId(id: String) {
        if (!ID_PATTERN.matches(id)) throw ThemePackageException.InvalidId(id)
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}
