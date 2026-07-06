package com.xniperbuilds.downloader

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xniperbuilds.downloader.ui.theme.XniperDownloaderTheme

/**
 * In-app "YouTube login" — app ke ANDAR ek WebView me YouTube khulti hai.
 * User login kare (ya bas open kare), phir "Cookies save" dabaye → us WebView ki
 * cookies Netscape cookies.txt me save ho jati (Seal-style). Manual export ki zaroorat nahi.
 * NOTE: dusri app (Chrome/YouTube) ki cookies Android sandbox ki wajah se nahi mil sakti —
 * isi liye login yahin, is andar-wale browser me hota hai.
 */
class CookieLoginActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kis site ki cookies chahiye — MainActivity se intent me aata hai.
        val site = intent.getStringExtra("site") ?: "youtube"
        val startUrl = intent.getStringExtra("url") ?: cookieSiteUrl(site)
        val label = intent.getStringExtra("label") ?: "YouTube"

        // TikTok ka desktop login-modal PORTRAIT me blank rehta hai, LANDSCAPE me poora
        // dikhta hai (Nazim ne khud discover kiya) → TikTok login seedha landscape me kholo
        if (site == "tiktok") {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        setContent {
            XniperDownloaderTheme {
                val context = LocalContext.current
                val holder = remember { arrayOfNulls<WebView>(1) }
                var pageProgress by remember { mutableIntStateOf(0) }
                // Live domain — user KHUD dekh sake k asli site pe hai (anti-phishing proof)
                var currentHost by remember {
                    mutableStateOf(android.net.Uri.parse(startUrl).host ?: "")
                }

                BackHandler(enabled = true) {
                    val w = holder[0]
                    if (w != null && w.canGoBack()) w.goBack() else finish()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    Column(modifier = Modifier.padding(pad).fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connect $label · 🔒 $currentHost",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Log in → tap Connect. Password stays on the site.",
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { holder[0]?.reload() }) { Text("↻") }
                            Spacer(Modifier.width(6.dp))
                            Button(onClick = {
                                // Snapshot pehle — login-cookie na mili to file WAPAS (guest
                                // cookies se "connected ✓" dikhna WRONG tha, Nazim ka bug-report)
                                val before = try {
                                    if (hasCookies(context)) cookiesFile(context).readText() else null
                                } catch (e: Exception) { null }
                                val n = saveCookiesFromWebView(context, cookieGroupsFor(site, startUrl))
                                val known = site in listOf("youtube", "instagram", "tiktok", "facebook")
                                val reallyConnected = if (known) {
                                    connectedSites(context).contains(label)
                                } else n > 0
                                if (reallyConnected) {
                                    Toast.makeText(context, "✓ $label connected", Toast.LENGTH_SHORT).show()
                                    setResult(RESULT_OK)
                                    finish()
                                } else {
                                    // rollback — guest cookies save na rahen
                                    try {
                                        if (before == null) cookiesFile(context).delete()
                                        else cookiesFile(context).writeText(before)
                                    } catch (e: Exception) {
                                    }
                                    Toast.makeText(
                                        context,
                                        "Not logged in yet — sign in to $label first, then tap Connect",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) { Text("✓ Connect") }
                        }

                        // Page load ho raha hai to bar dikhao — "blank" aur "loading" ka farq nazar aaye
                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                    with(settings) {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        javaScriptCanOpenWindowsAutomatically = true
                                        // FB/Insta ke login buttons popup window kholte hain —
                                        // support na ho to click pe kuch nahi hota / blank
                                        setSupportMultipleWindows(true)
                                        // Desktop page phone pe — pinch zoom chahiye
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mediaPlaybackRequiresUserGesture = false
                                        // Login pages sab https hain — mixed content ki zaroorat nahi (secure)
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        // Insta/FB = DESKTOP Chrome UA (Seal ka nuskha): desktop
                                        // login form simple hai, mobile in-app-browser detection
                                        // aur "open in app" redirect (blank ki jarr) bypass.
                                        // Baqi sites = device ka asli WebView UA minus "; wv"
                                        // (YouTube isi pe chal raha hai — usay nahi chherna).
                                        userAgentString = if (useDesktopUa(site)) {
                                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                                                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                                        } else {
                                            try {
                                                WebSettings.getDefaultUserAgent(ctx).replace("; wv", "")
                                            } catch (e: Exception) {
                                                userAgentString
                                            }
                                        }
                                    }
                                    // redirects webview ke andar hi khulein (Insta/FB blank na ho)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView, request: android.webkit.WebResourceRequest
                                        ): Boolean = false

                                        override fun doUpdateVisitedHistory(
                                            view: WebView, url: String?, isReload: Boolean
                                        ) {
                                            url?.let {
                                                currentHost = android.net.Uri.parse(it).host ?: currentHost
                                            }
                                        }

                                        // FB/Insta heavy pages pe renderer mar jaye to poori APP
                                        // crash ho jati thi ("FB kholo to app band") — ab handle:
                                        override fun onRenderProcessGone(
                                            view: WebView, detail: android.webkit.RenderProcessGoneDetail
                                        ): Boolean {
                                            Toast.makeText(
                                                ctx,
                                                "Page crashed — opening the lighter mobile site…",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            holder[0] = null
                                            try {
                                                (view.parent as? android.view.ViewGroup)?.removeView(view)
                                                view.destroy()
                                            } catch (_: Exception) {
                                            }
                                            finish()
                                            return true
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: android.webkit.WebResourceRequest,
                                            error: android.webkit.WebResourceError
                                        ) {
                                            if (request.isForMainFrame) {
                                                Toast.makeText(
                                                    ctx,
                                                    "Couldn't load the page — check internet and try ↻",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            pageProgress = newProgress
                                        }

                                        // Login ka popup (window.open / target=_blank) → usi
                                        // WebView me kholo, warna click "mara hua" lagta hai
                                        override fun onCreateWindow(
                                            view: WebView,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: android.os.Message
                                        ): Boolean {
                                            val temp = WebView(view.context)
                                            temp.webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    v: WebView,
                                                    request: android.webkit.WebResourceRequest
                                                ): Boolean {
                                                    view.loadUrl(request.url.toString())
                                                    return true
                                                }
                                            }
                                            (resultMsg.obj as WebView.WebViewTransport).webView = temp
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                    }
                                    loadUrl(startUrl)
                                    holder[0] = this
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
