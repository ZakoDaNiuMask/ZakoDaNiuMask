package org.bakasu.bakasu.data.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SukiSU-Nova `.sukisu` 包的只读兼容层。
 *
 * 布局为 `"SKTH"(4) | IV(12) | AES-GCM 密文 + 128-bit tag`，密钥是 Nova 内置的固定哈希。
 * 读取后统一归一化成明文包：条目名映射到本项目约定、未知条目丢弃、背景开关按实际图片补齐。
 * 导出永远走明文 zip，不加密。
 */
object SukisuThemePackage {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'K'.code.toByte(), 'T'.code.toByte(), 'H'.code.toByte())
    private const val IV_SIZE = 12
    private const val TAG_BIT_LENGTH = 128
    private const val MAX_PAYLOAD_BYTES = ThemePackageFormat.MAX_TOTAL_BYTES * 2

    /** Nova 内置的主题密钥哈希，前 32 字节即 AES-256 密钥。 */
    private val NOVA_KEY_HASH = byteArrayOf(
        0x41.toByte(), 0xdc.toByte(), 0x17.toByte(), 0x4e.toByte(), 0x50.toByte(), 0x96.toByte(), 0xb4.toByte(), 0xee.toByte(),
        0x93.toByte(), 0x31.toByte(), 0x1c.toByte(), 0xc8.toByte(), 0x92.toByte(), 0xb1.toByte(), 0x32.toByte(), 0xe2.toByte(),
        0x19.toByte(), 0x61.toByte(), 0x01.toByte(), 0x2b.toByte(), 0x14.toByte(), 0xcf.toByte(), 0x21.toByte(), 0x79.toByte(),
        0x54.toByte(), 0x0f.toByte(), 0x20.toByte(), 0x3c.toByte(), 0xa7.toByte(), 0x2a.toByte(), 0xb9.toByte(), 0xc1.toByte(),
        0xd9.toByte(), 0xcd.toByte(), 0xa5.toByte(), 0x45.toByte(), 0x2e.toByte(), 0x58.toByte(), 0xe6.toByte(), 0xdf.toByte(),
        0x56.toByte(), 0x79.toByte(), 0xa1.toByte(), 0x46.toByte(), 0x4d.toByte(), 0x44.toByte(), 0x0a.toByte(), 0x0a.toByte(),
    )

    private val KEY = SecretKeySpec(NOVA_KEY_HASH.copyOfRange(0, 32), "AES")

    /**
     * Nova 的背景条目有过多种写法：v2 单图用 `background` / `card_background`，
     * v3 双色用 `background_light|dark`，部分第三方包还带 `.png/.jpg/.webp` 后缀。
     * 全部收敛到本项目的四个固定名。
     */
    private val ENTRY_ALIASES: Map<String, String> = buildMap {
        val extensions = listOf("", ".webp", ".png", ".jpg", ".jpeg")
        for (ext in extensions) {
            put("background$ext", ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
            put("card_background$ext", ThemePackageFormat.CARD_LIGHT_ENTRY)
            put("background_light$ext", ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
            put("background_dark$ext", ThemePackageFormat.BACKGROUND_DARK_ENTRY)
            put("card_background_light$ext", ThemePackageFormat.CARD_LIGHT_ENTRY)
            put("card_background_dark$ext", ThemePackageFormat.CARD_DARK_ENTRY)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun decrypt(bytes: ByteArray): ByteArray {
        if (!isEncrypted(bytes)) {
            throw ThemePackageException.IntegrityFailure("缺少 SKTH 头")
        }
        if (bytes.size <= MAGIC.size + IV_SIZE) {
            throw ThemePackageException.IntegrityFailure("密文过短")
        }
        val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_SIZE)
        val payloadOffset = MAGIC.size + IV_SIZE
        val payloadLength = bytes.size - payloadOffset
        if (payloadLength > MAX_PAYLOAD_BYTES) {
            throw ThemePackageException.TooLarge("明文超过 ${MAX_PAYLOAD_BYTES / 1024 / 1024} MB")
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, KEY, GCMParameterSpec(TAG_BIT_LENGTH, iv))
            cipher.doFinal(bytes, payloadOffset, payloadLength)
        } catch (e: GeneralSecurityException) {
            throw ThemePackageException.IntegrityFailure(e.message ?: "解密失败")
        }
    }

    /**
     * 归一化成标准明文 zip：只保留 theme.json 与四张固定图片，
     * Nova 专有条目（底栏图标、卡片贴纸、商店预览等）丢弃；
     * 背景开关缺省时按实际存在的图片补 true，避免「图在、但不显示」。
     */
    fun normalizeToPlainZip(bytes: ByteArray): ByteArray {
        val payload = if (isEncrypted(bytes)) decrypt(bytes) else bytes
        if (payload.size > MAX_PAYLOAD_BYTES) {
            throw ThemePackageException.TooLarge("主题包超过 ${MAX_PAYLOAD_BYTES / 1024 / 1024} MB")
        }

        val collected = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(payload)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (!ThemePackageArchive.isSafeEntryName(entry.name)) {
                        throw ThemePackageException.EntryRejected(entry.name, "路径不合法")
                    }
                    val mapped = ENTRY_ALIASES[entry.name] ?: entry.name
                    val accepted = mapped == ThemePackageFormat.THEME_ENTRY || mapped in ThemePackageFormat.IMAGE_ENTRIES
                    if (accepted) {
                        val limit = if (mapped == ThemePackageFormat.THEME_ENTRY) {
                            ThemePackageFormat.MAX_MANIFEST_BYTES.toInt()
                        } else {
                            ThemePackageFormat.MAX_IMAGE_BYTES.toInt()
                        }
                        // 超大图片（Nova 的卡片底图常见十几 MB）只丢该图，不让整包导入失败。
                        val data = try {
                            zipIn.readEntryBytes(limit, entry.name)
                        } catch (e: ThemePackageException.TooLarge) {
                            if (mapped == ThemePackageFormat.THEME_ENTRY) throw e
                            null
                        }
                        if (data != null) {
                            total += data.size
                            if (total > ThemePackageFormat.MAX_TOTAL_BYTES) {
                                throw ThemePackageException.TooLarge("解压后超过 ${ThemePackageFormat.MAX_TOTAL_BYTES / 1024 / 1024} MB")
                            }
                            collected[mapped] = data
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val manifest = collected[ThemePackageFormat.THEME_ENTRY]
            ?: throw ThemePackageException.MissingManifest()
        collected[ThemePackageFormat.THEME_ENTRY] = enrichManifest(
            manifest,
            hasBackgroundImage = collected.containsKey(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY) ||
                collected.containsKey(ThemePackageFormat.BACKGROUND_DARK_ENTRY),
            hasCardImage = collected.containsKey(ThemePackageFormat.CARD_LIGHT_ENTRY) ||
                collected.containsKey(ThemePackageFormat.CARD_DARK_ENTRY),
        )

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zipOut ->
            collected.forEach { (name, data) ->
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(data)
                zipOut.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ZipInputStream.readEntryBytes(limit: Int, entryName: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw ThemePackageException.TooLarge("条目 $entryName 超过 ${limit / 1024} KB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun enrichManifest(
        data: ByteArray,
        hasBackgroundImage: Boolean,
        hasCardImage: Boolean,
    ): ByteArray {
        val element = runCatching { json.parseToJsonElement(String(data, Charsets.UTF_8)) }.getOrNull()
        if (element !is JsonObject) {
            throw ThemePackageException.MalformedManifest("不是 JSON 对象")
        }
        val declaredVersion = element["version"]?.jsonPrimitive?.intOrNull
        val merged = buildJsonObject {
            element.forEach { (key, value) -> put(key, value) }
            if (declaredVersion == null || declaredVersion > ThemePackageFormat.CURRENT_VERSION) {
                put("version", JsonPrimitive(ThemePackageFormat.CURRENT_VERSION))
            }
            if (element["hasBackground"] == null && hasBackgroundImage) {
                put("hasBackground", JsonPrimitive(true))
            }
            if (element["hasCardBackground"] == null && hasCardImage) {
                put("hasCardBackground", JsonPrimitive(true))
            }
            clampPercent(element, this, "cardBackgroundAlphaPercent")
            clampPercent(element, this, "customBackgroundBlurPercent")
            clampPercent(element, this, "customBackgroundScrimPercent")
        }
        return json.encodeToString(JsonObject.serializer(), merged).toByteArray(Charsets.UTF_8)
    }

    /** Nova 允许负的 scrim 表示「提亮」；本项目只接受 0–100，超出即夹取，避免背景被画反。 */
    private fun clampPercent(source: JsonObject, target: JsonObjectBuilder, key: String) {
        val value = source[key]?.jsonPrimitive?.intOrNull ?: return
        target.put(key, JsonPrimitive(value.coerceIn(0, 100)))
    }
}
