/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring): /proc identity, cmdline and keyword
 * scanning. The native uname/ARM64 CPU identity probes and CVE patch database are not ported.
 */

package com.zakodaniumask.manager.data.kernel

import com.zakodaniumask.manager.data.detection.DetectorStatus
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class KernelFindingKind {
    EMOJI,
    CHINESE_CHARS,
    NON_LATIN_SCRIPTS,
    TELEGRAM_REF,
    AT_MENTION,
    CUSTOM_KERNEL,
    NON_RELEASE_KERNEL_VERSION,
    SUSPICIOUS_CMDLINE,
    KPTR_EXPOSED,
    KERNEL_IDENTITY_MISMATCH,
}

enum class KernelFindingSeverity { HARD, INFO }

data class KernelFinding(
    val kind: KernelFindingKind,
    val label: String,
    val value: String,
    val detail: String? = null,
    val severity: KernelFindingSeverity,
)

data class KernelCheckReport(
    val status: DetectorStatus,
    val unameOutput: String,
    val procVersion: String,
    val procCmdline: String,
    val findings: List<KernelFinding>,
    val kptrRestrict: String? = null,
    val error: String? = null,
) {
    val hardFindings: List<KernelFinding>
        get() = findings.filter { it.severity == KernelFindingSeverity.HARD }

    val infoFindings: List<KernelFinding>
        get() = findings.filter { it.severity == KernelFindingSeverity.INFO }

    val hasHardIndicators: Boolean
        get() = hardFindings.isNotEmpty()

    companion object {
        fun failed(message: String): KernelCheckReport = KernelCheckReport(
            status = DetectorStatus.ERROR,
            unameOutput = "",
            procVersion = "",
            procCmdline = "",
            findings = emptyList(),
            error = message,
        )
    }
}

class KernelCheckDetector {

    suspend fun scan(): KernelCheckReport = withContext(Dispatchers.IO) {
        runCatching { scanInternal() }
            .getOrElse { throwable ->
                KernelCheckReport.failed(throwable.message ?: "Kernel check failed.")
            }
    }

    private fun scanInternal(): KernelCheckReport {
        val procVersion = readFile(PROC_VERSION_PATH).orEmpty()
        val procCmdline = readFile(PROC_CMDLINE_PATH).orEmpty()
        val kptr = readFile(KPTR_RESTRICT_PATH)?.trim()
        val unameOutput = readUname().orEmpty()

        val combined = buildString {
            appendLine(unameOutput)
            appendLine(procVersion)
        }

        val findings = buildList {
            if (KernelIdentityRules.EMOJI_REGEX.containsMatchIn(combined)) {
                add(hard(KernelFindingKind.EMOJI, "Emoji in kernel identity", firstMatch(combined)))
            }
            if (KernelIdentityRules.CHINESE_REGEX.containsMatchIn(combined)) {
                add(hard(KernelFindingKind.CHINESE_CHARS, "CJK characters in kernel identity", firstMatch(combined)))
            }
            if (KernelIdentityRules.NON_LATIN_REGEX.containsMatchIn(combined)) {
                add(hard(KernelFindingKind.NON_LATIN_SCRIPTS, "Non-Latin script in kernel identity", firstMatch(combined)))
            }
            if (KernelIdentityRules.TELEGRAM_REGEX.containsMatchIn(combined)) {
                add(hard(KernelFindingKind.TELEGRAM_REF, "Telegram reference in kernel identity", firstMatch(combined)))
            }
            KernelIdentityRules.MENTION_REGEX.find(combined)?.let {
                add(hard(KernelFindingKind.AT_MENTION, "Mention in kernel identity", it.value))
            }

            val keywords = KernelIdentityRules.detectCustomKernelKeywords(combined)
            if (keywords.isNotEmpty()) {
                add(
                    hard(
                        KernelFindingKind.CUSTOM_KERNEL,
                        "Known custom kernel keyword",
                        keywords.joinToString(", "),
                    )
                )
            }

            KernelIdentityRules.detectNonReleaseKernelMajorVersion(unameOutput)?.let { major ->
                add(hard(KernelFindingKind.NON_RELEASE_KERNEL_VERSION, "Non-release kernel major", major))
            }

            val cmdlineHits = KernelIdentityRules.detectCriticalCmdlineFallback(procCmdline)
            if (cmdlineHits.isNotEmpty()) {
                add(
                    hard(
                        KernelFindingKind.SUSPICIOUS_CMDLINE,
                        "Suspicious boot command line",
                        cmdlineHits.joinToString("; "),
                    )
                )
            }

            detectIdentityMismatch(unameOutput, procVersion)?.let { mismatch ->
                add(hard(KernelFindingKind.KERNEL_IDENTITY_MISMATCH, "Kernel identity mismatch", mismatch))
            }

            if (kptr == "0") {
                add(
                    KernelFinding(
                        kind = KernelFindingKind.KPTR_EXPOSED,
                        label = "kptr_restrict",
                        value = "0",
                        detail = "Kernel pointers are exposed to unprivileged readers.",
                        severity = KernelFindingSeverity.INFO,
                    )
                )
            }
        }

        val observed = procVersion.isNotBlank() || procCmdline.isNotBlank() || unameOutput.isNotBlank()
        val status = when {
            findings.any { it.severity == KernelFindingSeverity.HARD } -> DetectorStatus.DANGER
            findings.isNotEmpty() -> DetectorStatus.SUPPORT
            !observed -> DetectorStatus.INFO
            else -> DetectorStatus.CLEAR
        }

        return KernelCheckReport(
            status = status,
            unameOutput = unameOutput.trim(),
            procVersion = procVersion.trim(),
            procCmdline = procCmdline.trim(),
            findings = findings,
            kptrRestrict = kptr,
        )
    }

    private fun detectIdentityMismatch(unameOutput: String, procVersion: String): String? {
        val unameRelease = KernelIdentityRules.extractReleaseToken(unameOutput)
        val procRelease = KernelIdentityRules.extractReleaseToken(procVersion)
        if (unameRelease.isNullOrBlank() || procRelease.isNullOrBlank()) return null
        return if (unameRelease != procRelease) {
            "uname=$unameRelease / proc=$procRelease"
        } else {
            null
        }
    }

    private fun firstMatch(source: String): String =
        KernelIdentityRules.MENTION_REGEX.find(source)?.value
            ?: source.lineSequence().firstOrNull()?.take(80).orEmpty()

    private fun hard(kind: KernelFindingKind, label: String, value: String) =
        KernelFinding(kind = kind, label = label, value = value, severity = KernelFindingSeverity.HARD)

    private fun readUname(): String? {
        var process: Process? = null
        val output = try {
            process = ProcessBuilder("uname", "-a")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                text.ifBlank { null }
            } else {
                process.destroyForcibly()
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            process?.destroy()
        }
        // Fall back to the kernel release the runtime captured when it started.
        return output ?: System.getProperty("os.version")
    }

    private fun readFile(path: String): String? = try {
        val file = File(path)
        if (file.exists() && file.canRead()) file.readText() else null
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val PROCESS_TIMEOUT_SECONDS = 3L
        private const val PROC_VERSION_PATH = "/proc/version"
        private const val PROC_CMDLINE_PATH = "/proc/cmdline"
        private const val KPTR_RESTRICT_PATH = "/proc/sys/kernel/kptr_restrict"
    }
}
