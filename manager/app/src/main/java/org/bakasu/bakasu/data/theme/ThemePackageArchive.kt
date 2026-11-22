package org.bakasu.bakasu.data.theme

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

/**
 * 图片尺寸探测：Android 侧用 `BitmapFactory` 只读文件头，纯 JVM 测试可注入假实现。
 */
fun interface ImageSizeProbe {
    /** 返回 `(width, height)`；无法识别时返回 `null`。 */
    fun probe(bytes: ByteArray): Pair<Int, Int>?
}

/**
 * 明文主题包读写：zip + `theme.json`，不含加密、签名或网络访问。
 *
 * 读入时强制执行条目白名单、路径校验与体积/尺寸上限，避免 Zip Slip、解压炸弹与 OOM。
 */
object ThemePackageArchive {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    class Archive(
        val manifest: ThemePackageData,
        val images: Map<String, ByteArray>,
    )

    fun encodeManifest(data: ThemePackageData): ByteArray = json.encodeToString(ThemePackageData.serializer(), data).toByteArray(Charsets.UTF_8)

    fun decodeManifest(raw: ByteArray): ThemePackageData {
        if (raw.size > ThemePackageFormat.MAX_MANIFEST_BYTES) {
            throw ThemePackageException.TooLarge("theme.json 超过 ${ThemePackageFormat.MAX_MANIFEST_BYTES / 1024} KB")
        }
        val data = try {
            json.decodeFromString(ThemePackageData.serializer(), raw.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw ThemePackageException.MalformedManifest(e.message ?: "无法解析 JSON")
        }
        if (!ThemePackageFormat.isSupportedVersion(data.version)) {
            throw ThemePackageException.UnsupportedVersion(data.version)
        }
        return data
    }

    /**
     * 读取并校验整包。条目名必须在白名单内，任何 `..`、绝对路径、反斜杠或盘符都会被拒绝。
     */
    fun read(input: InputStream, probe: ImageSizeProbe): Archive {
        var manifest: ThemePackageData? = null
        val images = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > ThemePackageFormat.MAX_ENTRIES) {
                    throw ThemePackageException.TooLarge("条目数超过 ${ThemePackageFormat.MAX_ENTRIES}")
                }
                val name = entry.name
                if (!isSafeEntryName(name)) {
                    throw ThemePackageException.EntryRejected(name, "路径非法")
                }
                if (entry.isDirectory) continue

                when {
                    name == ThemePackageFormat.THEME_ENTRY -> {
                        if (manifest != null) {
                            throw ThemePackageException.EntryRejected(name, "重复条目")
                        }
                        val bytes = readEntry(zip, ThemePackageFormat.MAX_MANIFEST_BYTES, name)
                        totalBytes += bytes.size
                        manifest = decodeManifest(bytes)
                    }

                    ThemePackageFormat.IMAGE_ENTRIES.contains(name) -> {
                        if (images.containsKey(name)) {
                            throw ThemePackageException.EntryRejected(name, "重复条目")
                        }
                        val bytes = readEntry(zip, ThemePackageFormat.MAX_IMAGE_BYTES, name)
                        totalBytes += bytes.size
                        if (totalBytes > ThemePackageFormat.MAX_TOTAL_BYTES) {
                            throw ThemePackageException.TooLarge(
                                "解压总量超过 ${ThemePackageFormat.MAX_TOTAL_BYTES / 1024 / 1024} MB",
                            )
                        }
                        images[name] = bytes
                    }

                    else -> throw ThemePackageException.EntryRejected(name, "不在允许清单内")
                }
                zip.closeEntry()
            }
        }

        val data = manifest ?: throw ThemePackageException.MissingManifest()
        if (totalBytes > ThemePackageFormat.MAX_TOTAL_BYTES) {
            throw ThemePackageException.TooLarge("解压总量超过 ${ThemePackageFormat.MAX_TOTAL_BYTES / 1024 / 1024} MB")
        }
        val validated = LinkedHashMap<String, ByteArray>(images.size)
        images.forEach { (name, bytes) -> validated[name] = validateImage(name, bytes, probe) }
        return Archive(data, validated)
    }

    fun write(manifest: ThemePackageData, images: Map<String, ByteArray>, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ThemePackageFormat.THEME_ENTRY))
            zip.write(encodeManifest(manifest))
            zip.closeEntry()

            images.forEach { (name, bytes) ->
                if (!ThemePackageFormat.IMAGE_ENTRIES.contains(name) || !isSafeEntryName(name)) {
                    throw ThemePackageException.EntryRejected(name, "写入条目不在允许清单内")
                }
                if (bytes.size > ThemePackageFormat.MAX_IMAGE_BYTES) {
                    throw ThemePackageException.TooLarge(
                        "$name 超过 ${ThemePackageFormat.MAX_IMAGE_BYTES / 1024 / 1024} MB",
                    )
                }
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    internal fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > ThemePackageFormat.MAX_ENTRY_NAME_LENGTH) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains('\\') || name.contains(':') || name.contains('\u0000')) return false
        return name.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun validateImage(name: String, bytes: ByteArray, probe: ImageSizeProbe): ByteArray {
        if (!ThemePackageFormat.isImageEntry(name)) {
            throw ThemePackageException.InvalidImage(name, "仅支持 webp/png/jpg")
        }
        val size = probe.probe(bytes) ?: throw ThemePackageException.InvalidImage(name, "无法识别的图片")
        val (width, height) = size
        if (width <= 0 || height <= 0) {
            throw ThemePackageException.InvalidImage(name, "尺寸非法：$width×$height")
        }
        if (width > ThemePackageFormat.MAX_IMAGE_DIMENSION || height > ThemePackageFormat.MAX_IMAGE_DIMENSION) {
            throw ThemePackageException.InvalidImage(
                name,
                "尺寸 $width×$height 超过 ${ThemePackageFormat.MAX_IMAGE_DIMENSION}",
            )
        }
        return bytes
    }

    private fun readEntry(zip: ZipInputStream, limit: Long, name: String): ByteArray {
        val buffer = ByteArray(64 * 1024)
        val out = ByteArrayOutputStream()
        var read = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            read += count
            if (read > limit) {
                throw ThemePackageException.TooLarge("$name 超过 ${limit / 1024} KB")
            }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
