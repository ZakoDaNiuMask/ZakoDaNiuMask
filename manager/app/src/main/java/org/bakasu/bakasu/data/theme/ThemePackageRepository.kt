package org.bakasu.bakasu.data.theme

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主题包的门面：SAF 读取、`.sukisu` 只读兼容、解压校验、列表与删除全部跑在 [Dispatchers.IO]，
 * 调用方（Compose）只负责状态与提示。
 */
class ThemePackageRepository(
    context: Context,
    private val storage: ThemePackageStorage,
) {
    private val appContext = context.applicationContext

    suspend fun importPackage(uri: Uri, id: String = newId()): StoredTheme = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)
            ?.use { input -> readBounded(input, ThemePackageFormat.MAX_TOTAL_BYTES * 2) }
            ?: throw IOException("无法读取所选文件")
        val plain = SukisuThemePackage.normalizeToPlainZip(bytes)
        storage.importPackage(ByteArrayInputStream(plain), id, AndroidImageSizeProbe)
    }

    suspend fun list(): List<StoredTheme> = withContext(Dispatchers.IO) { storage.list() }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) { storage.delete(id) }

    fun newId(): String = "theme_${System.currentTimeMillis().toString(36)}"

    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw ThemePackageException.TooLarge("文件超过 ${limit / 1024 / 1024} MB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
