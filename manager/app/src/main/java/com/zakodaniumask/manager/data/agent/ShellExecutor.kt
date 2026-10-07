// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.shell.KsuCliRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a shell command for the agent.
 *
 * Context selection:
 *  - no root available            -> run directly (app uid / best effort)
 *  - root available, toggle off   -> uid 1000 shell (falls back to root on failure)
 *  - root available, toggle on    -> root shell
 */
class ShellExecutor(
    private val ksuCliRepository: KsuCliRepository,
    private val agentSettingsRepository: AgentSettingsRepository,
) {
    data class ShellResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        /** "root", "uid1000" or "direct". */
        val mode: String,
    )

    suspend fun exec(command: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val hasRoot = runCatching { ksuCliRepository.rootAvailable() }.getOrDefault(false)
        val allowRoot = agentSettingsRepository.load().allowRootShell
        when {
            !hasRoot -> runDirect(command, timeoutMs)
            allowRoot -> runRoot(command)
            else -> runUid1000(command)
        }
    }

    private fun runRoot(command: String): ShellResult {
        val result = runInRootShell(command)
        return ShellResult(result.stdout, result.stderr, result.exitCode, "root")
    }

    private fun runUid1000(command: String): ShellResult {
        val ksud = ksuCliRepository.getKsuDaemonPath()
        val su = "/data/adb/ksu/bin/su"
        val full = "mkdir -p /data/adb/ksu/bin; ln -sf ${quote(ksud)} $su; " +
            "$su 1000 -c ${quote(command)}"
        val result = runInRootShell(full)
        // The uid-1000 shell needs the ksud `su` path; if it did not run, fall
        // back to the root shell rather than reporting a confusing failure.
        val failedToDrop = result.exitCode == 127 ||
            result.stderr.contains("inaccessible", ignoreCase = true) ||
            result.stderr.contains("not found", ignoreCase = true) ||
            result.stderr.contains("Unknown user", ignoreCase = true)
        if (failedToDrop) {
            return runRoot(command)
        }
        return ShellResult(result.stdout, result.stderr, result.exitCode, "uid1000")
    }

    private fun runDirect(command: String, timeoutMs: Long): ShellResult {
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(false)
                .start()
        } catch (t: Throwable) {
            return ShellResult("", "failed to start sh: ${t.message}", 127, "direct")
        }
        val completed = try {
            process.waitFor(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (!completed) {
            process.destroyForcibly()
            return ShellResult("", "command timed out after ${timeoutMs}ms", 124, "direct")
        }
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        return ShellResult(out.cap(), err.cap(), process.exitValue(), "direct")
    }

    private fun runInRootShell(command: String): ShellResult = try {
        val shell = ksuCliRepository.getRootShell()
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val result = shell.newJob().add(command).to(out, err).exec()
        ShellResult(
            stdout = out.joinToString("\n").cap(),
            stderr = err.joinToString("\n").cap(),
            exitCode = result.code,
            mode = "root",
        )
    } catch (t: Throwable) {
        ShellResult("", "root shell failed: ${t.message}", 127, "root")
    }

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun String.cap(): String =
        if (length > MAX_OUTPUT) substring(0, MAX_OUTPUT) + "\n...[truncated]" else this

    private companion object {
        const val MAX_OUTPUT = 64 * 1024
    }
}
