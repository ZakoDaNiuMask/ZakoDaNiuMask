package com.zakodaniumask.manager.ksuui.ui.util

import com.topjohnwu.superuser.Shell
import com.zakodaniumask.manager.ksuui.ui.util.module.LatestVersionInfo

/** Phase 0: update checks are disabled until the KernelSU downloader is ported. */
fun checkNewVersion(): LatestVersionInfo = LatestVersionInfo()

fun rootAvailable(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)

fun reboot(reason: String = "") {
    val command = if (reason.isBlank()) {
        "/system/bin/svc power reboot || /system/bin/reboot"
    } else {
        "/system/bin/svc power reboot $reason || /system/bin/reboot $reason"
    }
    runCatching {
        Shell.Builder.create().build().newJob().add(command).exec()
    }
}

private val rootShell: Shell by lazy { Shell.Builder.create().build() }

fun getRootShell(globalMnt: Boolean = false): Shell = rootShell

fun android.net.Uri.getFileName(context: android.content.Context): String? {
    val resolver = context.contentResolver
    resolver.query(this, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return null
}
