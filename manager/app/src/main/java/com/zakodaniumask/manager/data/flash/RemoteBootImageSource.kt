package com.zakodaniumask.manager.data.flash

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.zakodaniumask.manager.core.tasks.ExtractImage
import com.zakodaniumask.manager.core.tasks.ProbeResult
import com.zakodaniumask.manager.core.utils.DataSourceChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Portions Copyright (C) Anatdx (YukiSU)
 * Source: https://github.com/Rouyashiki/YukiSU
 * Ported into ZakoDaNiuMask; see LICENSE.
 *
 * Downloads a boot image (raw payload.bin, OTA package or factory image) directly
 * from an HTTPS url and extracts the requested boot partition to the app cache.
 */
class RemoteBootImageSource(
    private val application: Application,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        validateDownloadUrl(url)
        DataSourceChannel(client, url).use { channel ->
            when (readDownloadMagic(channel)) {
                "CrAU" -> ExtractImage.probePayload(channel, withKmi = true)
                else -> ExtractImage.probe(channel, withKmi = true)
            }
        }
    }

    suspend fun downloadPartition(
        url: String,
        partition: String,
        onConsole: (String) -> Unit = {},
    ): Uri = withContext(Dispatchers.IO) {
        validateDownloadUrl(url)
        val outFile = File(application.cacheDir, "download-boot.img")
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("Cannot clear the previous download")
        }
        val image = ExtractImage(outFile, onConsole)
        DataSourceChannel(client, url).use { channel ->
            when (readDownloadMagic(channel)) {
                "CrAU" -> image.consumePayload(channel, partition)
                else -> image.consume(channel, partition)
            }
        }
        FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            outFile,
        )
    }

    suspend fun downloadFile(
        url: String,
        fileName: String,
    ): File = withContext(Dispatchers.IO) {
        validateDownloadUrl(url)
        val outFile = File(application.cacheDir, fileName)
        if (outFile.exists() && !outFile.delete()) {
            throw IOException("Cannot clear the previous download")
        }
        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { input ->
                outFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
        outFile
    }

    fun contentUri(file: File): Uri = FileProvider.getUriForFile(
        application,
        "${application.packageName}.fileprovider",
        file,
    )

    private fun validateDownloadUrl(url: String) {
        val uri = url.toUri()
        check(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Only HTTPS download URLs are supported"
        }
    }

    private fun readDownloadMagic(channel: DataSourceChannel): String {
        val buffer = ByteBuffer.allocate(4)
        if (channel.read(buffer) != 4) {
            throw IOException("Downloaded file is shorter than its magic header")
        }
        channel.position(0)
        return String(buffer.array(), StandardCharsets.ISO_8859_1)
    }
}
