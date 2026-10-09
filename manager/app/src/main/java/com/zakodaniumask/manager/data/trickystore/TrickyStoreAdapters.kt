// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store/src/adapters; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes one backend's on-disk configuration in the shared [ConfigData] shape. */
interface ConfigAdapter {
    val backend: Backend
    val configDir: String
    fun keyboxPath(shell: Shell): String = "$configDir/keybox.xml"
    fun policySchema(): PolicySchema
    fun restartKeys(): List<String> = emptyList()
    fun read(shell: Shell): ConfigData
    fun write(shell: Shell, config: ConfigData): Result<Unit>
}

object Backends {
    fun forBackend(backend: Backend): ConfigAdapter = when (backend) {
        Backend.TRICKY_STORE -> TrickyStoreAdapter
        Backend.TRICKY_STORE_LEGACY -> TrickyStoreLegacyAdapter
        Backend.TEE_SIMULATOR -> TeeSimulatorAdapter
        Backend.OH_MY_KEYMINT -> OhMyKeymintAdapter
    }
}

internal fun patchFields(): List<PolicyField> = listOf(
    PolicyField(
        key = "os_patch",
        label = "System Patch",
        placeholder = "YYYYMM",
        options = listOf("prop", "no"),
        maxLength = 6,
        hint = "YYYYMM | prop | no",
    ),
    PolicyField(
        key = "vendor_patch",
        label = "Vendor Patch",
        placeholder = "YYYYMMDD",
        options = listOf("prop", "no"),
        maxLength = 8,
        hint = "YYYYMMDD | prop | no",
    ),
    PolicyField(
        key = "boot_patch",
        label = "Boot Patch",
        placeholder = "YYYYMMDD",
        options = listOf("prop", "no"),
        maxLength = 8,
        hint = "YYYYMMDD | prop | no",
    ),
)

// ---------------------------------------------------------------------------
// Tricky Store (config.ini)
// ---------------------------------------------------------------------------

object TrickyStoreAdapter : ConfigAdapter {
    override val backend = Backend.TRICKY_STORE
    override val configDir = TrickystorePaths.TRICKY_STORE_DIR

    override fun policySchema() = PolicySchema(
        supportsAppMode = true,
        supportsPerAppPolicy = true,
        defaultPolicy = patchFields(),
    )

    override fun read(shell: Shell): ConfigData {
        val raw = SuFileUtils.readText(shell, TrickystorePaths.TRICKY_STORE_CONFIG) ?: return ConfigData()
        return parseIni(raw)
    }

    override fun write(shell: Shell, config: ConfigData): Result<Unit> = runCatching {
        val path = TrickystorePaths.TRICKY_STORE_CONFIG
        val body = buildString {
            val sections = mutableListOf<String>()
            if (config.defaultPolicy.isNotEmpty()) {
                sections += serializeSection("default_policy", config.defaultPolicy)
            }
            sections += buildString {
                appendLine("[target]")
                config.targets.forEach { appendLine(it.packageName + it.mode.marker) }
            }.trimEnd()
            config.perAppPolicy.toSortedMap().forEach { (pkg, policy) ->
                if (policy.isNotEmpty()) sections += serializeSection(pkg, policy)
            }
            append(sections.joinToString("\n\n"))
            append('\n')
        }
        val existing = SuFileUtils.readText(shell, path)
        val unknown = existing?.let { unknownSections(it) }.orEmpty()
        val full = buildString {
            append(body)
            unknown.forEach { append('\n'); append(it); append('\n') }
        }
        if (!SuFileUtils.writeText(shell, path, full)) error("failed to write config.ini")
    }

    private fun serializeSection(name: String, policy: Map<String, String>): String =
        buildString {
            appendLine("[$name]")
            policy.toSortedMap().forEach { (key, value) -> appendLine("$key = $value") }
        }.trimEnd()

    /** Per-app sections are named after packages (contain a dot); everything else is kept. */
    private fun isKnownSection(name: String) = name == "target" || name == "default_policy" || name.contains('.')

