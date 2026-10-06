// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Receives the privileged server binder.
 *
 * The server process (started as `shell`/root) calls [call] with [AxConstants.METHOD_ATTACH_SERVICE]
 * and the binder in [AxConstants.EXTRA_BINDER]. The provider is protected by the
 * `INTERACT_ACROSS_USERS_FULL` permission so only the shell server can reach it.
 */
class AxProvider : ContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == AxConstants.METHOD_ATTACH_SERVICE) {
            val binder = extras?.getBinder(AxConstants.EXTRA_BINDER)
            if (binder != null) {
                AxClient.attach(binder)
            }
            return Bundle().apply { putBoolean("ok", binder != null) }
        }
        return super.call(method, arg, extras)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
