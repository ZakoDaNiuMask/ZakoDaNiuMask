// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck ToolBox (MIT), ui/src/features/tricky-store/pages/RepoPage.vue; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.ui.screen.keybox

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.ui.component.settings.AppBackButton
import com.zakodaniumask.manager.ui.navigation.LocalNavigator
import com.zakodaniumask.manager.ui.theme.CardConfig
import com.zakodaniumask.manager.ui.theme.ThemeConfig
import com.zakodaniumask.manager.ui.theme.blurEffect
import com.zakodaniumask.manager.ui.util.adaptiveScaffoldWindowInsets
import com.zakodaniumask.manager.ui.viewmodel.KeyboxWorkbenchViewModel
import org.json.JSONObject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

private const val REPO_ORIGIN = "https://keybox.kowx712.cc"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyboxRepoScreen(viewModel: KeyboxWorkbenchViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    var loading by remember { mutableStateOf(true) }

    BackHandler { navigator.pop() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val locale = remember { repoLocale() }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.keybox_wb_repo)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                    scrolledContainerColor = if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
                ),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                        }
                        addJavascriptInterface(RepoBridge(viewModel), "Android")
                        loadDataWithBaseURL(REPO_ORIGIN, wrapperHtml(locale), "text/html", "utf-8", null)
                    }
                },
            )
            if (loading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private class RepoBridge(private val viewModel: KeyboxWorkbenchViewModel) {
    @JavascriptInterface
    fun post(json: String) {
        val message = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (message.optString("type") == "download") {
            val url = message.optString("url").takeIf { it.isNotBlank() } ?: return
            viewModel.installFromUrl(url, "")
        }
    }
}

private fun repoLocale(): String = when (Locale.getDefault().language) {
    "zh" -> if (Locale.getDefault().country == "TW" || Locale.getDefault().country == "HK") "zh-TW" else "zh-CN"
    "ja" -> "ja"
    "ko" -> "ko"
    "ru" -> "ru"
    else -> "en"
}

@SuppressLint("SetJavaScriptEnabled")
private fun wrapperHtml(locale: String): String = """
    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
    <body style="margin:0">
    <iframe id="frame" src="$REPO_ORIGIN/$locale" style="border:0;width:100vw;height:100vh"></iframe>
    <script>
      const ORIGIN = "$REPO_ORIGIN";
      const frame = document.getElementById('frame');
      setInterval(function () {
        try { frame.contentWindow.postMessage({ type: 'handshake' }, ORIGIN); } catch (e) {}
      }, 500);
      window.addEventListener('message', function (event) {
        if (event.origin !== ORIGIN) return;
        if (event.source !== frame.contentWindow) return;
        try { Android.post(JSON.stringify(event.data)); } catch (e) {}
      });
    </script>
    </body></html>
""".trimIndent()
