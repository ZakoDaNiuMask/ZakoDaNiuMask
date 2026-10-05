// SPDX-License-Identifier: GPL-3.0-or-later
// Runs the ported Duck-Detector SU/root probes inside an isolated, unprivileged
// process so results reflect what an ordinary third-party app can observe.

package com.zakodaniumask.manager.data.detection

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.zakodaniumask.detection.IRootProbe

class RootProbeService : Service() {

    override fun onBind(intent: Intent): IBinder {
        return object : IRootProbe.Stub() {
            override fun scan(): String = SuRepository().scanInternal().toJsonString()
        }
    }
}
