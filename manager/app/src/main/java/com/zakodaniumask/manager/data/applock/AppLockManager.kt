// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.applock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped lock state for the manager UI. Locks on cold start (when
 * enabled) and after the configured timeout once the whole process has left the
 * foreground. It never touches root authorization or module WebUIs.
 */
class AppLockManager(
    private val repository: AppLockRepository,
) {
    private val mutableLocked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = mutableLocked.asStateFlow()

    private var initialized = false
    private var backgroundedAt = 0L

    fun lock() {
        mutableLocked.value = true
    }

    fun unlock() {
        backgroundedAt = 0L
        mutableLocked.value = false
    }

    fun onForeground() {
        val settings = repository.state.value
        if (!settings.enabled) {
            initialized = true
            mutableLocked.value = false
            return
        }
        if (!initialized) {
            // Cold start: always require unlocking when the lock is enabled.
            initialized = true
            mutableLocked.value = true
            return
        }
        if (settings.timeout == AppLockTimeout.NEVER) return
        val elapsed = if (backgroundedAt == 0L) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() - backgroundedAt
        }
        if (elapsed >= settings.timeout.seconds * 1000L) {
            mutableLocked.value = true
        }
    }

    fun onBackground() {
        backgroundedAt = System.currentTimeMillis()
    }
}

/** Bridges the process lifecycle to [AppLockManager]. */
class AppLockLifecycleObserver(
    private val manager: AppLockManager,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = manager.onForeground()
    override fun onStop(owner: LifecycleOwner) = manager.onBackground()
}
