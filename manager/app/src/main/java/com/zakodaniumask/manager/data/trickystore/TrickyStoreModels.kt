// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

/** Keystore-spoofing module behind a config. */
enum class Backend(val id: String, val identity: String) {
    TRICKY_STORE("tricky_store", "TS"),
    TRICKY_STORE_LEGACY("tricky_store", "TS-L"),
    TEE_SIMULATOR("teesim", "TEES"),
    OH_MY_KEYMINT("oh_my_keymint", "OMK"),
    ;

    companion object {
        fun fromId(id: String): Backend? = entries.firstOrNull { it.id == id }
    }
}

/** Per-app attestation mode. Only Tricky Store honours generate/hack. */
enum class TargetMode(val marker: String) {
    AUTO(""),
    GENERATE("!"),
    HACK("?"),
    ;

    companion object {
        fun fromId(id: String): TargetMode = entries.firstOrNull { it.name.equals(id, true) } ?: AUTO

        /** Splits a `target.txt` line into package name and mode. */
        fun split(line: String): Pair<String, TargetMode> {
            val trimmed = line.trim()
            return when {
                trimmed.endsWith("!") -> trimmed.dropLast(1).trim() to GENERATE
                trimmed.endsWith("?") -> trimmed.dropLast(1).trim() to HACK
                else -> trimmed to AUTO
            }
        }
    }
}

data class TargetEntry(
    val packageName: String,
    val mode: TargetMode = TargetMode.AUTO,
)

enum class FieldKind { TEXT, BOOLEAN }

data class PolicyField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val placeholder: String? = null,
    val options: List<String> = emptyList(),
    val maxLength: Int? = null,
    val multiline: Boolean = false,
    val hint: String? = null,
)

data class PolicySchema(
    val supportsAppMode: Boolean,
    val supportsPerAppPolicy: Boolean,
    val defaultPolicy: List<PolicyField>,
)

data class ConfigData(
    val targets: List<TargetEntry> = emptyList(),
    val defaultPolicy: Map<String, String> = emptyMap(),
    val perAppPolicy: Map<String, Map<String, String>> = emptyMap(),
)

data class BackendDetection(
    val backend: Backend,
    val moduleId: String,
    val moduleDir: String,
    val name: String?,
    val version: String?,
    val versionCode: Long?,
    val active: Boolean,
)

data class KeyboxStatus(
    val path: String,
    val exists: Boolean,
    val size: Long,
    val modified: Long,
)

data class PropStatus(
    val propHandlerEnabled: Boolean,
    val bootHash: String?,
)

data class KeystoreStatus(
    val backends: List<BackendDetection>,
    val active: BackendDetection?,
    val schema: PolicySchema?,
    val config: ConfigData,
    val configError: String?,
    val keybox: KeyboxStatus?,
    val props: PropStatus,
)

/** One keybox provider: a name, an http(s) URL, and a declarative decode pipeline. */
data class KeyboxProvider(
    val name: String,
    val url: String,
    val decode: String,
)

object TrickystorePaths {
    const val MODULES_DIR = "/data/adb/modules"
    const val TRICKY_STORE_DIR = "/data/adb/tricky_store"
    const val TRICKY_STORE_CONFIG = "$TRICKY_STORE_DIR/config.ini"
    const val TRICKY_STORE_TARGETS = "$TRICKY_STORE_DIR/target.txt"
    const val TRICKY_STORE_PATCH = "$TRICKY_STORE_DIR/security_patch.txt"
    const val TRICKY_STORE_KEYBOX = "$TRICKY_STORE_DIR/keybox.xml"
    const val TEE_SIMULATOR_DIR = "/data/adb/teesim"
    const val TEE_SIMULATOR_CONFIG = "$TEE_SIMULATOR_DIR/config.json"
    const val TEE_SIMULATOR_KEYBOX = "$TEE_SIMULATOR_DIR/keybox.xml"
    const val OH_MY_KEYMINT_DIR = "/data/misc/keystore/omk"
    const val OH_MY_KEYMINT_CONFIG = "$OH_MY_KEYMINT_DIR/config.toml"
    const val OH_MY_KEYMINT_INJECTOR = "$OH_MY_KEYMINT_DIR/injector.toml"
    const val OH_MY_KEYMINT_KEYBOX = "$OH_MY_KEYMINT_DIR/keybox.xml"
    const val BOOT_HASH = "/data/adb/boot_hash"
    const val PROP_HANDLER_DISABLE = "/data/adb/disable_prop_handler"
}
