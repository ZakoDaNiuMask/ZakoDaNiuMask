// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron

import android.content.Context
import android.content.SharedPreferences

/** Small preference store for the rootless environment (kept independent of the manager app). */
object AxSettings {
    private const val PREFS = "axeron_settings"

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val preferences: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val prefs: SharedPreferences
        get() = preferences

    fun getTcpMode(): Boolean = prefs.getBoolean("tcp_mode", false)

    fun setTcpMode(enabled: Boolean) {
        prefs.edit().putBoolean("tcp_mode", enabled).apply()
    }

    fun getTcpPort(): Int = prefs.getInt("tcp_port", 5555)

    fun setTcpPort(port: Int) {
        prefs.edit().putInt("tcp_port", port).apply()
    }

    fun isActiveOnBoot(): Boolean = prefs.getBoolean("active_on_boot", false)

    fun setActiveOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean("active_on_boot", enabled).apply()
    }
}
