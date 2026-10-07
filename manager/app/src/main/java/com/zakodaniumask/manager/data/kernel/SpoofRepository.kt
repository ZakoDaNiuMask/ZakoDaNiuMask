package com.zakodaniumask.manager.data.kernel

import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CpuIdentity(
    val midrHex: String,
    val bogomips: Int,
    val coreCount: Int,
    val hwcap: String,
    val hwcap2: String,
)

data class UnameIdentity(
    val release: String,
    val version: String,
)

class SpoofRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"

    suspend fun readCurrentCpuIdentity(): CpuIdentity? = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val cpuInfo = ShellUtils.fastCmd(shell, "cat /proc/cpuinfo")
            var implementer = ""
            var variant = ""
            var part = ""
            var revision = ""
            var bogomips = 0

            for (line in cpuInfo.lineSequence()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("CPU implementer") ->
                        implementer = trimmed.substringAfter(":").trim()

                    trimmed.startsWith("CPU variant") ->
                        variant = trimmed.substringAfter(":").trim()

                    trimmed.startsWith("CPU part") ->
                        part = trimmed.substringAfter(":").trim()

                    trimmed.startsWith("CPU revision") ->
                        revision = trimmed.substringAfter(":").trim()

                    trimmed.startsWith("BogoMIPS") ->
                        bogomips = trimmed.substringAfter(":").trim().toFloatOrNull()?.toInt() ?: 0
                }
                if (implementer.isNotEmpty() && part.isNotEmpty() &&
                    variant.isNotEmpty() && revision.isNotEmpty()
                ) {
                    break
                }
            }

            if (implementer.isEmpty() || part.isEmpty()) {
                return@runCatching null
            }

            val midr = buildString {
                append(implementer.removePrefix("0x"))
                append(variant.removePrefix("0x").padStart(1, '0'))
                append("0")
                append(part.removePrefix("0x").padStart(3, '0'))
                append(revision.removePrefix("0x").padStart(1, '0'))
            }

            val hwcap = ShellUtils.fastCmd(shell, "getprop ro.hwcap")
                .trim()
                .ifEmpty { "0x0" }
            val hwcap2 = ShellUtils.fastCmd(shell, "getprop ro.hwcap2")
                .trim()
                .ifEmpty { "0x0" }

            CpuIdentity(
                midrHex = "0x$midr",
                bogomips = bogomips,
                coreCount = Runtime.getRuntime().availableProcessors(),
                hwcap = hwcap,
                hwcap2 = hwcap2,
            )
        }.getOrNull()
    }

    suspend fun readCurrentUname(): UnameIdentity = withContext(Dispatchers.IO) {
        val uname = runCatching { android.system.Os.uname() }.getOrNull()
        UnameIdentity(
            release = uname?.release ?: "",
            version = uname?.version ?: "",
        )
    }

    suspend fun spoofCpu(
        cpu: Int,
        midrHex: String,
        bogomips: Int,
        hwcapHex: String,
        hwcap2Hex: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val cmd = buildString {
            append("${ksuCliRepository.getKsuDaemonPath()} kernel spoof-cpu")
            append(" --cpu $cpu")
            append(" --midr ${shellQuote(midrHex)}")
            if (bogomips > 0) append(" --bogomips $bogomips")
            if (hwcapHex.isNotBlank()) append(" --hwcap ${shellQuote(hwcapHex)}")
            if (hwcap2Hex.isNotBlank()) append(" --hwcap2 ${shellQuote(hwcap2Hex)}")
        }
        ShellUtils.fastCmdResult(ksuCliRepository.getRootShell(), cmd).isSuccess
    }

    suspend fun spoofKernelUname(release: String, version: String): Boolean =
        withContext(Dispatchers.IO) {
            val cmd = buildString {
                append("${ksuCliRepository.getKsuDaemonPath()} kernel spoof-uname")
                if (release.isNotBlank()) append(" --release ${shellQuote(release)}")
                if (version.isNotBlank()) append(" --version ${shellQuote(version)}")
            }
            ShellUtils.fastCmdResult(ksuCliRepository.getRootShell(), cmd).isSuccess
        }

    suspend fun isSelinuxPermissive(): Boolean = withContext(Dispatchers.IO) {
        val output = ShellUtils.fastCmd(ksuCliRepository.getRootShell(), "getenforce")
            .trim()
            .lowercase()
        output == "permissive"
    }

    suspend fun setSelinuxPermissive(permissive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val target = if (permissive) "0" else "1"
        Shell.cmd("setenforce $target").exec().isSuccess
    }
}
