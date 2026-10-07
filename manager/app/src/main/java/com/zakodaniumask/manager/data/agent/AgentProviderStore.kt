// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import android.content.Context
import com.zakodaniumask.manager.data.agent.llm.LlmProviderType
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved provider profile. The [type] selects the wire protocol, while the
 * endpoint/key/model and optional overrides are copied into the active
 * [AgentSettings] when the profile is selected.
 */
data class AgentProviderProfile(
    val id: String,
    val name: String,
    val type: LlmProviderType,
    val endpoint: String = "",
    val apiKey: String = "",
    val apiPath: String = "",
    val userAgent: String = "",
    val extraHeaders: String = "",
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
) {
    /** Endpoint with the type default applied when left blank. */
    val effectiveEndpoint: String get() = endpoint.ifBlank { type.defaultEndpoint }

    /** Model to use, falling back to the first known model. */
    val effectiveModel: String get() = selectedModel.ifBlank { models.firstOrNull().orEmpty() }
}

/**
 * Persists the list of provider profiles and the active selection as JSON in
 * the app files directory.
 */
class AgentProviderStore(private val context: Context) {

    data class Snapshot(
        val activeId: String?,
        val providers: List<AgentProviderProfile>,
    ) {
        fun active(): AgentProviderProfile? = providers.firstOrNull { it.id == activeId }
    }

    private val file: File get() = File(context.filesDir, "agent_providers.json")

    fun load(): Snapshot {
        val raw = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
        if (raw.isBlank()) return Snapshot(null, emptyList())
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("providers") ?: JSONArray()
            val providers = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        AgentProviderProfile(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            type = LlmProviderType.fromId(obj.optString("type")),
                            endpoint = obj.optString("endpoint"),
                            apiKey = obj.optString("apiKey"),
                            apiPath = obj.optString("apiPath"),
                            userAgent = obj.optString("userAgent"),
                            extraHeaders = obj.optString("extraHeaders"),
                            models = obj.optJSONArray("models").toStringList(),
                            selectedModel = obj.optString("selectedModel"),
                        )
                    )
                }
            }
            Snapshot(root.optString("activeId").ifBlank { null }, providers)
        }.getOrDefault(Snapshot(null, emptyList()))
    }

    fun save(snapshot: Snapshot) {
        val root = JSONObject()
        root.put("activeId", snapshot.activeId.orEmpty())
        val array = JSONArray()
        snapshot.providers.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("type", profile.type.id)
                    .put("endpoint", profile.endpoint)
                    .put("apiKey", profile.apiKey)
                    .put("apiPath", profile.apiPath)
                    .put("userAgent", profile.userAgent)
                    .put("extraHeaders", profile.extraHeaders)
                    .put("models", JSONArray(profile.models))
                    .put("selectedModel", profile.selectedModel)
            )
        }
        root.put("providers", array)
        runCatching { file.writeText(root.toString()) }
    }

    fun upsert(snapshot: Snapshot, profile: AgentProviderProfile): Snapshot {
        val existing = snapshot.providers.indexOfFirst { it.id == profile.id }
        val providers = if (existing >= 0) {
            snapshot.providers.toMutableList().also { it[existing] = profile }
        } else {
            snapshot.providers + profile
        }
        val activeId = snapshot.activeId ?: profile.id
        return Snapshot(activeId, providers).also { save(it) }
    }

    fun delete(snapshot: Snapshot, id: String): Snapshot {
        val providers = snapshot.providers.filterNot { it.id == id }
        val activeId = if (snapshot.activeId == id) providers.firstOrNull()?.id else snapshot.activeId
        return Snapshot(activeId, providers).also { save(it) }
    }

    fun setActive(snapshot: Snapshot, id: String): Snapshot =
        Snapshot(id, snapshot.providers).also { save(it) }

    /** Seeds a profile from the current settings when the store is empty. */
    fun seeded(settings: AgentSettings): Snapshot {
        val current = load()
        if (current.providers.isNotEmpty()) return current
        val id = "default"
        val profile = AgentProviderProfile(
            id = id,
            name = settings.provider.label,
            type = settings.provider,
            endpoint = settings.endpoint,
            apiKey = settings.apiKey,
            apiPath = settings.apiPath,
            userAgent = settings.userAgent,
            extraHeaders = settings.extraHeaders,
            selectedModel = settings.model,
        )
        return Snapshot(id, listOf(profile)).also { save(it) }
    }
}

/** Copies the profile into runtime settings so the engine uses it. */
fun applyProfile(base: AgentSettings, profile: AgentProviderProfile): AgentSettings = base.copy(
    provider = profile.type,
    endpoint = profile.endpoint,
    apiKey = profile.apiKey,
    apiPath = profile.apiPath,
    userAgent = profile.userAgent,
    extraHeaders = profile.extraHeaders,
    model = profile.effectiveModel,
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val value = optString(i)
            if (value.isNotBlank()) add(value)
        }
    }
}
