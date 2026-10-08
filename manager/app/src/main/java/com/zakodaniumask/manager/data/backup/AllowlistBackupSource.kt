// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.backup

import com.zakodaniumask.manager.BuildConfig
import com.zakodaniumask.manager.Natives
import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository

/**
 * Exports the KernelSU allow-list (per-app root profiles) to a portable JSON
 * document. Restoration is performed by the caller after reading the artifact
 * back from a [BackupStorage].
 */
class AllowlistBackupSource(
    private val superUserRepository: SuperUserRepository,
) : BackupSource {

    override val kind: BackupKind = BackupKind.ALLOWLIST

    override suspend fun list(): Result<List<BackupEntry>> = Result.success(emptyList())

    override suspend fun export(): Result<List<BackupArtifact>> = runCatching {
        runCatching { superUserRepository.refresh() }
        val groups = superUserRepository.state.value.groups
        val entries = groups.mapNotNull { group ->
            runCatching {
                Natives.getAppProfile(group.primaryPackageName, group.uid)
                    .toDto(group.profileKey, group.primaryPackageName)
            }.getOrNull()
        }
        val document = AllowlistDocument(
            exportedAt = backupIsoTimestamp(System.currentTimeMillis()),
            appVersion = BuildConfig.VERSION_NAME,
            kernelUapiVersion = runCatching { Natives.version }.getOrDefault(0),
            entries = entries,
        )
        if (entries.isNotEmpty()) document.validate().getOrThrow()
        val bytes = document.toJson().toByteArray(Charsets.UTF_8)
        listOf(
            BackupArtifact(
                kind = BackupKind.ALLOWLIST,
                moduleId = null,
                fileName = allowlistFileName(System.currentTimeMillis()),
                metaFileName = null,
                bytes = bytes,
                sha256 = bytes.sha256(),
                metaJson = null,
            )
        )
    }

    override suspend fun restore(entry: BackupEntry): Result<Unit> =
        Result.failure(UnsupportedOperationException("restore is handled by the view model"))

    override suspend fun delete(entry: BackupEntry): Result<Unit> = Result.success(Unit)
}
