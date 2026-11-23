package com.zakodaniumask.manager.data.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackageArchiveTest {
    private val smallImage = ImageSizeProbe { 512 to 512 }

    @Test
    fun `round trip keeps adaptive fields and images`() {
        val data = ThemePackageData(
            version = ThemePackageFormat.CURRENT_VERSION,
            name = "暮色",
            author = "futaloli",
            adaptive = true,
            light = ThemeVariantData(
                themeMode = 1,
                keyColor = 0xFF7C4E7E.toInt(),
                colorStyle = "TONAL_SPOT",
                cardBackgroundAlphaPercent = 60,
                customBackgroundBlurPercent = 35,
                customBackgroundScrimPercent = 20,
                hasBackground = true,
                hasCardBackground = false,
                enableBlur = true,
                enableProgressiveBlur = false,
                amoled = false,
            ),
            dark = ThemeVariantData(
                themeMode = 2,
                keyColor = 0xFF2B2B2B.toInt(),
                colorStyle = "NEUTRAL",
                cardBackgroundAlphaPercent = 40,
                customBackgroundBlurPercent = 50,
                customBackgroundScrimPercent = 30,
                hasBackground = true,
                hasCardBackground = true,
                enableBlur = true,
                enableProgressiveBlur = true,
                amoled = true,
            ),
        )
        val images = mapOf(
            ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to ByteArray(128) { 1 },
            ThemePackageFormat.BACKGROUND_DARK_ENTRY to ByteArray(256) { 2 },
        )

        val out = ByteArrayOutputStream()
        ThemePackageArchive.write(data, images, out)
        val archive = ThemePackageArchive.read(ByteArrayInputStream(out.toByteArray()), smallImage)

        assertEquals(data, archive.manifest)
        assertEquals(images.keys, archive.images.keys)
        assertArrayEquals(images[ThemePackageFormat.BACKGROUND_LIGHT_ENTRY], archive.images[ThemePackageFormat.BACKGROUND_LIGHT_ENTRY])
        assertEquals(true, archive.manifest.isAdaptive)
        assertEquals(data.dark, archive.manifest.variantFor(dark = true))
    }

    @Test
    fun `v2 single variant package is readable`() {
        val json = """
            {"version":2,"name":"旧主题","themeMode":2,"keyColor":123,"hasBackground":true,"enableBlur":true}
        """.trimIndent()
        val archive = ThemePackageArchive.read(
            ByteArrayInputStream(zipOf(mapOf("theme.json" to json.toByteArray()))),
            smallImage,
        )

        assertEquals(2, archive.manifest.version)
        assertEquals("旧主题", archive.manifest.name)
        assertEquals(123, archive.manifest.sharedVariant().keyColor)
        assertEquals(false, archive.manifest.isAdaptive)
        assertEquals(null, archive.manifest.variantFor(dark = true))
    }

    @Test
    fun `rejects path traversal entry`() {
        val bytes = zipOf(
            mapOf(
                "theme.json" to manifestBytes(),
                "../escape.txt" to ByteArray(4),
            ),
        )
        assertThrows(ThemePackageException.EntryRejected::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects absolute path entry`() {
        val bytes = zipOf(
            mapOf(
                "theme.json" to manifestBytes(),
                "/etc/passwd" to ByteArray(4),
            ),
        )
        assertThrows(ThemePackageException.EntryRejected::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects entry outside allow list`() {
        val bytes = zipOf(
            mapOf(
                "theme.json" to manifestBytes(),
                "extra.bin" to ByteArray(4),
            ),
        )
        assertThrows(ThemePackageException.EntryRejected::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects package without manifest`() {
        val bytes = zipOf(mapOf(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to ByteArray(8)))
        assertThrows(ThemePackageException.MissingManifest::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects unsupported version`() {
        val bytes = zipOf(mapOf("theme.json" to """{"version":99}""".toByteArray()))
        assertThrows(ThemePackageException.UnsupportedVersion::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects oversized image bytes`() {
        val bytes = zipOf(
            mapOf(
                "theme.json" to manifestBytes(),
                ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to ByteArray(ThemePackageFormat.MAX_IMAGE_BYTES.toInt() + 1),
            ),
        )
        assertThrows(ThemePackageException.TooLarge::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), smallImage)
        }
    }

    @Test
    fun `rejects oversized image dimensions`() {
        val bytes = zipOf(
            mapOf(
                "theme.json" to manifestBytes(),
                ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to ByteArray(16) { 7 },
            ),
        )
        assertThrows(ThemePackageException.InvalidImage::class.java) {
            ThemePackageArchive.read(ByteArrayInputStream(bytes), ImageSizeProbe { 8000 to 100 })
        }
    }

    @Test
    fun `safe entry name guards traversal shapes`() {
        assertTrue(ThemePackageArchive.isSafeEntryName("theme.json"))
        assertTrue(ThemePackageArchive.isSafeEntryName("background_light.webp"))
        listOf("../a", "a/../b", "/abs", "C:evil", "a\\b", "a//b", "").forEach { name ->
            assertEquals("应拒绝 $name", false, ThemePackageArchive.isSafeEntryName(name))
        }
    }

    private fun manifestBytes(): ByteArray = ThemePackageArchive.encodeManifest(ThemePackageData(version = 2, name = "t"))

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
