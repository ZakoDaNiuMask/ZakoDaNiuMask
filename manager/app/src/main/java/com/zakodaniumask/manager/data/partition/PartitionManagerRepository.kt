package com.zakodaniumask.manager.data.partition

import com.topjohnwu.superuser.CallbackList
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class SlotInfo(
    val isAbDevice: Boolean,
    val currentSlot: String?,
    val otherSlot: String?,
)

data class PartitionInfo(
    val name: String,
    val blockDevice: String,
    val type: String,
    val size: Long,
    val isLogical: Boolean,
    val isDangerous: Boolean = false,
    val excludeFromBatch: Boolean = false,
)

data class Ak3PackageInfo(
    val kernelName: String,
    val devices: List<String>,
    val packageSlotPolicy: String?,
)

/**
 * Portions Copyright (C) Anatdx (YukiSU)
 * Source: https://github.com/Rouyashiki/YukiSU
 * Ported into ZakoDaNiuMask; see LICENSE.
 *
 * Thin wrapper around the `ksud flash` command group.
 */
class PartitionManagerRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val typeRegex = Regex("\\[([^,]+),\\s*(\\d+)\\s*bytes]")

    private fun shellArg(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun runKsudLines(args: String): List<String> {
        val stdout = ArrayList<String>()
        val stderr = ArrayList<String>()
        val result = ksuCliRepository.getRootShell()
            .newJob()
            .add("${ksuCliRepository.getKsuDaemonPath()} $args")
            .to(stdout, stderr)
            .exec()
        if (!result.isSuccess) {
            throw IOException(
                stderr.lastOrNull()
                    ?: stdout.lastOrNull()
                    ?: "ksud exited with code ${result.code}"
            )
        }
        return stdout.filter { it.isNotBlank() }.map { it.trim() }
    }

    private fun runKsudStreaming(
        args: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): Boolean {
        val stdout = object : CallbackList<String>() {
            override fun onAddElement(s: String?) {
                s?.let(onStdout)
            }
        }
        val stderr = object : CallbackList<String>() {
            override fun onAddElement(s: String?) {
                s?.let(onStderr)
            }
        }
        return ksuCliRepository.getRootShell()
            .newJob()
            .add("${ksuCliRepository.getKsuDaemonPath()} $args")
            .to(stdout, stderr)
            .exec()
            .isSuccess
    }

    suspend fun getSlotInfo(): SlotInfo = withContext(Dispatchers.IO) {
        val lines = runKsudLines("flash slots")
        if (lines.isEmpty()) {
            return@withContext SlotInfo(isAbDevice = false, currentSlot = null, otherSlot = null)
        }
        var isAbDevice = true
        var currentSlot: String? = null
        var otherSlot: String? = null
        lines.forEach { line ->
            when {
                "This device is not A/B partitioned" in line -> isAbDevice = false
                "Current slot:" in line -> currentSlot = line.substringAfter("Current slot:").trim()
                "Other slot:" in line -> otherSlot = line.substringAfter("Other slot:").trim()
            }
        }
        SlotInfo(isAbDevice, currentSlot, otherSlot)
    }

    suspend fun getPartitionList(
        slot: String?,
        scanAll: Boolean = false,
    ): List<PartitionInfo> = withContext(Dispatchers.IO) {
        val args = buildString {
            append("flash list")
            if (slot != null) append(" --slot ${shellArg(slot)}")
            if (scanAll) append(" --all")
        }
        parsePartitionList(runKsudLines(args))
    }

    private fun parsePartitionList(lines: List<String>): List<PartitionInfo> {
        return lines.mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("[") || line.contains("partitions", ignoreCase = true)) {
                return@mapNotNull null
            }
            val typeMatch = typeRegex.find(line) ?: return@mapNotNull null
            val name = line.substring(0, typeMatch.range.first).trim()
            if (name.isEmpty()) return@mapNotNull null
            val type = typeMatch.groupValues[1].trim()
            val size = typeMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            PartitionInfo(
                name = name,
                blockDevice = "",
                type = type,
                size = size,
                isLogical = type.equals("logical", ignoreCase = true),
                isDangerous = "[DANGEROUS]" in line,
                excludeFromBatch = name == "userdata" || name == "data",
            )
        }.distinctBy { it.name }
    }

    suspend fun getPartitionBlockDevice(
        partition: String,
        slot: String?,
    ): String = withContext(Dispatchers.IO) {
        val args = buildString {
            append("flash info ${shellArg(partition)}")
            if (slot != null) append(" --slot ${shellArg(slot)}")
        }
        runKsudLines(args)
            .firstOrNull { "Block device:" in it }
            ?.substringAfter("Block device:")
            ?.trim()
            .orEmpty()
    }

    suspend fun inspectAk3Package(
        zipPath: String,
    ): Ak3PackageInfo = withContext(Dispatchers.IO) {
        val values = runKsudLines("flash ak3-info ${shellArg(zipPath)}")
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator) to line.substring(separator + 1)
                }
            }
            .toMap()
        if (values["valid"] != "1") {
            throw IOException("ksud did not recognize this AnyKernel3 package")
        }
        Ak3PackageInfo(
            kernelName = values["kernel"].orEmpty(),
            devices = values["devices"].orEmpty().split('|').filter(String::isNotBlank),
            packageSlotPolicy = values["slot_policy"]?.takeIf(String::isNotBlank),
        )
    }

    suspend fun backupPartition(
        partition: String,
        outputPath: String,
        slot: String?,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val slotArg = if (slot != null) " --slot ${shellArg(slot)}" else ""
        runKsudStreaming(
            "flash backup ${shellArg(partition)} ${shellArg(outputPath)}$slotArg",
            onStdout,
            onStderr,
        )
    }

    suspend fun flashPartition(
        imagePath: String,
        partition: String,
        slot: String?,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val slotArg = if (slot != null) " --slot ${shellArg(slot)}" else ""
        runKsudStreaming(
            "flash image ${shellArg(imagePath)} ${shellArg(partition)}$slotArg",
            onStdout,
            onStderr,
        )
    }

    suspend fun mapLogicalPartitions(
        slot: String,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        runKsudStreaming("flash map ${shellArg(slot)}", onStdout, onStderr)
    }

    suspend fun getAvbStatus(): String = withContext(Dispatchers.IO) {
        runKsudLines("flash avb")
            .firstOrNull { "AVB/dm-verity status:" in it }
            ?.substringAfter("AVB/dm-verity status:")
            ?.trim()
            .orEmpty()
    }

    suspend fun disableAvb(
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        runKsudStreaming("flash avb disable", onStdout, onStderr)
    }

    suspend fun getKernelVersion(slot: String?): String = withContext(Dispatchers.IO) {
        val slotArg = if (slot != null) " --slot ${shellArg(slot)}" else ""
        runKsudLines("flash kernel$slotArg")
            .firstOrNull { "Kernel version:" in it }
            ?.substringAfter("Kernel version:")
            ?.trim()
            .orEmpty()
    }

    suspend fun getBootSlotInfo(): String = withContext(Dispatchers.IO) {
        runKsudLines("flash boot-info").joinToString("\n")
    }
}
