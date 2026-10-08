// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.applock

import com.zakodaniumask.manager.data.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long the manager may stay unlocked after leaving the foreground. */
enum class AppLockTimeout(val seconds: Int) {
    IMMEDIATE(0),
    SECONDS_30(30),
    MINUTE_1(60),
    MINUTES_5(300),
    NEVER(-1);

    companion object {
        fun fromSeconds(value: Int): AppLockTimeout =
            entries.firstOrNull { it.seconds == value } ?: IMMEDIATE
    }
}

data class AppLockSettings(
    val enabled: Boolean = false,
    val timeout: AppLockTimeout = AppLockTimeout.IMMEDIATE,
)

/** Persists the app-lock preference. */
class AppLockRepository(
    private val settings: AppSettingsRepository,
) {
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<AppLockSettings> = mutableState.asStateFlow()

    fun reload() {
        mutableState.value = read()
    }

    fun setEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_ENABLED, enabled)
        reload()
    }

    fun setTimeout(timeout: AppLockTimeout) {
        settings.putInt(KEY_TIMEOUT, timeout.seconds)
        reload()
    }

    private fun read() = AppLockSettings(
        enabled = settings.getBoolean(KEY_ENABLED, false),
        timeout = AppLockTimeout.fromSeconds(settings.getInt(KEY_TIMEOUT, 0)),
    )

    private companion object {
        const val KEY_ENABLED = "app_lock_enabled"
        const val KEY_TIMEOUT = "app_lock_timeout"
    }
}
