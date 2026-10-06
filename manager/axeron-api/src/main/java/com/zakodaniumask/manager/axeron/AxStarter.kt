// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron

import android.content.Context
import java.io.File

/**
 * Builds the commands used to start the privileged server.
 *
 * The starter is a native executable shipped as `libaxeron.so` inside the app's native library
 * directory. Running it launches [AxConstants.SERVER_CLASS_PATH] in the `shell`/root context.
 */
object AxStarter {

    fun starterFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, AxConstants.STARTER_LIB_NAME)

    fun userCommand(context: Context): String = starterFile(context).absolutePath

    fun adbCommand(context: Context): String = "adb shell ${userCommand(context)}"

    fun internalCommand(context: Context): String =
        "${userCommand(context)} --apk=${context.applicationInfo.sourceDir}"

    fun internalAdbCommand(context: Context, keyPair: String): String =
        "${internalCommand(context)}; settings put global ${AxConstants.KEY_PAIR} $keyPair"
}
