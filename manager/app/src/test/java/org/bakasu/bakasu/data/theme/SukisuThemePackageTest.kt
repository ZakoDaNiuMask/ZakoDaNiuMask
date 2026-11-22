package org.bakasu.bakasu.data.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SukisuThemePackageTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("sukisu-theme").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `encrypted sukisu package imports through storage`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":5,"keyColor":305419896,"amoled":true,"hasBackground":true}"""),
                "background_light" to ByteArray(16) { 1 },
            ),
        )

        val normalized = SukisuThemePackage.normalizeToPlainZip(encryptNova(plain))
        val storage = ThemePackageStorage(dir)
        val stored = storage.importPackage(ByteArrayInputStream(normalized), "theme_nova", ImageSizeProbe { 64 to 64 })

        assertEquals(305419896, stored.manifest.sharedVariant().keyColor)
        assertEquals(true, stored.manifest.amoled)
        assertEquals(3, stored.manifest.version)
        assertTrue(stored.images.contains(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = encryptNova(buildZip(mapOf("theme.json" to manifestJson("""{"keyColor":1}"""))))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0xFF).toByte()

        val failure = runCatching { SukisuThemePackage.normalizeToPlainZip(encrypted) }.exceptionOrNull()

        assertTrue(failure is ThemePackageException.IntegrityFailure)
    }

    @Test
    fun `missing magic header is rejected`() {
        val failure = runCatching { SukisuThemePackage.decrypt(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }.exceptionOrNull()

        assertTrue(failure is ThemePackageException.IntegrityFailure)
    }

    @Test
    fun `nova specific fields and entries are ignored`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson(
                    """
                    {"version":5,"keyColor":1193046,"miuixMonet":true,"homeLayout":"immersive",
                     "homeStatusScale":1.2,"bottomBarStyle":"floating","amoled":false}
                    """.trimIndent(),
                ),
                "bottom_bar_icon_0" to ByteArray(8) { 2 },
                "theme_import_preview" to ByteArray(8) { 3 },
            ),
        )

        val normalized = SukisuThemePackage.normalizeToPlainZip(plain)
        val manifest = ThemePackageArchive.decodeManifest(
            entryBytes(normalized, ThemePackageFormat.THEME_ENTRY)!!,
        )

        assertEquals(1193046, manifest.sharedVariant().keyColor)
        assertEquals(ThemePackageFormat.CURRENT_VERSION, manifest.version)
        assertFalse(entryNames(normalized).contains("bottom_bar_icon_0"))
        assertFalse(entryNames(normalized).contains("theme_import_preview"))
    }

    @Test
    fun `plain zip renamed to sukisu suffix still imports`() {
        val plain = buildZip(mapOf("theme.json" to manifestJson("""{"version":2,"keyColor":255}""")))

        assertFalse(SukisuThemePackage.isEncrypted(plain))
        val normalized = SukisuThemePackage.normalizeToPlainZip(plain)
        val stored = ThemePackageStorage(dir).importPackage(
            ByteArrayInputStream(normalized),
            "theme_plain",
            ImageSizeProbe { 64 to 64 },
        )

        assertEquals(255, stored.manifest.sharedVariant().keyColor)
    }

    @Test
    fun `single background entry becomes light background`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":2,"keyColor":7,"adaptive":false,"hasBackground":true}"""),
                "background" to WEBP_BYTES,
                "card_background" to PNG_BYTES,
            ),
        )

        val normalized = SukisuThemePackage.normalizeToPlainZip(plain)
        val names = entryNames(normalized)
        val manifest = ThemePackageArchive.decodeManifest(
            entryBytes(normalized, ThemePackageFormat.THEME_ENTRY)!!,
        )

        assertTrue(names.contains(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY))
        assertTrue(names.contains(ThemePackageFormat.CARD_LIGHT_ENTRY))
        assertEquals(2, manifest.version)
    }

    @Test
    fun `background with extension is mapped`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":3,"keyColor":9,"hasBackground":true}"""),
                "background.png" to PNG_BYTES,
                "background_dark.jpg" to JPG_BYTES,
            ),
        )

        val names = entryNames(SukisuThemePackage.normalizeToPlainZip(plain))

        assertTrue(names.contains(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY))
        assertTrue(names.contains(ThemePackageFormat.BACKGROUND_DARK_ENTRY))
    }

    @Test
    fun `missing hasBackground is inferred from images and negative scrim clamped`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":2,"keyColor":11,"customBackgroundScrimPercent":-25}"""),
                "background" to WEBP_BYTES,
            ),
        )

        val normalized = SukisuThemePackage.normalizeToPlainZip(plain)
        val manifest = ThemePackageArchive.decodeManifest(
            entryBytes(normalized, ThemePackageFormat.THEME_ENTRY)!!,
        )

        assertEquals(true, manifest.hasBackground)
        assertEquals(0, manifest.sharedVariant().customBackgroundScrimPercent)
    }

    @Test
    fun `oversized optional image is skipped instead of failing`() {
        val oversized = ByteArray(ThemePackageFormat.MAX_IMAGE_BYTES.toInt() + 1)
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":2,"keyColor":17,"hasBackground":true}"""),
                "card_background" to oversized,
                "background" to WEBP_BYTES,
            ),
        )

        val names = entryNames(SukisuThemePackage.normalizeToPlainZip(plain))

        assertTrue(names.contains(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY))
        assertFalse(names.contains(ThemePackageFormat.CARD_LIGHT_ENTRY))
    }

    @Test
    fun `applier resolves the imported background file`() {
        val plain = buildZip(
            mapOf(
                "theme.json" to manifestJson("""{"version":2,"keyColor":13,"hasBackground":true}"""),
                "background" to WEBP_BYTES,
            ),
        )
        val normalized = SukisuThemePackage.normalizeToPlainZip(plain)
        val stored = ThemePackageStorage(dir).importPackage(
            ByteArrayInputStream(normalized),
            "theme_bg",
            ImageSizeProbe { 64 to 64 },
        )

        val resolved = ThemeApplier.resolve(stored.manifest, stored.dir, dark = false)

        assertTrue(resolved.backgroundFile?.isFile == true)
    }

    private fun manifestJson(body: String): ByteArray = body.toByteArray(Charsets.UTF_8)

    private fun encryptNova(plain: ByteArray): ByteArray {
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(NOVA_KEY_HASH.copyOfRange(0, 32), "AES"),
            GCMParameterSpec(128, iv),
        )
        val cipherText = cipher.doFinal(plain)
        return "SKTH".toByteArray(Charsets.US_ASCII) + iv + cipherText
    }

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entryNames(zip: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                names += entry.name
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return names
    }

    private fun entryBytes(zip: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (entry.name == name) return input.readBytes()
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return null
    }

    private companion object {
        val WEBP_BYTES = "RIFF".toByteArray(Charsets.US_ASCII) + ByteArray(4) + "WEBP".toByteArray(Charsets.US_ASCII)
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val JPG_BYTES = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())

        val NOVA_KEY_HASH = byteArrayOf(
            0x41.toByte(), 0xdc.toByte(), 0x17.toByte(), 0x4e.toByte(), 0x50.toByte(), 0x96.toByte(), 0xb4.toByte(), 0xee.toByte(),
            0x93.toByte(), 0x31.toByte(), 0x1c.toByte(), 0xc8.toByte(), 0x92.toByte(), 0xb1.toByte(), 0x32.toByte(), 0xe2.toByte(),
            0x19.toByte(), 0x61.toByte(), 0x01.toByte(), 0x2b.toByte(), 0x14.toByte(), 0xcf.toByte(), 0x21.toByte(), 0x79.toByte(),
            0x54.toByte(), 0x0f.toByte(), 0x20.toByte(), 0x3c.toByte(), 0xa7.toByte(), 0x2a.toByte(), 0xb9.toByte(), 0xc1.toByte(),
            0xd9.toByte(), 0xcd.toByte(), 0xa5.toByte(), 0x45.toByte(), 0x2e.toByte(), 0x58.toByte(), 0xe6.toByte(), 0xdf.toByte(),
            0x56.toByte(), 0x79.toByte(), 0xa1.toByte(), 0x46.toByte(), 0x4d.toByte(), 0x44.toByte(), 0x0a.toByte(), 0x0a.toByte(),
        )
    }
}
