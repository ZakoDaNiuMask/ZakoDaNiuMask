// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.data.agent.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.zakodaniumask.manager.data.packageinfo.InstalledPackageRepository
import com.zakodaniumask.manager.data.webui.WebUiRepository
import com.zakodaniumask.manager.ui.webui.SuFilePathHandler
import com.zakodaniumask.manager.ui.webui.WebUIState
import com.zakodaniumask.manager.ui.webui.WebViewInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class BrowserActionResult(
    val text: String,
    val success: Boolean = true,
    /** Base64-encoded PNG for the screenshot action. */
    val imageBase64: String? = null,
) {
    companion object {
        fun error(message: String) = BrowserActionResult("Error: $message", success = false)
    }
}

/**
 * A single off-screen WebView that the agent can drive. It powers two modes:
 *  - general: loads any http(s) URL and exposes DOM/JS actions;
 *  - module WebUI: serves /data/adb/modules/<id>/webroot via the same asset
 *    handler and `ksu` bridge as the in-app WebUI, so the module's scripts work.
 *
 * All WebView access happens on the main thread. Screenshots use an off-screen
 * `WebView.draw` capture (GPU/HardwareRenderer capture is a later addition).
 */
class HeadlessBrowser(
    private val context: Context,
    private val webUiRepository: WebUiRepository,
    private val packageRepository: InstalledPackageRepository,
) {
    private var webView: WebView? = null
    private var state: WebUIState? = null
    private var moduleId: String? = null
    private var pageDone: CompletableDeferred<Unit>? = null

    @Volatile
    private var lastError: String? = null

    suspend fun control(
        action: String,
        args: JSONObject,
        forModuleId: String? = null,
    ): BrowserActionResult =
        withContext(Dispatchers.Main) {
            try {
                configure(forModuleId)
                if (forModuleId != null && action != "navigate" && currentUrl().isBlank()) {
                    loadAndWait("https://$MODULE_ORIGIN/index.html")
                }
                when (action) {
                    "navigate" -> navigateInCurrentMode(args.optString("url"))
                    "get_text" -> textResult()
                    "get_page_info" -> pageInfoResult()
                    "get_readable" -> textResult()
                    "execute_js" -> evalResult(args.optString("script"))
                    "find_elements" -> findElementsResult(
                        args.optString("selector", "a,button,input,[role=button]")
                    )
                    "click" -> clickResult(args.optString("selector"))
                    "type" -> typeResult(args.optString("selector"), args.optString("text"))
                    "scroll" -> scrollResult(
                        args.optString("direction", "down"),
                        args.optInt("amount", 600),
                    )
                    "screenshot" -> screenshotResult()
                    "back" -> goBackResult()
                    "forward" -> goForwardResult()
                    "set_user_agent" -> setUserAgentResult(args.optString("user_agent"))
                    else -> BrowserActionResult.error("unsupported action '$action'")
                }
            } catch (t: Throwable) {
                BrowserActionResult.error(t.message ?: t.javaClass.simpleName)
            }
        }

    /** Loads a general http(s) page (or a module webroot page when [forModuleId] is set). */
    suspend fun open(url: String, forModuleId: String?): BrowserActionResult =
        withContext(Dispatchers.Main) {
            configure(forModuleId)
            loadAndWait(url)
            BrowserActionResult("loaded ${currentUrl()}")
        }

    fun close() {
        val wv = webView
        webView = null
        state = null
        moduleId = null
        if (wv != null) {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    // ---- mode setup ----

    private fun configure(forModuleId: String?) {
        if (webView == null || moduleId != forModuleId) {
            createWebView(forModuleId)
        }
    }

    private fun createWebView(forModuleId: String?) {
        webView?.destroy()
        val uiState = WebUIState()
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_AUTO
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)

        val assetLoader = if (forModuleId != null) {
            uiState.modDir = "/data/adb/modules/$forModuleId"
            wv.addJavascriptInterface(
                WebViewInterface(uiState, packageRepository, webUiRepository),
                "ksu",
            )
            val webRoot = File("${uiState.modDir}/webroot")
            runCatching {
                WebViewAssetLoader.Builder()
                    .setDomain(MODULE_ORIGIN)
                    .addPathHandler(
                        "/",
                        SuFilePathHandler(
                            webRoot,
                            webUiRepository,
                            { uiState.currentInsets },
                            { enable -> uiState.isInsetsEnabled = enable },
                            { "" },
                        ),
                    )
                    .build()
            }.getOrNull()
        } else {
            null
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val loader = assetLoader ?: return null
                return request?.let { loader.shouldInterceptRequest(it.url) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (pageDone == null) pageDone = CompletableDeferred()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageDone?.complete(Unit)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    lastError = error?.description?.toString()
                    pageDone?.complete(Unit)
                }
            }
        }

        uiState.webView = wv
        state = uiState
        webView = wv
        moduleId = forModuleId
    }

    private suspend fun navigateInCurrentMode(url: String): BrowserActionResult {
        if (url.isBlank()) return BrowserActionResult.error("missing 'url'")
        val target = if (moduleId != null && !url.startsWith("http")) {
            "https://$MODULE_ORIGIN/" + url.trimStart('/')
        } else {
            url
        }
        loadAndWait(target)
        return BrowserActionResult("loaded ${currentUrl()}")
    }

    private suspend fun loadAndWait(url: String) {
        val wv = ensure() ?: throw IllegalStateException("webview unavailable")
        lastError = null
        val done = CompletableDeferred<Unit>()
        pageDone = done
        wv.loadUrl(url)
        withTimeoutOrNull(NAV_TIMEOUT_MS) { done.await() }
        lastError?.let { throw IllegalStateException(it) }
    }

    private fun ensure(): WebView? = webView

    private fun currentUrl(): String {
        val wv = ensure() ?: return ""
        return wv.url ?: ""
    }

    // ---- actions ----

    private suspend fun textResult(): BrowserActionResult {
        val text = evalString("(document.body ? document.body.innerText : '')")
        return BrowserActionResult(text.take(MAX_TEXT))
    }

    private suspend fun pageInfoResult(): BrowserActionResult {
        val json = evalJson(
            "JSON.stringify({url: location.href, title: document.title})"
        )
        return BrowserActionResult(json.toString(2))
    }

    private suspend fun evalResult(script: String): BrowserActionResult {
        if (script.isBlank()) return BrowserActionResult.error("missing 'script'")
        val value = evalRaw("(function(){ try { return (${script}); } catch(e) { return 'Error: '+e; } })()")
        return BrowserActionResult(value.take(MAX_TEXT))
    }

    private suspend fun findElementsResult(selector: String): BrowserActionResult {
        val script = """
            (function(){
              try {
                var els = Array.from(document.querySelectorAll(${jsString(selector)})).slice(0, 40);
                return JSON.stringify(els.map(function(e, i){
                  return {index:i, tag:e.tagName.toLowerCase(),
                    text:(e.innerText||e.value||e.getAttribute('aria-label')||'').trim().slice(0,120)};
                }));
              } catch(e){ return JSON.stringify({error:String(e)}); }
            })()
        """.trimIndent()
        return BrowserActionResult(evalRaw(script))
    }

    private suspend fun clickResult(selector: String): BrowserActionResult {
        if (selector.isBlank()) return BrowserActionResult.error("missing 'selector'")
        val script = """
            (function(){
              try {
                var el = document.querySelector(${jsString(selector)});
                if(!el) return 'Error: element not found';
                el.scrollIntoView({block:'center'});
                el.click();
                return 'clicked';
              } catch(e){ return 'Error: '+e; }
            })()
        """.trimIndent()
        return BrowserActionResult(evalRaw(script))
    }

    private suspend fun typeResult(selector: String, text: String): BrowserActionResult {
        if (selector.isBlank()) return BrowserActionResult.error("missing 'selector'")
        val script = """
            (function(){
              try {
                var el = document.querySelector(${jsString(selector)});
                if(!el) return 'Error: element not found';
                el.focus();
                if('value' in el){ el.value = ${jsString(text)}; }
                else { el.textContent = ${jsString(text)}; }
                el.dispatchEvent(new Event('input', {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
                return 'typed';
              } catch(e){ return 'Error: '+e; }
            })()
        """.trimIndent()
        return BrowserActionResult(evalRaw(script))
    }

    private suspend fun scrollResult(direction: String, amount: Int): BrowserActionResult {
        val dy = if (direction.equals("up", true)) -amount else amount
        evalRaw("window.scrollBy(0, $dy); 'ok'")
        return BrowserActionResult("scrolled $direction $amount")
    }

    private suspend fun goBackResult(): BrowserActionResult {
        val wv = ensure() ?: return BrowserActionResult.error("webview unavailable")
        if (wv.canGoBack()) {
            wv.goBack()
        } else {
            return BrowserActionResult("no history")
        }
        return BrowserActionResult("went back")
    }

    private suspend fun goForwardResult(): BrowserActionResult {
        val wv = ensure() ?: return BrowserActionResult.error("webview unavailable")
        if (wv.canGoForward()) wv.goForward() else return BrowserActionResult("no forward history")
        return BrowserActionResult("went forward")
    }

    private suspend fun setUserAgentResult(ua: String): BrowserActionResult {
        val wv = ensure() ?: return BrowserActionResult.error("webview unavailable")
        wv.settings.userAgentString = ua.ifBlank { null }
        return BrowserActionResult("user agent set")
    }

    private suspend fun screenshotResult(): BrowserActionResult {
        val wv = ensure() ?: return BrowserActionResult.error("webview unavailable")
        val width = if (wv.width > 0) wv.width else DEFAULT_WIDTH
        val height = if (wv.height > 0) wv.height else DEFAULT_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wv.draw(canvas)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        bitmap.recycle()
        return BrowserActionResult("screenshot ${width}x$height", imageBase64 = base64)
    }

    // ---- JS helpers ----

    private suspend fun evalRaw(script: String): String {
        val wv = ensure() ?: throw IllegalStateException("webview unavailable")
        return suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(decodeJsString(value))
            }
        }
    }

    private suspend fun evalString(script: String): String = evalRaw(script)

    private suspend fun evalJson(script: String): JSONObject {
        val raw = evalRaw(script)
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    /** evaluateJavascript JSON-encodes strings; decode them back. */
    private fun decodeJsString(value: String?): String {
        if (value == null || value == "null") return ""
        val trimmed = value.trim()
        return runCatching {
            val decoded = org.json.JSONTokener(trimmed).nextValue()
            when (decoded) {
                is String -> decoded
                else -> decoded.toString()
            }
        }.getOrDefault(trimmed)
    }

    private fun jsString(value: String): String = JSONObject.quote(value)

    private companion object {
        const val NAV_TIMEOUT_MS = 30_000L
        const val MAX_TEXT = 60_000
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 1920
        const val MODULE_ORIGIN = "mui.kernelsu.org"
    }
}
