// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.backup

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray

/**
 * Packs each installed module directory into a zip artifact. Reads the module
 * tree through a root shell (libsu), so it works regardless of app sandboxing.
 */
class ModuleBackupSource(
    private val listModules: () -> String,
    private val rootShell: () -> Shell,
) : BackupSource {

    override val kind: BackupKind = BackupKind.MODULE

    override suspend fun list(): Result<List<BackupEntry>> = Result.success(emptyList())

    override suspend fun export(): Result<List<BackupArtifact>> = runCatching {
        val modules = runCatching { JSONArray(listModules()) }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        buildList {
            for (i in 0 until modules.length()) {
                val module = modules.optJSONObject(i) ?: continue
                val id = module.optString("id").ifBlank { continue }
                val version = module.optString("version")
                val versionCode = module.optInt("versionCode", 0)
                val bytes = runCatching { zipDirectory("/data/adb/modules/$id") }.getOrNull() ?: continue
                add(
                    BackupArtifact(
                        kind = BackupKind.MODULE,
                        moduleId = id,
                        fileName = moduleArchiveName(id, version, versionCode, now),
                        metaFileName = null,
                        bytes = bytes,
                        sha256 = bytes.sha256(),
                        metaJson = null,
                    )
                )
            }
        }
    }

    override suspend fun restore(entry: BackupEntry): Result<Unit> =
        Result.failure(UnsupportedOperationException("restore is handled by the view model"))

    override suspend fun delete(entry: BackupEntry): Result<Unit> = Result.success(Unit)

    private fun zipDirectory(rootPath: String): ByteArray {
        val shell = rootShell()
        val root = SuFile(rootPath).apply { this.shell = shell }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            addDirectory(root, rootPath, shell, zip)
        }
        return out.toByteArray()
    }

    private fun addDirectory(
        dir: SuFile,
        rootPath: String,
        shell: Shell,
        zip: ZipOutputStream,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val path = child.path
            val relative = path.removePrefix(rootPath).trimStart('/')
            if (relative.isEmpty()) continue
            if (child.isDirectory) {
                zip.putNextEntry(ZipEntry("$relative/"))
                zip.closeEntry()
                addDirectory(child, rootPath, shell, zip)
            } else {
                runCatching {
                    zip.putNextEntry(ZipEntry(relative))
                    SuFileInputStream.open(
                        SuFile(path).apply { this.shell = shell }
                    ).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}
