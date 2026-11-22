package org.bakasu.bakasu.data.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemePackageStorageTest {
    private lateinit var root: File
    private lateinit var storage: ThemePackageStorage
    private val probe = ImageSizeProbe { 256 to 256 }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("theme-storage").toFile()
        storage = ThemePackageStorage(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `import writes files and export reproduces manifest`() {
        val stored = storage.importPackage(ByteArrayInputStream(samplePackage()), "dusk-01", probe)

        assertEquals("暮色", stored.manifest.name)
        assertTrue(File(stored.dir, ThemePackageFormat.THEME_ENTRY).isFile)
        assertTrue(File(stored.dir, ThemePackageFormat.BACKGROUND_DARK_ENTRY).isFile)
        assertEquals(1, storage.list().size)

        val out = ByteArrayOutputStream()
        storage.exportPackage("dusk-01", out)
        val again = ThemePackageArchive.read(ByteArrayInputStream(out.toByteArray()), probe)
        assertEquals(stored.manifest, again.manifest)
        assertEquals(stored.images, again.images.keys)

        assertTrue(storage.delete("dusk-01"))
        assertTrue(storage.list().isEmpty())
    }

    @Test
    fun `rejects traversal and leaves no staging directory`() {
        val bad = zipOf(
            mapOf(
                "theme.json" to ThemePackageArchive.encodeManifest(ThemePackageData(version = 2)),
                "../escape.txt" to ByteArray(2),
            ),
        )
        assertThrows(ThemePackageException.EntryRejected::class.java) {
            storage.importPackage(ByteArrayInputStream(bad), "bad", probe)
        }
        assertEquals(0, root.listFiles().orEmpty().count { it.name.startsWith(".staging") })
        assertTrue(storage.list().isEmpty())
    }

    @Test
    fun `rejects invalid id`() {
        assertThrows(ThemePackageException.InvalidId::class.java) {
            storage.importPackage(ByteArrayInputStream(ByteArray(0)), "../etc", probe)
        }
    }

    private fun samplePackage(): ByteArray {
        val data = ThemePackageData(
            version = ThemePackageFormat.CURRENT_VERSION,
            name = "暮色",
            adaptive = true,
            light = ThemeVariantData(themeMode = 1, keyColor = 1),
            dark = ThemeVariantData(themeMode = 2, keyColor = 2, amoled = true),
        )
        val images = mapOf(
            ThemePackageFormat.BACKGROUND_DARK_ENTRY to ByteArray(32) { 3 },
        )
        val out = ByteArrayOutputStream()
        ThemePackageArchive.write(data, images, out)
        return out.toByteArray()
    }

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
