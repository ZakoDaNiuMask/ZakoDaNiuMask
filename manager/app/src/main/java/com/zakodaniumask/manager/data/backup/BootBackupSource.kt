// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.backup

import android.content.Context
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import java.io.File
import java.security.MessageDigest

/**
 * Backs up the current boot partition image to a `.img` artifact. Uses the
 * ksud `flash backup` command, so it needs root and a populated boot partition.
 */
class BootBackupSource(
    private val context: Context,
    private val ksuCliRepository: KsuCliRepository,
) : BackupSource {

    override val kind: BackupKind = BackupKind.BOOT

    override suspend fun list(): Result<List<BackupEntry>> = Result.success(emptyList())

    override suspend fun export(): Result<List<BackupArtifact>> = runCatching {
        val partition = ksuCliRepository.defaultBootPartition().ifBlank { "boot" }
        val scratch = File(context.filesDir, "boot_backup_${System.currentTimeMillis()}.img")
        try {
            check(ksuCliRepository.flashBackup(partition, scratch.absolutePath)) {
                "ksud flash backup failed"
            }
            val bytes = scratch.readBytes()
            check(bytes.isNotEmpty()) { "boot backup is empty" }
            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            listOf(
                BackupArtifact(
                    kind = BackupKind.BOOT,
                    moduleId = null,
                    fileName = bootBackupName(partition, sha1),
                    metaFileName = null,
                    bytes = bytes,
                    sha256 = bytes.sha256(),
                    metaJson = null,
                )
            )
        } finally {
            scratch.delete()
        }
    }

    override suspend fun restore(entry: BackupEntry): Result<Unit> =
        Result.failure(UnsupportedOperationException("restore is handled by the view model"))

    override suspend fun delete(entry: BackupEntry): Result<Unit> = Result.success(Unit)
}
