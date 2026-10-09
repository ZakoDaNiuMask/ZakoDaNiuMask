// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store/src/props.rs; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import com.topjohnwu.superuser.Shell
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PropSaveResult(
    val appliedBootHash: String?,
    val syncedBackendPolicy: Boolean,
)

class TrickyPropsRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val trickyStoreRepository: TrickyStoreRepository,
) {
    suspend fun save(propHandlerEnabled: Boolean, bootHash: String?): Result<PropSaveResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shell = ksuCliRepository.getRootShell()
                val normalized = bootHash?.trim()?.takeIf { it.isNotEmpty() }
                if (normalized != null) {
                    require(normalized.length == 64 && normalized.all { it.isDigit() || it in 'a'..'f' }) {
                        "boot hash must be 64 lowercase hex characters"
                    }
                    SuFileUtils.writeText(shell, TrickystorePaths.BOOT_HASH, normalized)
                } else {
                    SuFileUtils.delete(shell, TrickystorePaths.BOOT_HASH)
                }

                val disablePath = TrickystorePaths.PROP_HANDLER_DISABLE
                if (propHandlerEnabled) {
                    SuFileUtils.delete(shell, disablePath)
                } else {
                    SuFileUtils.writeText(shell, disablePath, "")
                }

                var applied: String? = null
                if (propHandlerEnabled && normalized != null) {
                    applyBootHash(shell, normalized)
                    applied = normalized
                }

                val synced = syncBackendPolicy(shell, if (propHandlerEnabled) normalized else null)
                PropSaveResult(applied, synced)
            }
        }

    private fun applyBootHash(shell: Shell, hash: String) {
        val daemon = ksuCliRepository.getKsuDaemonPath()
        shell.newJob()
            .add("$daemon resetprop -n ro.boot.vbmeta.digest ${SuFileUtils.shq(hash)}")
            .exec()
        shell.newJob()
            .add("$daemon resetprop -c -Z ro.boot.vbmeta.digest")
            .exec()
    }

    /** Mirrors the hash into the active backend's `vb_hash` policy when it exposes one. */
    private fun syncBackendPolicy(shell: Shell, hash: String?): Boolean {
        val active = trickyStoreRepository.detectActive(shell) ?: return false
        val adapter = Backends.forBackend(active.backend)
        val schema = adapter.policySchema()
        if (schema.defaultPolicy.none { it.key == "vb_hash" }) return false
        val config = runCatching { adapter.read(shell) }.getOrNull() ?: return false
        val policy = config.defaultPolicy.toMutableMap()
        if (hash == null) {
            if (!policy.containsKey("vb_hash")) return false
            policy["vb_hash"] = "auto"
        } else {
            policy["vb_hash"] = hash
        }
        return adapter.write(shell, config.copy(defaultPolicy = policy)).isSuccess
    }
}
