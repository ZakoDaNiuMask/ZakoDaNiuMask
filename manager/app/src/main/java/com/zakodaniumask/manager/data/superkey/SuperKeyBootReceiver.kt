// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from YukiSU (GPL-3.0), manager superkey boot receiver; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.superkey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts one saved-SuperKey authentication attempt after user unlock. */
class SuperKeyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { SuperKeyRepository(context).launchAutoAuthentication() }
    }
}
