/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Ported from Duck-Detector-Refactoring (https://github.com/eltavine/Duck-Detector-Refactoring).
 */

package com.zakodaniumask.manager.data.kernel

/** Identity and boot command line rules that recognise community kernels and unlocked boot state. */
internal object KernelIdentityRules {
    val TELEGRAM_REGEX =
        Regex("""\bTG\b|\btg\b|\bTelegram\b|\btelegram\b|t\.me/""", RegexOption.IGNORE_CASE)

    val MENTION_REGEX = Regex("@[A-Za-z0-9_]+")

    val EMOJI_REGEX = Regex("""[\u203C-\u3299\uD83C\uD000-\uD83C\uDFFF\uD83D\uDC00-\uD83D\uDEFF]""")

    val CHINESE_REGEX = Regex("""[\u4E00-\u9FFF]""")

    val NON_LATIN_REGEX = Regex("""[\u0400-\u04FF\u0600-\u06FF\u0900-\u097F\u0E00-\u0E7F]""")

    private val CUSTOM_KERNEL_KEYWORDS = listOf(
        "xiaoxiaow",
        "qdykernel",
        "numbers",
        "cctv",
        "shirkneko",
        "mirinfork",
        "brokestar",
        "sukisu",
        "Glow-v",
        "aptkernel",
        "coolzyd9107",
        "aptusitu",
        "Winkmoon",
        "ShirokoNeko",
    )

    private val CASE_SENSITIVE_KEYWORDS = listOf("OKI")

    val KEYWORD_SCAN_COUNT =
        CUSTOM_KERNEL_KEYWORDS.size + CASE_SENSITIVE_KEYWORDS.size + 4

    private val KNOWN_KERNEL_MAJOR_VERSIONS = setOf("4", "5", "6")

    private val KERNEL_RELEASE_MAJOR_REGEX = Regex("""^(\d{1,2})\.\d{1,3}\.\d+""")

    private val PROC_VERSION_RELEASE_REGEX = Regex("""Linux version (\S+)""")

    val CMDLINE_CHECKS = listOf(
        CmdlineCheck("androidboot.verifiedbootstate=orange", "Bootloader unlocked (orange)", true),
        CmdlineCheck("androidboot.verifiedbootstate=yellow", "Self-signed boot (yellow)", true),
        CmdlineCheck("androidboot.enable_dm_verity=0", "dm-verity disabled", true),
        CmdlineCheck("androidboot.secboot=disabled", "Secure boot disabled", true),
        CmdlineCheck("androidboot.vbmeta.device_state=unlocked", "vbmeta unlocked", true),
        CmdlineCheck("skip_initramfs", "Skip initramfs (possible root)", false),
        CmdlineCheck("init=/sbin", "Custom init path", true),
        CmdlineCheck("init=/system", "Custom init path", false),
        CmdlineCheck("androidboot.force_normal_boot=1", "Force normal boot", false),
        CmdlineCheck("magisk", "Magisk reference in cmdline", true),
        CmdlineCheck("ksu", "KernelSU reference in cmdline", true),
        CmdlineCheck("apatch", "APatch reference in cmdline", true),
        CmdlineCheck("rootfs=", "Custom rootfs", false),
        CmdlineCheck("androidboot.slot_suffix=", "Slot suffix present", false),
    )

    fun detectCustomKernelKeywords(input: String): List<String> {
        if (input.isBlank()) {
            return emptyList()
        }
        return buildList {
            CUSTOM_KERNEL_KEYWORDS.forEach { keyword ->
                if (input.contains(keyword, ignoreCase = true)) {
                    add(keyword)
                }
            }
            CASE_SENSITIVE_KEYWORDS.forEach { keyword ->
                if (input.contains(keyword)) {
                    add(keyword)
                }
            }
        }
    }

    fun detectNonReleaseKernelMajorVersion(unameOutput: String): String? {
        val major = extractKernelReleaseMajor(unameOutput) ?: return null
        return major.takeIf { it !in KNOWN_KERNEL_MAJOR_VERSIONS }
    }

    private fun extractKernelReleaseMajor(source: String): String? {
        // Genuine `uname -a` output on Android is "Linux localhost <release> ...", because init sets
        // the UTS nodename with `hostname localhost`. Require that prefix so extraction is anchored
        // to a real uname invocation instead of guessing the field from its position.
        val tokens = source.trim().split(Regex("""\s+"""))
        if (tokens.getOrNull(0) != "Linux" || tokens.getOrNull(1) != "localhost") {
            return null
        }
        val releaseToken = tokens.getOrNull(2) ?: return null
        return KERNEL_RELEASE_MAJOR_REGEX.find(releaseToken)?.groupValues?.get(1)
    }

    fun detectCriticalCmdlineFallback(procCmdline: String): List<String> {
        if (procCmdline.isBlank()) {
            return emptyList()
        }
        return CMDLINE_CHECKS.filter {
            it.isCritical && procCmdline.contains(it.pattern, ignoreCase = true)
        }.map { it.description }
    }

    /** Release token of the running kernel, from `uname` output or the JVM os.version snapshot. */
    fun extractReleaseToken(source: String): String? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        PROC_VERSION_RELEASE_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        val tokens = trimmed.split(Regex("""\s+"""))
        if (tokens.size >= 3 && tokens[0] == "Linux" && tokens[1] == "localhost") {
            return tokens[2]
        }
        return tokens.firstOrNull()?.substringBefore(' ')
    }

    /** Major version from a raw release token such as `5.10.101-android12`. */
    fun releaseMajor(release: String): String? =
        KERNEL_RELEASE_MAJOR_REGEX.find(release)?.groupValues?.get(1)

    fun isKnownMajor(release: String): Boolean =
        releaseMajor(release)?.let { it in KNOWN_KERNEL_MAJOR_VERSIONS } ?: true
}

internal data class CmdlineCheck(
    val pattern: String,
    val description: String,
    val isCritical: Boolean,
)
