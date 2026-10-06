/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Online plugin catalog, compatible with the FolkPatch catalog endpoint.
 */

package com.zakodaniumask.manager.data.plugin

import com.zakodaniumask.manager.data.network.NetworkRequestRepository
import com.zakodaniumask.manager.domain.model.OnlinePlugin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class OnlinePluginRepository(
    private val networkRequestRepository: NetworkRequestRepository,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(language: String): Result<List<OnlinePlugin>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$CATALOG_URL&lang=$language"
            val body = networkRequestRepository.fetch(url).getOrThrow()
            parse(body, language)
        }
    }

    suspend fun download(url: String, destPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destination = File(destPath)
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: error("Empty response")
                    destination.outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                }
                destination.absolutePath
            }
        }

    private fun parse(body: String, language: String): List<OnlinePlugin> {
        val array = JSONArray(body)
        val list = ArrayList<OnlinePlugin>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url")
            if (url.isBlank() || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue
            }
            val descZh = obj.optString("description")
            val descEn = obj.optString("description_en")
            val description = if (language == "zh") {
                descZh
            } else {
                descEn.ifEmpty { descZh }
            }
            list.add(
                OnlinePlugin(
                    name = obj.optString("name"),
                    version = obj.optString("version"),
                    url = url,
                    description = description,
                )
            )
        }
        return list
    }

    private companion object {
        const val CATALOG_URL = "https://folk.mysqil.com/api/modules?type=plugin"
    }
}
