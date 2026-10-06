// SPDX-License-Identifier: GPL-3.0-or-later
//
// Privileged server for the rootless environment.
//
// The native starter (`libaxeron.so`) launches this class with `app_process` in the `shell`
// (uid 2000) or root context. It publishes an [IAxeronService] binder back to the manager app by
// calling the app's [com.zakodaniumask.manager.axeron.AxProvider].

package com.zakodaniumask.manager.axeron.server

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import com.zakodaniumask.manager.axeron.AxConstants
import com.zakodaniumask.manager.axeron.IAxeronService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.concurrent.TimeUnit

object Server {
    @JvmStatic
    fun main(args: Array<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }

        val apkPath = args.firstOrNull { it.startsWith("--apk=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
        val context = systemContext()
        val packageName = resolvePackageName(context, apkPath) ?: DEFAULT_PACKAGE

        val binder = AxeronService().asBinder()
        deliverBinder(context, packageName, binder)

        Looper.prepareMainLooper()
        Looper.loop()
    }

    private fun systemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("systemMain").invoke(null)
        activityThread.getMethod("getSystemContext").invoke(thread) as? Context
    }.getOrNull()

    private fun resolvePackageName(context: Context?, apkPath: String?): String? {
        if (context == null || apkPath.isNullOrBlank()) return null
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, 0)
        }
        return info?.packageName
    }

    private fun deliverBinder(context: Context?, packageName: String, binder: android.os.IBinder) {
        if (context == null) return
        val uri = Uri.parse("content://$packageName${AxConstants.SERVER_AUTHORITY_SUFFIX}")
        val extras = Bundle().apply { putBinder(AxConstants.EXTRA_BINDER, binder) }
        runCatching {
            context.contentResolver.call(uri, AxConstants.METHOD_ATTACH_SERVICE, null, extras)
        }
    }

    private const val DEFAULT_PACKAGE = "com.zakodaniumask.manager"
}

private class AxeronService : IAxeronService.Stub() {
    override fun getVersion(): Int = AxConstants.VERSION

    override fun getUid(): Int = Process.myUid()

    override fun exec(command: String): String = runShell(command)

    /** Starts the command in the background and returns immediately. */
    override fun execDetached(command: String) {
        runCatching {
            ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        }
    }

    override fun writeFile(path: String, data: ByteArray): Boolean = runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        true
    }.getOrDefault(false)

    override fun readFile(path: String): ByteArray? = runCatching {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }.getOrNull()

    override fun fileExists(path: String): Boolean =
        runCatching { File(path).exists() }.getOrDefault(false)

    override fun exit() {
        Process.killProcess(Process.myPid())
    }

    private fun runShell(command: String): String = runCatching {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        output
    }.getOrElse { it.message.orEmpty() }

    private companion object {
        const val EXEC_TIMEOUT_SECONDS = 30L
    }
}
