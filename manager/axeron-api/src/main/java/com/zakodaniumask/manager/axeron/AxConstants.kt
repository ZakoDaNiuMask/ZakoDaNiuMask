// SPDX-License-Identifier: GPL-3.0-or-later
//
// Minimal Shizuku-style rootless environment. The privileged server is started through ADB
// (wireless debugging / computer) or root and runs in the `shell` (uid 2000) context.

package com.zakodaniumask.manager.axeron

object AxConstants {
    const val VERSION = 1

    const val SERVER_CLASS_PATH = "com.zakodaniumask.manager.axeron.server.Server"
    const val SERVER_PROCESS_NAME = "axeron_server"
    const val STARTER_LIB_NAME = "libaxeron.so"

    const val SERVER_AUTHORITY_SUFFIX = ".server"
    const val PERMISSION_SUFFIX = ".permission.AXERON"

    const val METHOD_ATTACH_SERVICE = "attachService"
    const val EXTRA_BINDER = "binder"

    /** Global secure setting used to hand the ADB key pair to the started server. */
    const val KEY_PAIR = "zakodaniumask_adb_key_pair"
}
