// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.domain.model

import org.json.JSONObject

/**
 * User-selected Folk Mount provider, mirroring the tokens accepted by
 * `ksud mount set-mode`.
 */
enum class FolkMountMode(val token: String) {
    AUTO("auto"),
    BUILTIN("builtin"),
    METAMODULE("metamodule");

    companion object {
        fun fromToken(token: String?): FolkMountMode? =
            entries.firstOrNull { it.token == token }
    }
}

/** Provider that actually performs module mounting, mirroring ksud's tokens. */
enum class FolkMountProvider(val token: String) {
    BUILTIN("builtin"),
    METAMODULE("metamodule");

    companion object {
        fun fromToken(token: String?): FolkMountProvider? =
            entries.firstOrNull { it.token == token }
    }
}

/** Decoded `ksud mount status --json` payload (schema_version = 1). */
data class FolkMountStatus(
    val configuredMode: FolkMountMode,
    val nextProvider: FolkMountProvider?,
    val metamoduleId: String?,
    val metamoduleEnabled: Boolean,
    val bootId: String?,
    val bootProvider: FolkMountProvider?,
    val bootResult: String,
    val targetCount: Int?,
    val error: String?,
) {
    companion object {
        const val SCHEMA_VERSION = 1

        fun fromJson(json: JSONObject): FolkMountStatus {
            val configuredMode = FolkMountMode.fromToken(json.optStringOrNull("configured_mode"))
                ?: error("folk mount status has no valid configured_mode")
            return FolkMountStatus(
                configuredMode = configuredMode,
                nextProvider = FolkMountProvider.fromToken(json.optStringOrNull("next_provider")),
                metamoduleId = json.optStringOrNull("metamodule_id"),
                metamoduleEnabled = json.optBoolean("metamodule_enabled", false),
                bootId = json.optStringOrNull("boot_id"),
                bootProvider = FolkMountProvider.fromToken(json.optStringOrNull("boot_provider")),
                bootResult = json.optString("boot_result", "unknown"),
                targetCount = json.optIntOrNull("target_count"),
                error = json.optStringOrNull("error"),
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key, 0)
