package com.zakodaniumask.manager.data.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ThemeApplierTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("theme-apply").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `adaptive package picks dark variant and dark background`() {
        writeImage(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
        writeImage(ThemePackageFormat.BACKGROUND_DARK_ENTRY)

        val resolved = ThemeApplier.resolve(adaptivePackage(), dir, dark = true)

        assertEquals(2, resolved.variant.themeMode)
        assertEquals(2, resolved.variant.keyColor)
        assertEquals(ThemePackageFormat.BACKGROUND_DARK_ENTRY, resolved.backgroundFile?.name)
    }

    @Test
    fun `adaptive package picks light variant and light background`() {
        writeImage(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
        writeImage(ThemePackageFormat.BACKGROUND_DARK_ENTRY)

        val resolved = ThemeApplier.resolve(adaptivePackage(), dir, dark = false)

        assertEquals(1, resolved.variant.themeMode)
        assertEquals(1, resolved.variant.keyColor)
        assertEquals(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY, resolved.backgroundFile?.name)
    }

    @Test
    fun `single variant package uses top level fields`() {
        writeImage(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
        val pkg = ThemePackageData(version = 2, keyColor = 7, hasBackground = true)

        val resolved = ThemeApplier.resolve(pkg, dir, dark = true)

        assertEquals(7, resolved.variant.keyColor)
        assertEquals(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY, resolved.backgroundFile?.name)
    }

    @Test
    fun `hasBackground false drops the image even when present`() {
        writeImage(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY)
        val pkg = ThemePackageData(version = 2, keyColor = 7, hasBackground = false)

        assertNull(ThemeApplier.resolve(pkg, dir, dark = false).backgroundFile)
    }

    @Test
    fun `missing image yields null background`() {
        assertNull(ThemeApplier.resolve(adaptivePackage(), dir, dark = true).backgroundFile)
    }

    @Test
    fun `exported appearance survives zip round trip`() {
        val exported = ThemeExporter.build(
            name = "当前外观",
            author = null,
            dark = true,
            seedColor = 0x123456,
            colorStyle = "TONAL_SPOT",
            cardAlphaPercent = 60,
            blurPercent = 30,
            scrimPercent = 20,
            hasBackground = true,
            amoled = true,
        )
        val out = ByteArrayOutputStream()
        ThemePackageArchive.write(
            exported,
            mapOf(ThemePackageFormat.BACKGROUND_LIGHT_ENTRY to ByteArray(8) { 1 }),
            out,
        )

        val read = ThemePackageArchive.read(ByteArrayInputStream(out.toByteArray()), ImageSizeProbe { 64 to 64 })

        assertEquals(exported, read.manifest)
        assertEquals(0x123456, read.manifest.sharedVariant().keyColor)
        assertEquals(2, read.manifest.themeMode)
        assertEquals(true, read.manifest.amoled)
        assertEquals(60, read.manifest.cardBackgroundAlphaPercent)
    }

    private fun adaptivePackage(): ThemePackageData = ThemePackageData(
        version = ThemePackageFormat.CURRENT_VERSION,
        adaptive = true,
        light = ThemeVariantData(themeMode = 1, keyColor = 1, hasBackground = true),
        dark = ThemeVariantData(themeMode = 2, keyColor = 2, hasBackground = true),
    )

    private fun writeImage(name: String) {
        File(dir, name).writeBytes(ByteArray(8) { 1 })
    }
}
