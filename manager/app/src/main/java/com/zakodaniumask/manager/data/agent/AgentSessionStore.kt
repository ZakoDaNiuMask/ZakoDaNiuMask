// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent

import android.app.Application
import com.zakodaniumask.manager.data.agent.llm.LlmMessage
import com.zakodaniumask.manager.data.agent.llm.LlmToolCall
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AgentSessionMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

/**
 * Lightweight multi-session store for the agent conversation. Uses JSON files
 * under the app's files dir; deliberately dependency-free so it can later be
 * swapped for a Room-backed store without touching callers.
 */
class AgentSessionStore(
    application: Application,
) {
    private val root = File(application.filesDir, "agent_sessions").apply { mkdirs() }
    private val indexFile = File(root, "sessions.json")
    private val lock = Any()

    suspend fun list(): List<AgentSessionMeta> = withContext(Dispatchers.IO) {
        synchronized(lock) { readIndex() }
    }

    suspend fun create(title: String): AgentSessionMeta = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val meta = AgentSessionMeta(
                id = "s_" + System.currentTimeMillis().toString(36) + "_" +
                    (0..9999).random().toString(36),
                title = title,
                updatedAt = System.currentTimeMillis(),
            )
            writeIndex(readIndex() + meta)
            meta
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeIndex(readIndex().map { if (it.id == id) it.copy(title = title) else it })
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeIndex(readIndex().filterNot { it.id == id })
            messageFile(id).delete()
        }
    }

    suspend fun loadMessages(id: String): List<LlmMessage> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = messageFile(id)
            if (!file.exists()) return@synchronized emptyList()
            runCatching {
                val array = JSONArray(file.readText())
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        add(decodeMessage(obj))
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun saveMessages(id: String, messages: List<LlmMessage>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val array = JSONArray()
            messages.forEach { array.put(encodeMessage(it)) }
            messageFile(id).writeText(array.toString())
            writeIndex(
                readIndex().map {
                    if (it.id == id) it.copy(updatedAt = System.currentTimeMillis()) else it
                }
            )
        }
    }

    private fun messageFile(id: String) = File(root, "$id.json")

    private fun readIndex(): List<AgentSessionMeta> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        AgentSessionMeta(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            updatedAt = obj.optLong("updatedAt"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(sessions: List<AgentSessionMeta>) {
        val array = JSONArray()
        sessions.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("updatedAt", it.updatedAt)
            )
        }
        indexFile.writeText(array.toString())
    }

    private fun encodeMessage(message: LlmMessage): JSONObject {
        val calls = JSONArray()
        message.toolCalls.forEach { call ->
            calls.put(
                JSONObject()
                    .put("id", call.id)
                    .put("name", call.name)
                    .put("arguments", call.arguments)
            )
        }
        return JSONObject()
            .put("role", message.role)
            .put("content", message.content)
            .put("toolCallId", message.toolCallId ?: JSONObject.NULL)
            .put("toolCalls", calls)
    }

    private fun decodeMessage(obj: JSONObject): LlmMessage {
        val calls = buildList {
            val array = obj.optJSONArray("toolCalls") ?: JSONArray()
            for (i in 0 until array.length()) {
                val call = array.optJSONObject(i) ?: continue
                add(
                    LlmToolCall(
                        id = call.optString("id"),
                        name = call.optString("name"),
                        arguments = call.optString("arguments"),
                    )
                )
            }
        }
        return LlmMessage(
            role = obj.optString("role"),
            content = obj.optString("content"),
            toolCalls = calls,
            toolCallId = if (obj.isNull("toolCallId")) null else obj.optString("toolCallId"),
        )
    }
}