    private fun unknownSections(raw: String): List<String> {
        val sections = mutableListOf<String>()
        var current: MutableList<String>? = null
        for (line in raw.lines()) {
            val trimmed = line.trim()
            val name = trimmed.removePrefix("[").removeSuffix("]").takeIf { trimmed.startsWith("[") && trimmed.endsWith("]") }
            if (name != null) {
                current?.let { sections += it.joinToString("\n").trimEnd() }
                current = if (!isKnownSection(name.trim())) mutableListOf(trimmed) else null
            } else {
                current?.add(line)
            }
        }
        current?.let { sections += it.joinToString("\n").trimEnd() }
        return sections
    }

    private fun parseIni(raw: String): ConfigData {
        val targets = mutableListOf<TargetEntry>()
        val defaultPolicy = linkedMapOf<String, String>()
        val perApp = linkedMapOf<String, MutableMap<String, String>>()
        var section: String? = null
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val name = trimmed.removePrefix("[").removeSuffix("]")
                .takeIf { trimmed.startsWith("[") && trimmed.endsWith("]") }
            if (name != null) {
                section = name.trim()
                continue
            }
            val current = section
            when {
                current == "target" -> {
                    val (pkg, mode) = TargetMode.split(trimmed)
                    if (pkg.isNotEmpty()) targets += TargetEntry(pkg, mode)
                }
                current == "default_policy" -> insertKv(defaultPolicy, trimmed)
                current != null && isKnownSection(current) ->
                    insertKv(perApp.getOrPut(current) { linkedMapOf() }, trimmed)
            }
        }
        return ConfigData(targets, defaultPolicy, perApp.mapValues { it.value.toMap() })
    }

    private fun insertKv(policy: MutableMap<String, String>, line: String) {
        val index = line.indexOf('=')
        if (index > 0) policy[line.substring(0, index).trim()] = line.substring(index + 1).trim()
    }
}

// ---------------------------------------------------------------------------
// Tricky Store legacy (target.txt + security_patch.txt)
// ---------------------------------------------------------------------------

object TrickyStoreLegacyAdapter : ConfigAdapter {
    override val backend = Backend.TRICKY_STORE_LEGACY
    override val configDir = TrickystorePaths.TRICKY_STORE_DIR

    override fun policySchema() = PolicySchema(
        supportsAppMode = true,
        supportsPerAppPolicy = false,
        defaultPolicy = patchFields(),
    )

    override fun read(shell: Shell): ConfigData {
        val targets = SuFileUtils.readText(shell, TrickystorePaths.TRICKY_STORE_TARGETS)
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.map { TargetMode.split(it).let { (pkg, mode) -> TargetEntry(pkg, mode) } }
            ?.filter { it.packageName.isNotEmpty() }
            .orEmpty()
        val policy = SuFileUtils.readText(shell, TrickystorePaths.TRICKY_STORE_PATCH)
            ?.let(::parseSecurityPatch)
            .orEmpty()
        return ConfigData(targets = targets, defaultPolicy = policy)
    }

    override fun write(shell: Shell, config: ConfigData): Result<Unit> = runCatching {
        val body = config.targets.joinToString("") { "${it.packageName}${it.mode.marker}\n" }
        if (!SuFileUtils.writeText(shell, TrickystorePaths.TRICKY_STORE_TARGETS, body)) {
            error("failed to write target.txt")
        }
        val sp = serializeSecurityPatch(config.defaultPolicy)
        if (sp.isEmpty()) {
            SuFileUtils.delete(shell, TrickystorePaths.TRICKY_STORE_PATCH)
        } else if (!SuFileUtils.writeText(shell, TrickystorePaths.TRICKY_STORE_PATCH, sp)) {
            error("failed to write security_patch.txt")
        }
    }

