// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from OpenMinis (https://github.com/OpenMinis/OpenMinis), GPL-3.0:
//   provider/ToolJsonRepair.kt  -> repair()
//   provider/ToolPairing.kt     -> pairingKey()
// Modified to work against this project's LlmTool model and JsonObject args.
package com.zakodaniumask.manager.data.agent.llm

import org.json.JSONObject

/**
 * JSON repair for malformed / incomplete tool calls. Three strategies, applied
 * in order: truncation closure, type coercion on required fields, and a
 * Levenshtein-1 fuzzy field rename.
 */
object ToolCallRepair {

    /** Mutates [args] and returns the repair tags that fired. */
    fun repair(
        toolName: String,
        args: JSONObject,
        rawTail: String?,
        tools: List<LlmTool>,
    ): List<String> {
        val tool = tools.firstOrNull { it.name == toolName } ?: return emptyList()
        val required = tool.parameters.optJSONArray("required").toStringList()
        val schemaFields = tool.parameters.optJSONObject("properties")?.let { props ->
            buildSet { props.keys().forEach { add(it) } }
        } ?: emptySet()

        val repairs = mutableListOf<String>()

        // 1. Truncation repair: empty dict but the raw stream tail looks cut.
        if (args.length() == 0 && !rawTail.isNullOrBlank()) {
            val tail = rawTail.trim()
            val suffixes = listOf("", "\"", "\"}", "\"]}", "}", "}}", "]}", "]}}", "]", "]]")
            for (suffix in suffixes) {
                val parsed = tryParseObject(tail + suffix) ?: continue
                parsed.keys().forEach { key -> args.put(key, parsed.opt(key)) }
                repairs.add("truncation+" + if (suffix.isEmpty()) "noop" else suffix)
                break
            }
        }

        // 2. Type coercion on required fields.
        for (field in required) {
            if (!args.has(field)) continue
            val raw = args.opt(field) ?: continue
            if (raw is String || raw === JSONObject.NULL) continue
            val coerced = raw.toString()
            if (coerced.trim().isNotEmpty()) {
                args.put(field, coerced)
                repairs.add("type-coerce:$field")
            }
        }

        // 3. Fuzzy field-name match for missing required fields.
        for (field in required) {
            if (args.has(field)) continue
            val candidate = args.keys().asSequence()
                .firstOrNull { key -> key !in schemaFields && levenshteinAtMostOne(key, field) }
                ?: continue
            args.put(field, args.opt(candidate))
            args.remove(candidate)
            repairs.add("fuzzy:$candidate->$field")
        }

        return repairs
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun tryParseObject(s: String): JSONObject? = try {
        JSONObject(s)
    } catch (_: Throwable) {
        null
    }

    /** `true` iff the case-insensitive Levenshtein distance is exactly 1. */
    private fun levenshteinAtMostOne(a: String, b: String): Boolean {
        val al = a.lowercase()
        val bl = b.lowercase()
        if (al == bl) return false
        val diff = al.length - bl.length
        if (diff > 1 || diff < -1) return false
        if (al.length == bl.length) {
            var mismatches = 0
            for (i in al.indices) {
                if (al[i] != bl[i]) {
                    mismatches += 1
                    if (mismatches > 1) return false
                }
            }
            return mismatches == 1
        }
        val longer = if (al.length > bl.length) al else bl
        val shorter = if (al.length > bl.length) bl else al
        var i = 0
        var j = 0
        var skipped = false
        while (i < longer.length && j < shorter.length) {
            if (longer[i] == shorter[j]) {
                i += 1; j += 1
            } else if (!skipped) {
                i += 1; skipped = true
            } else {
                return false
            }
        }
        return true
    }
}

/**
 * The one notion of "this tool result answers that tool call". The OpenAI
 * Responses path stores a combined `"<call_id>|<fc_id>"` id; the pairing key is
 * the part before `|`. Chat Completions / Anthropic / Gemini ids contain no
 * `|`, so this is the identity for them.
 */
object ToolPairing {
    fun key(id: String): String {
        val sep = id.indexOf('|')
        return if (sep < 0) id else id.substring(0, sep)
    }
}
