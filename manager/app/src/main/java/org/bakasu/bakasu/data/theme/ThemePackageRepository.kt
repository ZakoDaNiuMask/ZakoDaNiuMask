package org.bakasu.bakasu.data.theme

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主题包的门面：SAF 读取、解压校验、列表与删除全部跑在 [Dispatchers.IO]，
 * 调用方（Compose）只负责状态与提示。
 */
class ThemePackageRepository(
    context: Context,
    private val storage: ThemePackageStorage,
) {
    private val appContext = context.applicationContext

    suspend fun importPackage(uri: Uri, id: String = newId()): StoredTheme = withContext(Dispatchers.IO) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        input.use { storage.importPackage(it, id, AndroidImageSizeProbe) }
    }

    suspend fun list(): List<StoredTheme> = withContext(Dispatchers.IO) { storage.list() }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) { storage.delete(id) }

    fun newId(): String = "theme_${System.currentTimeMillis().toString(36)}"
}
