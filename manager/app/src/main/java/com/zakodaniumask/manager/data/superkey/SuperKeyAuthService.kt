// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from YukiSU (GPL-3.0), manager superkey auth service; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.superkey

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.zakodaniumask.manager.Natives
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A non-restarting background action that authenticates the saved SuperKey once. */
class SuperKeyAuthService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val started = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_AUTHENTICATE) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (started.compareAndSet(false, true)) {
            executor.execute {
                try {
                    val repository = SuperKeyRepository(this)
                    val key = repository.savedKey()
                    val success = if (key != null && repository.isAutoAuthenticationEnabled()) {
                        Natives.authenticateSuperKey(key)
                    } else {
                        false
                    }
                    Log.i(TAG, "automatic SuperKey authentication success=$success")
                } catch (throwable: Throwable) {
                    Log.e(TAG, "automatic SuperKey authentication failed", throwable)
                } finally {
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ZakoSuperKey"
        const val ACTION_AUTHENTICATE = "com.zakodaniumask.manager.superkey.AUTHENTICATE"
    }
}
