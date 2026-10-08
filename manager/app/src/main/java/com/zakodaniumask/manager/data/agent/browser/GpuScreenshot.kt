// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.browser

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.webkit.WebView
import android.widget.FrameLayout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort GPU screenshot for an off-screen WebView.
 *
 * A detached WebView has no window surface, so `WebView.draw` re-rasterises on
 * the CPU and GPU-only layers (WebGL, video) come out blank. Here the WebView is
 * parented into a `Presentation` window on a private `VirtualDisplay` backed by
 * an `ImageReader`, so the normal hardware compositor draws one frame that we
 * read back. Any failure returns null and the caller falls back to `draw`.
 *
 * The path self-disables for the process after repeated host failures.
 */
internal object GpuScreenshot {
    private const val MAX_EDGE_PX = 4096
    private const val FRAME_TIMEOUT_MS = 1_500L
    private const val MAX_HOST_FAILURES = 2

    @Volatile
    private var disabled: String? = null

    @Volatile
    private var hostFailures = 0

    suspend fun capture(context: Context, webView: WebView, width: Int, height: Int): Bitmap? {
        if (disabled != null) return null
        if (width <= 0 || height <= 0 || width > MAX_EDGE_PX || height > MAX_EDGE_PX) return null
        if (webView.parent != null || webView.isAttachedToWindow) return null
        val bitmap = runCatching { captureInternal(context, webView, width, height) }.getOrNull()
        if (bitmap == null) {
            hostFailures += 1
            if (hostFailures >= MAX_HOST_FAILURES) disabled = "gpu capture unavailable"
        } else {
            hostFailures = 0
        }
        return bitmap
    }

    private suspend fun captureInternal(
        context: Context,
        webView: WebView,
        width: Int,
        height: Int,
    ): Bitmap? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return null
        val density = context.resources.displayMetrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val latch = CountDownLatch(1)
        val thread = HandlerThread("agent-gpu-shot").apply { start() }
        val handler = Handler(thread.looper)
        reader.setOnImageAvailableListener({ latch.countDown() }, handler)
        val display = displayManager.createVirtualDisplay(
            "agent-gpu-shot",
            width,
            height,
            density,
            reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        )
        var presentation: Presentation? = null
        val container = FrameLayout(context).apply { setBackgroundColor(Color.WHITE) }
        try {
            withContext(Dispatchers.Main) {
                val created = Presentation(context, display)
                created.setContentView(container)
                created.show()
                presentation = created
                container.addView(webView, FrameLayout.LayoutParams(width, height))
                webView.invalidate()
            }
            val got = withContext(Dispatchers.Default) {
                latch.await(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            if (!got) return null
            val image = withContext(Dispatchers.Default) { reader.acquireLatestImage() } ?: return null
            return try {
                imageToBitmap(image, width, height)
            } finally {
                image.close()
            }
        } finally {
            withContext(Dispatchers.Main) {
                runCatching { container.removeView(webView) }
                runCatching { presentation?.dismiss() }
            }
            runCatching { display.release() }
            runCatching { reader.close() }
            runCatching { thread.quitSafely() }
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }
}
