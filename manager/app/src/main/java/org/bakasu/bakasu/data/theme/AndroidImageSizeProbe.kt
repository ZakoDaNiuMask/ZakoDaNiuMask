package org.bakasu.bakasu.data.theme

import android.graphics.BitmapFactory

/**
 * 只读文件头取尺寸，不分配整张位图。解码失败或尺寸非正时返回 `null`，
 * 由 [ThemePackageArchive] 转成 `ThemePackageException.InvalidImage`。
 */
object AndroidImageSizeProbe : ImageSizeProbe {
    override fun probe(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.isEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }
}
