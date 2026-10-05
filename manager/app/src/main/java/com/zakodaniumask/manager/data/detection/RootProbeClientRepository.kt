// SPDX-License-Identifier: GPL-3.0-or-later
// Binds the isolated RootProbeService and returns its JSON report as a SuReport.

package com.zakodaniumask.manager.data.detection

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.zakodaniumask.detection.IRootProbe
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RootProbeClientRepository(
    private val application: Application,
) {
    private val mutex = Mutex()
    private var activeConnection: ServiceConnection? = null

    suspend fun scan(): SuReport = mutex.withLock {
        val intent = Intent(application, RootProbeService::class.java)
        try {
            val binder = withTimeoutOrNull(10_000.milliseconds) { connect(intent) }
            if (binder == null) {
                SuReport.failed("Root probe service unavailable")
            } else {
                withContext(Dispatchers.IO) {
                    parseSuReport(IRootProbe.Stub.asInterface(binder).scan())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SuReport.failed(error.message ?: "Root probe failed")
        } finally {
            disconnect()
        }
    }

    private suspend fun connect(intent: Intent): IBinder? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (continuation.isActive) {
                            continuation.resume(binder)
                        } else {
                            disconnectOnMain()
                        }
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                activeConnection = connection
                continuation.invokeOnCancellation { disconnectOnMain() }
                val bound = runCatching {
                    ContextCompat.bindService(
                        application,
                        intent,
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                }.getOrDefault(false)
                if (!bound && continuation.isActive) {
                    activeConnection = null
                    continuation.resume(null)
                }
            }
        }

    private suspend fun disconnect() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        activeConnection?.let { connection ->
            runCatching { application.unbindService(connection) }
        }
        activeConnection = null
    }

    private fun disconnectOnMain() {
        ContextCompat.getMainExecutor(application).execute {
            activeConnection?.let { connection ->
                runCatching { application.unbindService(connection) }
            }
            activeConnection = null
        }
    }
}
