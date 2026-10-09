// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from YukiSU (GPL-3.0), manager superkey helper; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.superkey

import android.content.Context
import android.content.Intent
import com.zakodaniumask.manager.Natives
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the user's SuperKey and drives password-based manager authentication.
 * The key never leaves the process except through the kernel ioctl.
 */
class SuperKeyRepository(
    private val context: Context,
) {
    private val preferences = context.applicationContext
        .getSharedPreferences("superkey", Context.MODE_PRIVATE)

    fun hasSavedKey(): Boolean = savedKey() != null

    fun savedKey(): String? =
        preferences.getString(KEY_SAVED, null)?.takeIf { it.isNotBlank() }

    fun saveKey(key: String) {
        if (shouldSkipStorage() || key.isBlank()) return
        preferences.edit().putString(KEY_SAVED, key).apply()
    }

    fun clearKey() {
        preferences.edit()
            .remove(KEY_SAVED)
            .putBoolean(KEY_AUTO, false)
            .putBoolean(KEY_PATCH, false)
            .apply()
    }

    fun shouldSkipStorage(): Boolean = preferences.getBoolean(KEY_SKIP, false)

    fun setSkipStorage(skip: Boolean) {
        preferences.edit().putBoolean(KEY_SKIP, skip).apply()
        if (skip) {
            preferences.edit()
                .remove(KEY_SAVED)
                .putBoolean(KEY_AUTO, false)
                .putBoolean(KEY_PATCH, false)
                .apply()
        }
    }

    fun isAutoAuthenticationEnabled(): Boolean = preferences.getBoolean(KEY_AUTO, false)

    fun setAutoAuthenticationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /** Whether the current SuperKey should be injected when patching a boot image. */
    fun isPatchEnabled(): Boolean = preferences.getBoolean(KEY_PATCH, false)

    fun setPatchEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PATCH, enabled).apply()
    }

    fun isSignatureBypass(): Boolean = preferences.getBoolean(KEY_BYPASS, false)

    fun setSignatureBypass(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BYPASS, enabled).apply()
    }

    /** The SuperKey to inject when patching, or null when patch injection is off. */
    fun patchKeyOrNull(): String? = savedKey()?.takeIf { isPatchEnabled() }

    fun shouldAutoAuthenticate(): Boolean = isAutoAuthenticationEnabled() && hasSavedKey()

    fun isConfigured(): Boolean = runCatching { Natives.isSuperKeyConfigured() }.getOrDefault(false)

    fun isAuthenticated(): Boolean =
        runCatching { Natives.isSuperKeyAuthenticated() }.getOrDefault(false)

    suspend fun authenticate(key: String): Boolean =
        withContext(Dispatchers.IO) { runCatching { Natives.authenticateSuperKey(key) }.getOrDefault(false) }

    fun launchAutoAuthentication(): Boolean {
        if (!shouldAutoAuthenticate()) return false
        return runCatching {
            context.applicationContext.startService(
                Intent(context.applicationContext, SuperKeyAuthService::class.java)
                    .setAction(SuperKeyAuthService.ACTION_AUTHENTICATE),
            )
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val KEY_SAVED = "saved_superkey"
        const val KEY_SKIP = "skip_store_superkey"
        const val KEY_AUTO = "auto_authenticate_superkey"
        const val KEY_PATCH = "patch_superkey"
        const val KEY_BYPASS = "signature_bypass"
    }
}
