package ar.com.efectolimpio.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: LinearLayout
    private lateinit var message: TextView
    private lateinit var retry: Button
    private val appUrl = "https://daniramirez0979-rgb.github.io/mi-iptv-efecto/efecto-limpio/?appver=20261126-4"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.loadsImagesAutomatically = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)
        webView.webChromeClient = WebChromeClient()
        status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32,32,32,32); setBackgroundColor(Color.rgb(239,249,255)) }
        val brand = TextView(this).apply { text = "EFECTO LIMPIO"; textSize = 27f; setTextColor(Color.rgb(5,65,125)); typeface = Typeface.create("sans-serif",Typeface.BOLD_ITALIC); gravity = Gravity.CENTER }
        message = TextView(this).apply { text = "Abriendo el panel de administración…"; textSize = 16f; setTextColor(Color.rgb(30,75,100)); gravity = Gravity.CENTER; setPadding(0,18,0,18) }
        retry = Button(this).apply { text = "Volver a intentar"; visibility = View.GONE; setOnClickListener { showLoading(); webView.loadUrl(appUrl) } }
        status.addView(brand); status.addView(message); status.addView(ProgressBar(this).apply { isIndeterminate = true }); status.addView(retry)
        root.addView(webView,FrameLayout.LayoutParams(-1,-1)); root.addView(status,FrameLayout.LayoutParams(-1,-1)); setContentView(root)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { showLoading() }
            override fun onPageCommitVisible(view: WebView, url: String) { status.visibility = View.GONE }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) showFailure("No se pudo abrir el panel. Revisá tu conexión a internet y volvé a intentar.") }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) { if (request.isForMainFrame) showFailure("El servidor respondió con error (${response.statusCode}). Probá nuevamente en unos instantes.") }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri=request.url; val host=uri.host ?: return true
                if (host == "daniramirez0979-rgb.github.io" || host.endsWith(".supabase.co") || host == "cdn.jsdelivr.net") return false
                if (uri.scheme == "https") { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri)); return true }
                return true
            }
        }
        webView.clearHistory()
        webView.loadUrl(appUrl)
    }
    private fun showLoading() { status.visibility=View.VISIBLE; message.text="Abriendo el panel de administración…"; retry.visibility=View.GONE }
    private fun showFailure(text:String) { status.visibility=View.VISIBLE; message.text=text; retry.visibility=View.VISIBLE }
    @Deprecated("Deprecated in Android API; retained for compatibility")
    override fun onBackPressed() { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onSaveInstanceState(outState:Bundle) { if (::webView.isInitialized) webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { if (::webView.isInitialized) webView.destroy(); super.onDestroy() }
}
