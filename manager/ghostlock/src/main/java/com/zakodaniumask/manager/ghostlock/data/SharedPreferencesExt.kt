/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Local replacement for `androidx.core.content.edit` so the vendored GhostLock code keeps its
 * `preferences.edit { ... }` call sites without pulling in androidx.core-ktx.
 */

package com.zakodaniumask.manager.ghostlock.data

import android.content.SharedPreferences

internal inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) editor.commit() else editor.apply()
}