    private fun parseSecurityPatch(raw: String): Map<String, String> {
        val policy = linkedMapOf<String, String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val index = trimmed.indexOf('=')
            if (index < 0) {
                setAll(policy, trimmed)
                continue
            }
            val key = trimmed.substring(0, index).trim()
            val value = trimmed.substring(index + 1).trim()
            when (key) {
                "system" -> policy["os_patch"] = value
                "boot" -> policy["boot_patch"] = value
                "vendor" -> policy["vendor_patch"] = value
                "all" -> setAll(policy, value)
            }
        }
        return policy
    }

    private fun setAll(policy: MutableMap<String, String>, value: String) {
        policy["os_patch"] = stripDay(value)
        policy["boot_patch"] = value.trim()
        policy["vendor_patch"] = value.trim()
    }

    private fun stripDay(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length == 8 && trimmed.all { it.isDigit() }) trimmed.substring(0, 6) else trimmed
    }

    private fun serializeSecurityPatch(policy: Map<String, String>): String {
        fun noop(key: String) = policy[key]?.let { it == "no" } != false
        if (listOf("os_patch", "boot_patch", "vendor_patch").all { noop(it) }) return ""
        return buildList {
            policy["os_patch"]?.let { add("system=$it") }
            policy["boot_patch"]?.let { add("boot=$it") }
            policy["vendor_patch"]?.let { add("vendor=$it") }
        }.joinToString("\n")
    }
}

// ---------------------------------------------------------------------------
// TEESimulator (config.json)
// ---------------------------------------------------------------------------

object TeeSimulatorAdapter : ConfigAdapter {
    private const val DEFAULT_PROFILE = "default"

    override val backend = Backend.TEE_SIMULATOR
    override val configDir = TrickystorePaths.TEE_SIMULATOR_DIR

    override fun keyboxPath(shell: Shell): String {
        val configured = runCatching {
            JSONObject(SuFileUtils.readText(shell, TrickystorePaths.TEE_SIMULATOR_CONFIG).orEmpty())
                .optJSONObject("profiles")
                ?.optJSONObject(DEFAULT_PROFILE)
                ?.optString("keybox")
                .orEmpty()
        }.getOrDefault("")
        val name = configured.takeIf { isSafeRelative(it) } ?: "keybox.xml"
        return "$configDir/$name"
    }

    override fun policySchema(): PolicySchema {
        fun patch(key: String, label: String, placeholder: String) = PolicyField(
            key = key,
            label = label,
            options = listOf("today", "system_property", "harvested", "no"),
            placeholder = placeholder,
            hint = "today | system_property | harvested | no | YYYY-MM(-DD)",
        )
        val fields = mutableListOf(
            PolicyField("mode", "Operation Mode", options = listOf("patch", "generation"), placeholder = "patch", hint = "patch | generation"),
            patch("os_patch", "System Patch", "today"),
            patch("vendor_patch", "Vendor Patch", "YYYY-MM-05"),
            patch("boot_patch", "Boot Patch", "YYYY-MM-05"),
            PolicyField("os_version", "OS Version", options = listOf("system_property", "harvested"), placeholder = "16 | 16.0.0 | 160000", hint = "system_property | harvested | version"),
        )
        IDENTITY_FIELDS.forEach { (key, label) -> fields += PolicyField(key, label) }
        fields += PolicyField("autoIncludeNewApps", "Auto-include New Apps", kind = FieldKind.BOOLEAN)
        return PolicySchema(supportsAppMode = false, supportsPerAppPolicy = false, defaultPolicy = fields)
    }

    override fun read(shell: Shell): ConfigData {
        val root = readRoot(shell) ?: return ConfigData()
        return decode(root)
    }

    override fun write(shell: Shell, config: ConfigData): Result<Unit> = runCatching {
        val existing = readRoot(shell)
        val version = existing?.optLong("version")
        if (version != null && version != 1L) error("config.json version $version is not supported (expected 1)")
        val root = encode(config, existing)
        validate(shell, root)
        if (!SuFileUtils.writeText(shell, TrickystorePaths.TEE_SIMULATOR_CONFIG, root.toString(2))) {
            error("failed to write config.json")
        }
    }

    private val IDENTITY_FIELDS = listOf(
        "brand" to "Brand",
        "device" to "Device",
        "product" to "Product",
        "manufacturer" to "Manufacturer",
        "model" to "Model",
        "serial" to "Serial",
        "imei" to "IMEI",
        "meid" to "MEID",
        "imei2" to "IMEI 2",
    )

