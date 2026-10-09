// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), crates/duck-tricky-store/src/keybox; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.trickystore

import android.content.Context
import com.topjohnwu.superuser.Shell
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

data class KeyboxSummary(
    val keyboxes: Int,
    val hasEcdsa: Boolean,
    val hasRsa: Boolean,
    val chainLengths: List<Int>,
)

data class KeyboxInstall(
    val backend: Backend,
    val targetPath: String,
    val backupPath: String?,
    val size: Int,
    val source: String,
    val summary: KeyboxSummary,
)

class KeyboxRepository(
    private val context: Context,
    private val ksuCliRepository: KsuCliRepository,
    private val trickyStoreRepository: TrickyStoreRepository,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun install(xml: String, source: String): Result<KeyboxInstall> = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val cleaned = clean(xml)
            val summary = validate(cleaned)
            val backend = activeBackend(shell)
            val target = Backends.forBackend(backend).keyboxPath(shell)
            val backup = if (SuFileUtils.isFile(shell, target)) "$target.bak" else null
            if (backup != null) SuFileUtils.copy(shell, target, backup)
            if (!SuFileUtils.writeText(shell, target, cleaned)) error("failed to write keybox")
            if (backup == null) SuFileUtils.chmod644(shell, target)
            KeyboxInstall(backend, target, backup, cleaned.toByteArray().size, source, summary)
        }
    }

    suspend fun generate(): Result<KeyboxInstall> = withContext(Dispatchers.IO) {
        runCatching { KeyboxGenerator.unknownKeybox() }.mapCatching { xml ->
            install(xml, "generated").getOrThrow()
        }
    }

    suspend fun installAosp(): Result<KeyboxInstall> = withContext(Dispatchers.IO) {
        runCatching {
            val content = context.resources.openRawResource(R.raw.aosp_keybox)
                .use { it.readBytes().toString(Charsets.UTF_8) }
            install(content, "aosp").getOrThrow()
        }
    }

    suspend fun installLocal(content: String): Result<KeyboxInstall> = install(content, "local")

    suspend fun fetch(url: String, decode: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get()
                .header("User-Agent", "ZakoDaNiuMask")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: error("empty response")
                check(bytes.size <= MAX_KEYBOX_BYTES) { "keybox is larger than 1 MiB" }
                applyDecode(bytes, decode).toString(Charsets.UTF_8)
            }
        }
    }

    suspend fun installFromUrl(url: String, decode: String): Result<KeyboxInstall> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = fetch(url, decode).getOrThrow()
                install(content, "url").getOrThrow()
            }
        }

    fun providers(): List<KeyboxProvider> = trickyStoreRepository.providers()

    fun saveProviders(providers: List<KeyboxProvider>): Boolean {
        val validated = validateProviders(providers)
        return if (validated == null) false else {
            trickyStoreRepository.setProviders(validated)
            true
        }
    }

    fun resetProviders(): Boolean {
        trickyStoreRepository.clearProviders()
        return true
    }

    fun exportProviders(): String {
        val array = JSONArray()
        providers().forEach { provider ->
            array.put(
                JSONObject()
                    .put("name", provider.name)
                    .put("link", provider.url)
                    .put("script", provider.decode)
            )
        }
        return JSONObject()
            .put("metadata", "tricky_addon_custom_keybox_config")
            .put("version", 1)
            .put("entries", array)
            .toString(2)
    }

    fun importProviders(content: String): Boolean = runCatching {
        val root = JSONObject(content)
        val array = root.optJSONArray("entries") ?: JSONArray(content)
        val entries = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
            KeyboxProvider(
                name = item?.optString("name").orEmpty(),
                url = item?.optString("link")?.takeIf { it.isNotBlank() } ?: item?.optString("url").orEmpty(),
                decode = item?.optString("script")?.takeIf { it.isNotBlank() } ?: item?.optString("decode").orEmpty(),
            )
        }
        saveProviders(providers().filterNot { existing -> entries.any { it.name == existing.name } } + entries)
    }.getOrDefault(false)

    private fun activeBackend(shell: Shell): Backend =
        trickyStoreRepository.detectActive(shell)?.backend ?: Backend.TRICKY_STORE

    private fun validateProviders(providers: List<KeyboxProvider>): List<KeyboxProvider>? {
        val seen = linkedMapOf<String, KeyboxProvider>()
        for (provider in providers) {
            val name = provider.name.trim()
            val url = provider.url.trim()
            if (name.isEmpty()) return null
            if (!url.startsWith("https://") && !url.startsWith("http://")) return null
            if (!decodeSupported(provider.decode)) return null
            seen[name] = KeyboxProvider(name, url, provider.decode.trim())
        }
        return seen.values.toList()
    }

    /** The keybox XML is validated structurally only, with no key-type restriction. */
    fun validate(xml: String): KeyboxSummary {
        check(xml.contains("<AndroidAttestation")) { "not an AndroidAttestation keybox" }
        val keys = Regex("<Key\\s").findAll(xml).count()
        check(keys > 0) { "keybox has no <Key>" }
        check(xml.contains("<PrivateKey")) { "keybox has no <PrivateKey>" }
        check(xml.contains("<Certificate")) { "keybox has no <Certificate>" }
        val chainLengths = Regex("<CertificateChain>(.*?)</CertificateChain>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { match -> Regex("<Certificate\\s").findAll(match.groupValues[1]).count() }
            .toList()
        check(chainLengths.all { it > 0 }) { "keybox has an empty certificate chain" }
        return KeyboxSummary(
            keyboxes = Regex("<Keybox\\s").findAll(xml).count().coerceAtLeast(1),
            hasEcdsa = xml.contains("algorithm=\"ecdsa\""),
            hasRsa = xml.contains("algorithm=\"rsa\""),
            chainLengths = chainLengths,
        )
    }

    private fun clean(xml: String): String = xml
        .removePrefix("\uFEFF")
        .replace("\u200B", "")
        .replace("\u200C", "")
        .replace("\u200D", "")
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .trim()

    private fun decodeSupported(decode: String): Boolean =
        decode.split('|').all { step ->
            when (step.trim()) {
                "", "cat", "base64 -d", "base64 --decode", "xxd -r -p" -> true
                else -> false
            }
        }

    private fun applyDecode(input: ByteArray, decode: String): ByteArray {
        var current = input
        decode.split('|').map { it.trim() }.filter { it.isNotEmpty() }.forEach { step ->
            current = when (step) {
                "cat" -> current
                "base64 -d", "base64 --decode" -> Base64.getMimeDecoder().decode(current)
                "xxd -r -p" -> hexDecode(current)
                else -> error("unsupported decode step: $step")
            }
        }
        return current
    }

    private fun hexDecode(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.US_ASCII).filter { !it.isWhitespace() }
        check(text.length % 2 == 0) { "invalid hex" }
        return ByteArray(text.length / 2) { index ->
            text.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val MAX_KEYBOX_BYTES = 1024 * 1024
    }
}
