/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Selects the backend used for shell execution.
 *
 * Root always wins: when the KernelSU root shell is available every command runs as root, exactly
 * as before. Only when the device is NOT rooted does the manager fall back to the rootless
 * (Axeron/shell-uid) backend ported from AxManager. There is no manual switch.
 */

package com.zakodaniumask.manager.data.privilege

import com.topjohnwu.superuser.Shell
import com.zakodaniumask.manager.axeron.AxClient
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.PrivBackend
import com.zakodaniumask.manager.domain.model.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivilegeManager(
    private val ksuCliRepository: KsuCliRepository,
) {
    fun isRootAvailable(): Boolean =
        runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(false)

    /** Root first; rootless only when there is no root; otherwise unavailable. */
    fun current(): PrivBackend = when {
        isRootAvailable() -> PrivBackend.ROOT
        AxClient.isRunning() -> PrivBackend.ROOTLESS
        else -> PrivBackend.NONE
    }

    suspend fun exec(command: String, globalMnt: Boolean = false): ShellResult =
        withContext(Dispatchers.IO) {
            when (current()) {
                PrivBackend.ROOT -> {
                    val result = ksuCliRepository.withNewRootShell(globalMnt) {
                        newJob().add(command).to(ArrayList(), ArrayList()).exec()
                    }
                    ShellResult(
                        code = result.code,
                        stdout = result.out.joinToString("\n"),
                        stderr = result.err.joinToString("\n"),
                    )
                }

                PrivBackend.ROOTLESS -> {
                    val output = AxClient.exec(command)
                    ShellResult(
                        code = if (output != null) 0 else -1,
                        stdout = output.orEmpty(),
                        stderr = "",
                    )
                }

                PrivBackend.NONE -> ShellResult(
                    code = -1,
                    stdout = "",
                    stderr = "No privileged backend available (root required or activate rootless mode)",
                )
            }
        }

    /** A libsu shell. Only the root backend can provide one. */
    fun newRootShell(globalMnt: Boolean): Shell = ksuCliRepository.createRootShell(globalMnt)
}