    private fun readRoot(shell: Shell): JSONObject? {
        val raw = SuFileUtils.readText(shell, TrickystorePaths.TEE_SIMULATOR_CONFIG) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun isSafeRelative(name: String) =
        name.isNotEmpty() && !name.startsWith("/") && !name.split('/').any { it == ".." || it.isEmpty() }

    private fun decode(root: JSONObject): ConfigData {
        val targets = linkedSetOf<String>()
        val defaultPolicy = linkedMapOf<String, String>()
        val profiles = root.optJSONObject("profiles")
        if (profiles != null) {
            profiles.keys().forEach { key ->
                val apps = profiles.optJSONObject(key)?.optJSONArray("apps") ?: return@forEach
                for (i in 0 until apps.length()) {
                    apps.optString(i).takeIf { it.isNotEmpty() }?.let(targets::add)
                }
            }
            profiles.optJSONObject(DEFAULT_PROFILE)?.let { default ->
                default.optJSONObject("patchLevel")?.let { patch ->
                    copyStr(patch, "system", defaultPolicy, "os_patch")
                    copyStr(patch, "vendor", defaultPolicy, "vendor_patch")
                    copyStr(patch, "boot", defaultPolicy, "boot_patch")
                }
                copyStr(default, "mode", defaultPolicy, "mode")
                copyStr(default, "osVersion", defaultPolicy, "os_version")
                IDENTITY_FIELDS.forEach { (key, _) -> copyStr(default, key, defaultPolicy, key) }
                if (default.has("autoIncludeNewApps")) {
                    defaultPolicy["autoIncludeNewApps"] = default.optBoolean("autoIncludeNewApps").toString()
                }
            }
        }
        return ConfigData(
            targets = targets.map { TargetEntry(it) },
            defaultPolicy = defaultPolicy,
        )
    }

    private fun copyStr(source: JSONObject, from: String, policy: MutableMap<String, String>, to: String) {
        if (!source.has(from) || source.isNull(from)) return
        val value = source.opt(from)
        val text = when (value) {
            is String -> value.trim()
            is Number -> value.toString()
            else -> return
        }
        if (text.isNotEmpty()) policy[to] = text
    }

    private fun encode(config: ConfigData, existing: JSONObject?): JSONObject {
        val root = existing?.takeIf { it.length() > 0 } ?: JSONObject()
        if (!root.has("version")) root.put("version", 1)
        val profiles = root.optJSONObject("profiles") ?: JSONObject().also { root.put("profiles", it) }

        val unassigned = config.targets.map { it.packageName }.toMutableList()
        profiles.keys().asSequence().toList().forEach { key ->
            val profile = profiles.optJSONObject(key) ?: return@forEach
            val apps = profile.optJSONArray("apps")
            val kept = JSONArray()
            if (apps != null) {
                for (i in 0 until apps.length()) {
                    val app = apps.optString(i)
                    if (unassigned.remove(app)) kept.put(app)
                }
            }
            profile.put("apps", kept)
        }

        val default = profiles.optJSONObject(DEFAULT_PROFILE) ?: JSONObject().also {
            it.put("keybox", "keybox.xml")
            it.put("mode", "patch")
            it.put("apps", JSONArray())
            profiles.put(DEFAULT_PROFILE, it)
        }
        val defaultApps = default.optJSONArray("apps") ?: JSONArray().also { default.put("apps", it) }
        unassigned.forEach { defaultApps.put(it) }

        val patch = default.optJSONObject("patchLevel") ?: JSONObject().also { default.put("patchLevel", it) }
        patch.put("system", config.defaultPolicy["os_patch"] ?: "today")
        patch.put("vendor", config.defaultPolicy["vendor_patch"] ?: "YYYY-MM-05")
        patch.put("boot", config.defaultPolicy["boot_patch"] ?: "YYYY-MM-05")
        default.put("mode", config.defaultPolicy["mode"] ?: "patch")
        default.put("osVersion", config.defaultPolicy["os_version"] ?: "")
        IDENTITY_FIELDS.forEach { (key, _) -> default.put(key, config.defaultPolicy[key] ?: "") }
        config.defaultPolicy["autoIncludeNewApps"]?.let { default.put("autoIncludeNewApps", it == "true") }
        return root
    }

    private fun validate(shell: Shell, root: JSONObject) {
        val profiles = root.optJSONObject("profiles")
        require(profiles != null && profiles.length() > 0) { "config.json has no profiles" }
        var autoInclude: String? = null
        profiles.keys().forEach { id ->
            val profile = profiles.optJSONObject(id)!!
            val keybox = profile.optString("keybox").trim()
            require(isSafeRelative(keybox)) { "profile '$id' has no valid keybox" }
            require(SuFileUtils.isFile(shell, "$configDir/$keybox")) {
                "profile '$id' keybox $keybox is missing; install a keybox first"
            }
            val mode = profile.optString("mode", "patch")
            require(mode == "patch" || mode == "generation") { "profile '$id' has invalid mode '$mode'" }
            val auto = profile.optBoolean("autoIncludeNewApps", false)
            if (auto) {
                require(autoInclude == null) { "profiles '$autoInclude' and '$id' both auto-include new apps; only one may" }
                autoInclude = id
            }
            val apps = profile.optJSONArray("apps")
            require((apps != null && apps.length() > 0) || auto) {
                "profile '$id' would have no apps; keep one of its apps selected or enable auto-include"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// OhMyKeymint (config.toml + injector.toml)
// ---------------------------------------------------------------------------

object OhMyKeymintAdapter : ConfigAdapter {
    private val TRUST_FIELDS = listOf(
        "os_version", "security_patch", "os_patchlevel", "vendor_patchlevel", "boot_patchlevel",
        "vb_key", "vb_hash", "verified_boot_state", "device_locked",
    )
    private val BOOLEAN_FIELDS = setOf("verified_boot_state", "device_locked")
    private val RESTART_FIELDS = listOf(
        "os_version", "vb_key", "vb_hash", "verified_boot_state", "device_locked", "boot_patchlevel",
    )

    override val backend = Backend.OH_MY_KEYMINT
    override val configDir = TrickystorePaths.OH_MY_KEYMINT_DIR

    override fun policySchema(): PolicySchema {
        fun patch(key: String, label: String) = PolicyField(
            key = key,
            label = label,
            options = listOf("auto", "latest"),
            placeholder = "YYYY-MM-DD",
            maxLength = 10,
            hint = "auto | latest | YYYY-MM-DD",
        )
        fun bootHash(key: String, label: String) = PolicyField(
            key = key,
            label = label,
            options = listOf("auto", "random"),
            placeholder = "64 hex chars",
            maxLength = 64,
            multiline = true,
            hint = "auto | random | 64 hex chars",
        )
        return PolicySchema(
            supportsAppMode = false,
            supportsPerAppPolicy = false,
            defaultPolicy = listOf(
                PolicyField("os_version", "OS Version", options = listOf("auto"), placeholder = "16", maxLength = 2, hint = "auto | Android major version"),
                patch("security_patch", "Security Patch"),
                patch("os_patchlevel", "OS Patch Level"),
                patch("vendor_patchlevel", "Vendor Patch Level"),
                patch("boot_patchlevel", "Boot Patch Level"),
                bootHash("vb_key", "VB Key"),
                bootHash("vb_hash", "VB Hash"),
                PolicyField("verified_boot_state", "Verified Boot State", kind = FieldKind.BOOLEAN),
                PolicyField("device_locked", "Device Locked", kind = FieldKind.BOOLEAN),
            ),
        )
    }

    override fun restartKeys(): List<String> = RESTART_FIELDS

    override fun read(shell: Shell): ConfigData {
        val targets = SuFileUtils.readText(shell, TrickystorePaths.OH_MY_KEYMINT_INJECTOR)
            ?.let(::parseScoop)
            ?.map { TargetEntry(it) }
            .orEmpty()
        val defaultPolicy = SuFileUtils.readText(shell, TrickystorePaths.OH_MY_KEYMINT_CONFIG)
            ?.let(::parseTrustSection)
            .orEmpty()
        return ConfigData(targets = targets, defaultPolicy = defaultPolicy)
    }

    override fun write(shell: Shell, config: ConfigData): Result<Unit> = runCatching {
        val injectorPath = TrickystorePaths.OH_MY_KEYMINT_INJECTOR
        val injector = SuFileUtils.readText(shell, injectorPath) ?: ""
        val updatedInjector = replaceScoop(injector, config.targets.map { it.packageName })
        if (!SuFileUtils.writeText(shell, injectorPath, updatedInjector)) error("failed to write injector.toml")

        if (config.defaultPolicy.isEmpty()) return@runCatching
        val configPath = TrickystorePaths.OH_MY_KEYMINT_CONFIG
        val existing = SuFileUtils.readText(shell, configPath)
            ?: error("OhMyKeymint has not created config.toml yet; reboot once with it enabled")
        val updated = replaceTrust(existing, config.defaultPolicy)
        if (!SuFileUtils.writeText(shell, configPath, updated)) error("failed to write config.toml")
    }

    /** Parses `scoop = [ "a", "b" ]`, possibly spanning lines, preserving nothing else. */
    private fun parseScoop(raw: String): List<String> {
        val match = Regex("(?s)^\\s*scoop\\s*=\\s*\\[(.*?)\\]", RegexOption.MULTILINE).find(raw) ?: return emptyList()
        return Regex("\"([^\"]*)\"").findAll(match.groupValues[1]).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toList()
    }

    private fun replaceScoop(raw: String, names: List<String>): String {
        val array = if (names.isEmpty()) {
            "scoop = []"
        } else {
            "scoop = [\n" + names.joinToString("") { "  \"$it\",\n" } + "]"
        }
        val regex = Regex("(?s)^\\s*scoop\\s*=\\s*\\[.*?\\]", RegexOption.MULTILINE)
        return if (regex.containsMatchIn(raw)) regex.replaceFirst(raw, Regex.escapeReplacement(array)) else "$raw\n$array\n"
    }

    private fun parseTrustSection(raw: String): Map<String, String> {
        val policy = linkedMapOf<String, String>()
        var inTrust = false
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inTrust = trimmed.removePrefix("[").removeSuffix("]").trim() == "trust"
                continue
            }
            if (!inTrust || trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val index = trimmed.indexOf('=')
            if (index <= 0) continue
            val key = trimmed.substring(0, index).trim()
            val value = trimComment(trimmed.substring(index + 1).trim()).trim().trim('"')
            if (key in TRUST_FIELDS) policy[key] = value
        }
        return policy
    }

    private fun replaceTrust(raw: String, policy: Map<String, String>): String {
        val lines = raw.lines().toMutableList()
        val result = mutableListOf<String>()
        var inTrust = false
        var trustFound = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (inTrust) result += policy.keys.filter { key -> result.none { it.trimStart().startsWith("$key ") || it.trimStart().startsWith("$key=") } }.map { renderTrust(it, policy[it]!!) }
                inTrust = trimmed.removePrefix("[").removeSuffix("]").trim() == "trust"
                if (inTrust) trustFound = true
                result += line
                continue
            }
            if (inTrust && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val index = trimmed.indexOf('=')
                val key = if (index > 0) trimmed.substring(0, index).trim() else ""
                if (key in policy) {
                    result += renderTrust(key, policy[key]!!)
                    continue
                }
            }
            result += line
        }
        if (inTrust) {
            policy.keys.forEach { key ->
                if (result.none { it.trimStart().startsWith("$key ") || it.trimStart().startsWith("$key=") }) {
                    result += renderTrust(key, policy[key]!!)
                }
            }
        } else if (!trustFound && policy.isNotEmpty()) {
            result += "[trust]"
            policy.forEach { (key, value) -> result += renderTrust(key, value) }
        }
        return result.joinToString("\n")
    }

    private fun renderTrust(key: String, value: String): String {
        val rendered = when {
            key == "os_version" && value.toLongOrNull() != null -> value
            key in BOOLEAN_FIELDS && (value == "true" || value == "false") -> value
            else -> "\"$value\""
        }
        return "$key = $rendered"
    }

    private fun trimComment(value: String): String {
        var inQuote = false
        value.forEachIndexed { index, c ->
            if (c == '"') inQuote = !inQuote
            if (c == '#' && !inQuote) return value.substring(0, index)
        }
        return value
    }
}
