// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.mcp

import com.zakodaniumask.manager.data.shell.KsuCliRepository
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val READY_MARKER = "__KSU_MCP_READY__"

/**
 * Bridges the app to a root `ksud mcp` process over stdio.
 *
 * The manager obtains root through `ksud debug su` (a root `sh`), which is
 * exactly how libsu is configured in this app. We start that shell as a normal
 * child process, ask it to `exec ksud mcp` so it is replaced by the MCP server,
 * then speak newline-delimited JSON-RPC over the same pipes.
 */
class KsudMcpProcess(
    private val ksuCliRepository: KsuCliRepository,
) {
    private var process: Process? = null
    var reader: BufferedReader? = null
        private set
    var writer: BufferedWriter? = null
        private set

    val isRunning: Boolean
        get() = process?.isAlive == true

    /** Start the server; returns false if no root entry point worked. */
    fun start(): Boolean {
        if (isRunning) return true
        val ksud = ksuCliRepository.getKsuDaemonPath()
        // Preferred: the ksud-provided root shell shebang (`ksud debug su`),
        // then exec ksud mcp so it replaces the shell. Fallbacks cover images
        // where the manager's root entry point differs.
        val launches = listOf(
            Launch(
                command = listOf(ksud, "debug", "su"),
                prelude = listOf("echo $READY_MARKER", "exec ${shellQuote(ksud)} mcp"),
                expectMarker = true,
            ),
            Launch(
                command = listOf("su", "-c", "exec ${shellQuote(ksud)} mcp"),
                prelude = emptyList(),
                expectMarker = false,
            ),
            Launch(
                command = listOf("su", "0", "exec ${shellQuote(ksud)} mcp"),
                prelude = emptyList(),
                expectMarker = false,
            ),
        )
        for (launch in launches) {
            if (tryLaunch(launch)) return true
        }
        return false
    }

    private fun tryLaunch(launch: Launch): Boolean {
        val proc = try {
            ProcessBuilder(launch.command)
                .redirectErrorStream(false)
                .start()
        } catch (_: Throwable) {
            return false
        }
        process = proc
        val out = BufferedWriter(OutputStreamWriter(proc.outputStream))
        val input = BufferedReader(InputStreamReader(proc.inputStream))
        try {
            launch.prelude.forEach {
                out.write(it)
                out.write("\n")
            }
            if (launch.prelude.isNotEmpty()) out.flush()
        } catch (_: Throwable) {
            proc.destroy()
            process = null
            return false
        }
        if (launch.expectMarker) {
            val deadline = System.currentTimeMillis() + 5_000
            var found = false
            while (System.currentTimeMillis() < deadline) {
                val line = try {
                    input.readLine()
                } catch (_: Throwable) {
                    null
                } ?: break
                if (line.contains(READY_MARKER)) {
                    found = true
                    break
                }
            }
            if (!found) {
                proc.destroy()
                process = null
                return false
            }
        }
        if (!proc.isAlive) {
            process = null
            return false
        }
        reader = input
        writer = out
        return true
    }

    private data class Launch(
        val command: List<String>,
        val prelude: List<String>,
        val expectMarker: Boolean,
    )

    fun stop() {
        try {
            writer?.close()
        } catch (_: Throwable) {
        }
        try {
            process?.destroy()
        } catch (_: Throwable) {
        }
        process = null
        reader = null
        writer = null
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"
}

class McpException(message: String) : Exception(message)

/**
 * A minimal MCP client speaking JSON-RPC 2.0 to a [KsudMcpProcess].
 * Supports initialize / tools/list / tools/call.
 */
class KsudMcpClient(
    private val process: KsudMcpProcess,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val idSeq = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    @Volatile
    private var connected = false

    suspend fun connect() {
        if (connected && process.isRunning) return
        val started = withContext(Dispatchers.IO) { process.start() }
        if (!started) {
            throw McpException("failed to start ksud mcp (is the device rooted?)")
        }
        startReader()
        request(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-06-18")
                .put("capabilities", JSONObject())
                .put(
                    "clientInfo",
                    JSONObject().put("name", "ZakoDaNiuMask").put("version", "1.0"),
                ),
        )
        notify("notifications/initialized", JSONObject())
        connected = true
    }

    suspend fun listTools(): List<AgentTool> {
        connect()
        val result = request("tools/list", JSONObject())
        return parseToolsResult("ksud", result)
    }

    suspend fun callTool(name: String, arguments: JSONObject): AgentToolResult {
        connect()
        val result = request(
            "tools/call",
            JSONObject().put("name", name).put("arguments", arguments),
        )
        return parseToolResult(result)
    }

    fun close() {
        connected = false
        scope.cancel()
        process.stop()
    }

    private fun startReader() {
        val input = process.reader ?: throw McpException("no reader")
        scope.launch {
            try {
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isBlank()) continue
                    val json = try {
                        JSONObject(line)
                    } catch (_: Throwable) {
                        continue
                    }
                    val id = json.optInt("id", Int.MIN_VALUE)
                    val deferred = pending.remove(id) ?: continue
                    if (json.has("error")) {
                        val error = json.optJSONObject("error")
                        deferred.completeExceptionally(
                            McpException(error?.optString("message") ?: "rpc error")
                        )
                    } else {
                        deferred.complete(json.optJSONObject("result") ?: JSONObject())
                    }
                }
            } finally {
                // Fail any in-flight requests when the pipe closes.
                pending.values.forEach {
                    it.completeExceptionally(McpException("ksud mcp connection closed"))
                }
                pending.clear()
            }
        }
    }

    private suspend fun request(method: String, params: JSONObject): JSONObject {
        val writer = process.writer ?: throw McpException("not connected")
        val id = idSeq.incrementAndGet()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        synchronized(writer) {
            writer.write(payload.toString())
            writer.write("\n")
            writer.flush()
        }
        return withTimeout(30_000) { deferred.await() }
    }

    private fun notify(method: String, params: JSONObject) {
        val writer = process.writer ?: return
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", params)
        synchronized(writer) {
            writer.write(payload.toString())
            writer.write("\n")
            writer.flush()
        }
    }
}
