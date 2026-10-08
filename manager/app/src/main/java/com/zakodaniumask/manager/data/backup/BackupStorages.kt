// Ported from FolkSU (GPL-3.0); modified for ZakoDaNiuMask.
// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.backup

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Stores backup artifacts under an app-private directory. */
class LocalBackupStorage(private val root: File) : BackupStorage {
    override suspend fun test(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            root.mkdirs()
            check(root.exists() && root.canWrite()) { "backup directory is not writable" }
        }
    }

    override suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(root, relativePath)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
        }

    override suspend fun get(relativePath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) { runCatching { File(root, relativePath).readBytes() } }

    override suspend fun list(prefix: String): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(root, prefix)
                dir.listFiles()
                    ?.filter { it.isFile }
                    ?.map {
                        RemoteEntry(
                            relativePath = it.relativeTo(root).path,
                            sizeBytes = it.length(),
                            lastModifiedEpochMs = it.lastModified(),
                        )
                    }
                    ?: emptyList()
            }
        }

    override suspend fun delete(relativePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                File(root, relativePath).delete()
                Unit
            }
        }

    override fun toString(): String = "local"
}

/** Stores backup artifacts on a WebDAV server (basic auth). */
class WebDavBackupStorage(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) : BackupStorage {

    private fun urlFor(path: String): String = WebDavPaths.joinBase(baseUrl, path)

    private fun Request.Builder.basicAuth(): Request.Builder = apply {
        if (username.isNotEmpty()) header("Authorization", Credentials.basic(username, password))
    }

    override suspend fun test(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(urlFor(""))
                .method("PROPFIND", ByteArray(0).toRequestBody())
                .basicAuth()
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
            }
        }
    }

    override suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dirs = WebDavPaths.parentDirs(relativePath).dropLast(1)
                for (dir in dirs) {
                    val mkcol = Request.Builder()
                        .url(urlFor(dir))
                        .method("MKCOL", ByteArray(0).toRequestBody())
                        .basicAuth()
                        .build()
                    client.newCall(mkcol).execute().use { /* 201 created, 405 exists */ }
                }
                val request = Request.Builder()
                    .url(urlFor(relativePath))
                    .put(bytes.toRequestBody(OCTET_STREAM))
                    .basicAuth()
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                }
            }
        }

    override suspend fun get(relativePath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(urlFor(relativePath))
                    .get()
                    .basicAuth()
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    response.body?.bytes() ?: error("empty response body")
                }
            }
        }

    override suspend fun list(prefix: String): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dirUrl = urlFor(prefix.trimEnd('/') + "/")
                val request = Request.Builder()
                    .url(dirUrl)
                    .method("PROPFIND", ByteArray(0).toRequestBody())
                    .basicAuth()
                    .header("Depth", "1")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body?.string().orEmpty()
                    val hrefs = HREF_REGEX.findAll(body).map { it.groupValues[1] }.toList()
                    val base = prefix.trimEnd('/')
                    hrefs.mapNotNull { href ->
                        val decoded = runCatching {
                            java.net.URLDecoder.decode(href, "UTF-8")
                        }.getOrDefault(href)
                        val name = decoded.trimEnd('/').substringAfterLast('/')
                        if (name.isBlank() || decoded.endsWith("/")) return@mapNotNull null
                        RemoteEntry(
                            relativePath = "$base/$name",
                            sizeBytes = 0L,
                        )
                    }.distinctBy { it.relativePath }
                }
            }
        }

    override suspend fun delete(relativePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(urlFor(relativePath))
                    .delete()
                    .basicAuth()
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                }
            }
        }

    override fun toString(): String = "webdav"

    private companion object {
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val HREF_REGEX = Regex("<[^>]*href>([^<]+)</[^>]*href>")
    }
}
