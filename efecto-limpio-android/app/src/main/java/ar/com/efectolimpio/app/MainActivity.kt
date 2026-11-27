package ar.com.efectolimpio.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 7101
    private lateinit var status: LinearLayout
    private lateinit var message: TextView
    private lateinit var retry: Button
    private val appUrl = "https://daniramirez0979-rgb.github.io/mi-iptv-efecto/efecto-limpio/?appver=20261127-share2"

    private inner class ShareBridge {
        @JavascriptInterface
        fun sharePdf(base64: String, filename: String): Boolean {
            return try {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "-").let { if (it.endsWith(".pdf")) it else "$it.pdf" }
                val file = File(dir, safeName)
                file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "ar.com.efectolimpio.app.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newUri(contentResolver, "Catálogo Efecto Limpio", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Compartir catálogo por WhatsApp")) }
                true
            } catch (e: Exception) { false }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(ShareBridge(), "EfectoNative")
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.loadsImagesAutomatically = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return try { startActivityForResult(fileChooserParams.createIntent(), fileChooserRequestCode); true }
                catch (e: Exception) { this@MainActivity.filePathCallback?.onReceiveValue(null); this@MainActivity.filePathCallback = null; Toast.makeText(this@MainActivity, "No se pudo abrir la galería.", Toast.LENGTH_LONG).show(); false }
            }
        }
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
                if (uri.scheme == "https") { startActivity(Intent(Intent.ACTION_VIEW,uri)); return true }
                return true
            }
        }
        webView.clearHistory()
        webView.loadUrl(appUrl)
    }
    private fun showLoading() { status.visibility=View.VISIBLE; message.text="Abriendo el panel de administración…"; retry.visibility=View.GONE }
    private fun showFailure(text:String) { status.visibility=View.VISIBLE; message.text=text; retry.visibility=View.VISIBLE }
    @Deprecated("Deprecated in Android API; retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == fileChooserRequestCode) { val callback=filePathCallback; filePathCallback=null; callback?.onReceiveValue(if(resultCode==Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode,data) else null); return }
        super.onActivityResult(requestCode,resultCode,data)
    }
    @Deprecated("Deprecated in Android API; retained for compatibility")
    override fun onBackPressed() { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onSaveInstanceState(outState:Bundle) { if (::webView.isInitialized) webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { filePathCallback?.onReceiveValue(null); filePathCallback=null; if (::webView.isInitialized) webView.destroy(); super.onDestroy() }
}
