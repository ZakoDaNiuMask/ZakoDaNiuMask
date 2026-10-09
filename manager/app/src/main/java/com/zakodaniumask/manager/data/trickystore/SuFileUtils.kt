// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream

/** Small root-file helper mirroring the `duck_core::fs` + `Sysroot` helpers. */
internal object SuFileUtils {
    fun file(shell: Shell, path: String): SuFile = SuFile(path).apply { this.shell = shell }

    fun run(shell: Shell, command: String): Boolean =
        runCatching { shell.newJob().add(command).exec().isSuccess }.getOrDefault(false)

    fun exists(shell: Shell, path: String): Boolean =
        runCatching { file(shell, path).exists() }.getOrDefault(false)

    fun isFile(shell: Shell, path: String): Boolean =
        runCatching { file(shell, path).let { it.exists() && it.isFile } }.getOrDefault(false)

    fun readText(shell: Shell, path: String): String? = runCatching {
        val target = file(shell, path)
        if (!target.exists() || !target.isFile) return@runCatching null
        SuFileInputStream.open(target).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    fun writeText(
        shell: Shell,
        path: String,
        text: String,
        makeParents: Boolean = true,
    ): Boolean = runCatching {
        val target = file(shell, path)
        if (makeParents) target.parentFile?.mkdirs()
        SuFileOutputStream.open(target).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)

    fun size(shell: Shell, path: String): Long =
        runCatching { file(shell, path).length() }.getOrDefault(0L)

    fun lastModified(shell: Shell, path: String): Long =
        runCatching { file(shell, path).lastModified() }.getOrDefault(0L)

    fun delete(shell: Shell, path: String): Boolean =
        runCatching { file(shell, path).delete() }.getOrDefault(false)

    fun copy(shell: Shell, from: String, to: String): Boolean =
        run(shell, "cp -f ${shq(from)} ${shq(to)}")

    fun chmod644(shell: Shell, path: String): Boolean = run(shell, "chmod 644 ${shq(path)}")

    fun pickFile(shell: Shell, path: String): Boolean = run(shell, "touch ${shq(path)}")

    fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
