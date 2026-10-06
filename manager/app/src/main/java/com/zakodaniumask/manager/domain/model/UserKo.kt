// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.domain.model

/** One imported kernel module (`.ko`) managed under /data/adb/user_ko. */
data class UserKoModule(
    /** UUID identifying the module; the file is stored as `<id>.ko`. */
    val id: String,
    /** User-facing display name. */
    val name: String,
    /** Module name parsed from the `.ko` modinfo (`name=`), used by rmmod. */
    val moduleName: String,
    /** Whether ksud loads this module automatically at boot. */
    val autoLoad: Boolean,
    /** True when the module name is currently present in /proc/modules. */
    val loaded: Boolean = false,
) {
    val fileName: String get() = "$id.ko"
}

data class UserKoState(
    val stage: String = UserKoStages.POST_FS_DATA,
    val modules: List<UserKoModule> = emptyList(),
    val rootAvailable: Boolean = false,
)

object UserKoStages {
    const val POST_FS_DATA = "post-fs-data"
    const val SERVICE = "service"
    val All = listOf(POST_FS_DATA, SERVICE)
}

/** Result of a root operation; [output] carries the command log for the UI. */
data class UserKoOperationResult(
    val success: Boolean,
    val output: String,
)
