// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron

import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the binder of the privileged server once it has been delivered to the app.
 *
 * The server (running as `shell`) calls [AxProvider] after start-up; the provider hands the binder
 * to this singleton.
 */
object AxClient {
    private val mutableService = MutableStateFlow<IAxeronService?>(null)
    val service: StateFlow<IAxeronService?> = mutableService.asStateFlow()

    fun attach(binder: IBinder) {
        mutableService.value = IAxeronService.Stub.asInterface(binder)
    }

    fun detach() {
        mutableService.value = null
    }

    fun isRunning(): Boolean = mutableService.value != null

    fun getVersion(): Int? = runCatching { mutableService.value?.version }.getOrNull()

    fun getUid(): Int? = runCatching { mutableService.value?.uid }.getOrNull()

    fun exec(command: String): String? =
        runCatching { mutableService.value?.exec(command) }.getOrNull()

    /** Starts a shell command in the background; no output is captured. */
    fun execDetached(command: String) {
        runCatching { mutableService.value?.execDetached(command) }
    }

    fun writeFile(path: String, data: ByteArray): Boolean =
        runCatching { mutableService.value?.writeFile(path, data) ?: false }.getOrDefault(false)

    fun readText(path: String): String? =
        runCatching { mutableService.value?.readFile(path)?.toString(Charsets.UTF_8) }.getOrNull()

    fun fileExists(path: String): Boolean =
        runCatching { mutableService.value?.fileExists(path) ?: false }.getOrDefault(false)

    /**
     * Runs a command, preferring the root shell when available and falling back to the rootless
     * server otherwise.
     */
    fun isAlive(): Boolean = runCatching { mutableService.value?.let { it.uid; true } == true }
        .getOrDefault(false)
}
