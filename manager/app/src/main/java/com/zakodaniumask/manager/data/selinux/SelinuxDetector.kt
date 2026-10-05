/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Preliminary subset ported from Duck-Detector-Refactoring
 * (https://github.com/eltavine/Duck-Detector-Refactoring): filesystem, sysfs, getenforce and
 * proc/self/attr probes plus the enforcing/permissive paradox logic. The native dirty-policy,
 * context-validity and audit-runtime probes are not ported.
 */

package com.zakodaniumask.manager.data.selinux

import com.zakodaniumask.manager.data.detection.DetectorStatus
import com.zakodaniumask.manager.data.detection.PathState
import com.zakodaniumask.manager.data.detection.PathStat
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelinuxDetector {

    suspend fun scan(): SelinuxReport = withContext(Dispatchers.IO) {
        runCatching { scanInternal() }
            .getOrElse { throwable ->
                SelinuxReport.failed(throwable.message ?: "SELinux scan failed.")
            }
    }

    private fun scanInternal(): SelinuxReport {
        val filesystem = checkFilesystem()
        val sysfs = checkViaSysfs()
        val getenforce = checkViaGetenforce()
        val procAttr = checkViaProcAttr()
        val checks = listOf(filesystem, sysfs, getenforce, procAttr)

        val resolution = resolveStatus(checks)
        val contextRaw = readProcAttrRaw()
        val contextType = contextRaw?.trim()?.replace("\u0000", "")?.split(":")?.getOrNull(2)

        val status = when {
            resolution.mode == SelinuxMode.PERMISSIVE -> DetectorStatus.DANGER
            resolution.mode == SelinuxMode.DISABLED -> DetectorStatus.DANGER
            resolution.paradox -> DetectorStatus.DANGER
            resolution.mode == SelinuxMode.ENFORCING -> DetectorStatus.CLEAR
            else -> DetectorStatus.SUPPORT
        }

        return SelinuxReport(
            status = status,
            mode = resolution.mode,
            statusLabel = resolution.label,
            paradoxDetected = resolution.paradox,
            checks = checks,
            processContext = contextRaw,
            contextType = contextType,
        )
    }

    private fun checkFilesystem(): SelinuxCheckResult = try {
        when (PathStat.of(SELINUX_MOUNT_PATH)) {
            PathState.ABSENT -> SelinuxCheckResult(
                method = METHOD_FILESYSTEM,
                status = FILESYSTEM_NOT_MOUNTED,
                secure = false,
                detail = "/sys/fs/selinux does not exist",
            )

            PathState.NOT_OBSERVABLE -> SelinuxCheckResult(
                method = METHOD_FILESYSTEM,
                status = FILESYSTEM_NOT_OBSERVABLE,
                secure = null,
                detail = "/sys/fs/selinux could not be examined from this process",
            )

            PathState.PRESENT -> {
                val policy = File(SELINUX_POLICY_PATH)
                val enforce = File(SELINUX_STATUS_PATH)
                if (policy.exists() && enforce.exists()) {
                    SelinuxCheckResult(
                        method = METHOD_FILESYSTEM,
                        status = FILESYSTEM_ACTIVE,
                        secure = true,
                        detail = "SELinux filesystem mounted with policy nodes",
                    )
                } else {
                    SelinuxCheckResult(
                        method = METHOD_FILESYSTEM,
                        status = FILESYSTEM_MOUNTED,
                        secure = true,
                        detail = "SELinux filesystem present",
                    )
                }
            }
        }
    } catch (throwable: Throwable) {
        SelinuxCheckResult(
            method = METHOD_FILESYSTEM,
            status = STATUS_ERROR,
            secure = null,
            detail = throwable.message ?: "Filesystem check failed",
        )
    }

    private fun checkViaSysfs(): SelinuxCheckResult = try {
        val enforceFile = File(SELINUX_STATUS_PATH)
        when {
            enforceFile.exists() && enforceFile.canRead() -> when (enforceFile.readText().trim()) {
                "1" -> SelinuxCheckResult(
                    method = METHOD_SYSFS,
                    status = SELINUX_ENFORCING,
                    secure = true,
                    detail = "/sys/fs/selinux/enforce = 1",
                    readsEnforcing = true,
                )

                "0" -> SelinuxCheckResult(
                    method = METHOD_SYSFS,
                    status = SELINUX_PERMISSIVE,
                    secure = false,
                    detail = "/sys/fs/selinux/enforce = 0",
                )

                else -> SelinuxCheckResult(
                    method = METHOD_SYSFS,
                    status = STATUS_UNKNOWN,
                    secure = null,
                    detail = "Unexpected sysfs value",
                )
            }

            enforceFile.exists() -> SelinuxCheckResult(
                method = METHOD_SYSFS,
                status = BLOCKED_ENFORCING,
                secure = true,
                permissionDenied = true,
                detail = "enforce file present but unreadable",
            )

            else -> SelinuxCheckResult(
                method = METHOD_SYSFS,
                status = STATUS_NOT_FOUND,
                secure = null,
                detail = "enforce file does not exist",
            )
        }
    } catch (throwable: Throwable) {
        if (throwable.message.isPermissionDenied()) {
            SelinuxCheckResult(
                method = METHOD_SYSFS,
                status = BLOCKED_ENFORCING,
                secure = true,
                permissionDenied = true,
                detail = "Access blocked by SELinux policy",
            )
        } else {
            SelinuxCheckResult(
                method = METHOD_SYSFS,
                status = STATUS_ERROR,
                secure = null,
                detail = throwable.message ?: "sysfs check failed",
            )
        }
    }

    private fun checkViaGetenforce(): SelinuxCheckResult {
        var process: Process? = null
        return try {
            process = ProcessBuilder("getenforce").redirectErrorStream(false).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
            val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = STATUS_TIMEOUT,
                    secure = null,
                    detail = "Command timed out after ${PROCESS_TIMEOUT_SECONDS}s",
                )
            }
            when {
                stdout.equals(SELINUX_ENFORCING, ignoreCase = true) -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = SELINUX_ENFORCING,
                    secure = true,
                    detail = "Command returned Enforcing",
                    readsEnforcing = true,
                )

                stdout.equals(SELINUX_PERMISSIVE, ignoreCase = true) -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = SELINUX_PERMISSIVE,
                    secure = false,
                    detail = "Command returned Permissive",
                )

                stdout.equals(SELINUX_DISABLED, ignoreCase = true) -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = SELINUX_DISABLED,
                    secure = false,
                    detail = "Command returned Disabled",
                )

                stderr.isPermissionDenied() -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = BLOCKED_ENFORCING,
                    secure = true,
                    permissionDenied = true,
                    detail = "Command blocked by SELinux policy",
                )

                stderr.isNotBlank() -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = STATUS_ERROR,
                    secure = null,
                    detail = "stderr: $stderr",
                )

                else -> SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = STATUS_UNKNOWN,
                    secure = null,
                    detail = "Unexpected: $stdout",
                )
            }
        } catch (throwable: Throwable) {
            if (throwable.message.isPermissionDenied()) {
                SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = BLOCKED_ENFORCING,
                    secure = true,
                    permissionDenied = true,
                    detail = "Execution blocked by SELinux",
                )
            } else {
                SelinuxCheckResult(
                    method = METHOD_GETENFORCE,
                    status = STATUS_FAILED,
                    secure = null,
                    detail = throwable.message ?: "getenforce failed",
                )
            }
        } finally {
            process?.destroy()
        }
    }

    private fun checkViaProcAttr(): SelinuxCheckResult = try {
        val file = File(PROC_ATTR_PATH)
        if (file.exists() && file.canRead()) {
            classifyProcAttrContext(file.readText())
        } else {
            SelinuxCheckResult(
                method = METHOD_PROC_ATTR,
                status = STATUS_NOT_READABLE,
                secure = null,
                permissionDenied = file.exists(),
                detail = if (file.exists()) "Access denied" else "File not found",
            )
        }
    } catch (throwable: Throwable) {
        SelinuxCheckResult(
            method = METHOD_PROC_ATTR,
            status = STATUS_ERROR,
            secure = null,
            detail = throwable.message ?: "proc attr check failed",
        )
    }

    private fun classifyProcAttrContext(raw: String): SelinuxCheckResult {
        val context = raw.trim().replace("\u0000", "")
        val type = context.split(":").getOrNull(2)
        return when {
            context.isBlank() -> SelinuxCheckResult(
                method = METHOD_PROC_ATTR,
                status = STATUS_EMPTY,
                secure = null,
                detail = "Context file empty",
            )

            type == null -> SelinuxCheckResult(
                method = METHOD_PROC_ATTR,
                status = STATUS_UNKNOWN_CONTEXT,
                secure = null,
                detail = "Raw: $context",
            )

            // Zygote moves every app process into its seapp_contexts domain before app code runs.
            type == "kernel" || type == "init" -> SelinuxCheckResult(
                method = METHOD_PROC_ATTR,
                status = STATUS_UNEXPECTED_CONTEXT,
                secure = false,
                detail = "Context: $context. An app process never keeps the $type domain.",
            )

            else -> SelinuxCheckResult(
                method = METHOD_PROC_ATTR,
                status = PROC_ATTR_LABELED,
                secure = null,
                detail = "Context: $context. A labelled context shows SELinux is enabled, not whether it enforces.",
            )
        }
    }

    private fun resolveStatus(checks: List<SelinuxCheckResult>): SelinuxResolution {
        val filesystemActive = checks.any {
            it.method == METHOD_FILESYSTEM &&
                (it.status == FILESYSTEM_ACTIVE || it.status == FILESYSTEM_MOUNTED)
        }

        checks.forEach { result ->
            if (result.status == SELINUX_PERMISSIVE) {
                return SelinuxResolution(SelinuxMode.PERMISSIVE, SELINUX_PERMISSIVE, paradox = false)
            }
            if (result.status == SELINUX_DISABLED || result.status == FILESYSTEM_NOT_MOUNTED) {
                return SelinuxResolution(SelinuxMode.DISABLED, SELINUX_DISABLED, paradox = false)
            }
        }

        if (checks.any { it.readsEnforcing }) {
            return SelinuxResolution(SelinuxMode.ENFORCING, SELINUX_ENFORCING, paradox = false)
        }

        // Only a denied read of the enforce node proves enforcing mode: every UID may read it, and
        // in permissive mode avc_denied() grants instead of returning -EACCES.
        val enforceReadDenied = checks.any {
            it.permissionDenied && it.method in ENFORCE_NODE_METHODS
        }
        if (enforceReadDenied && filesystemActive) {
            return SelinuxResolution(SelinuxMode.ENFORCING, "Enforcing (paradox)", paradox = true)
        }

        return SelinuxResolution(SelinuxMode.UNKNOWN, STATUS_UNKNOWN, paradox = false)
    }

    private fun readProcAttrRaw(): String? = try {
        val file = File(PROC_ATTR_PATH)
        if (file.exists() && file.canRead()) file.readText().trim().replace("\u0000", "") else null
    } catch (_: Exception) {
        null
    }

    private fun String?.isPermissionDenied(): Boolean =
        this?.contains("Permission denied", ignoreCase = true) == true ||
            this?.contains("EACCES", ignoreCase = true) == true

    private data class SelinuxResolution(
        val mode: SelinuxMode,
        val label: String,
        val paradox: Boolean,
    )

    companion object {
        private const val PROCESS_TIMEOUT_SECONDS = 5L
        private const val SELINUX_ENFORCING = "Enforcing"
        private const val SELINUX_PERMISSIVE = "Permissive"
        private const val SELINUX_DISABLED = "Disabled"

        private const val BLOCKED_ENFORCING = "Blocked (Enforcing)"
        private const val METHOD_FILESYSTEM = "filesystem"
        private const val METHOD_SYSFS = "sysfs"
        private const val METHOD_GETENFORCE = "getenforce"
        private const val METHOD_PROC_ATTR = "proc/self/attr"
        private val ENFORCE_NODE_METHODS = setOf(METHOD_SYSFS, METHOD_GETENFORCE)

        private const val PROC_ATTR_LABELED = "Labeled"
        private const val FILESYSTEM_NOT_MOUNTED = "Not mounted"
        private const val FILESYSTEM_NOT_OBSERVABLE = "Not observable"
        private const val FILESYSTEM_ACTIVE = "Active"
        private const val FILESYSTEM_MOUNTED = "Mounted"

        private const val STATUS_ERROR = "Error"
        private const val STATUS_FAILED = "Failed"
        private const val STATUS_UNKNOWN = "Unknown"
        private const val STATUS_NOT_FOUND = "Not found"
        private const val STATUS_TIMEOUT = "Timeout"
        private const val STATUS_EMPTY = "Empty"
        private const val STATUS_UNKNOWN_CONTEXT = "Unknown context"
        private const val STATUS_UNEXPECTED_CONTEXT = "Unexpected context"
        private const val STATUS_NOT_READABLE = "Not readable"

        private const val SELINUX_STATUS_PATH = "/sys/fs/selinux/enforce"
        private const val SELINUX_MOUNT_PATH = "/sys/fs/selinux"
        private const val SELINUX_POLICY_PATH = "/sys/fs/selinux/policy"
        private const val PROC_ATTR_PATH = "/proc/self/attr/current"
    }
}
