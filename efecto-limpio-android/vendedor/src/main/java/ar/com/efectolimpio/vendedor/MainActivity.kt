package ar.com.efectolimpio.vendedor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class MainActivity : Activity() {
    private inner class DeviceBridge {
        @JavascriptInterface
        fun installationId(): String {
            val prefs = getSharedPreferences("efecto_vendor_device", MODE_PRIVATE)
            var id = prefs.getString("installation_id", null)
            if (id.isNullOrBlank()) { id = UUID.randomUUID().toString(); prefs.edit().putString("installation_id", id).apply() }
            return id
        }
        @JavascriptInterface
        fun deviceLabel(): String = "Android · " + Build.MODEL.take(70)
        @JavascriptInterface
        fun sharePdf(base64: String, filename: String): Boolean {
            return try {
                val dir=File(cacheDir,"shared").apply { mkdirs() }
                val safeName=filename.replace(Regex("[^A-Za-z0-9._-]"),"-").let { if(it.endsWith(".pdf")) it else "$it.pdf" }
                val file=File(dir,safeName); file.writeBytes(Base64.decode(base64,Base64.DEFAULT))
                val uri=FileProvider.getUriForFile(this@MainActivity,"ar.com.efectolimpio.vendedor.fileprovider",file)
                val send=Intent(Intent.ACTION_SEND).apply { type="application/pdf"; putExtra(Intent.EXTRA_STREAM,uri); clipData=android.content.ClipData.newUri(contentResolver,"Catálogo Efecto Limpio",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                runOnUiThread { startActivity(Intent.createChooser(send,"Compartir catálogo por WhatsApp")) }
                true
            } catch(e:Exception) { false }
        }
    }
    private lateinit var webView: WebView
    private val appUrl = "https://daniramirez0979-rgb.github.io/mi-iptv-efecto/efecto-limpio/vendedores/?appver=20261127-share1"
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView=WebView(this); webView.settings.javaScriptEnabled=true; webView.addJavascriptInterface(DeviceBridge(),"EfectoDevice")
        webView.settings.domStorageEnabled=true; webView.settings.databaseEnabled=true; webView.settings.cacheMode=android.webkit.WebSettings.LOAD_NO_CACHE; webView.clearCache(true)
        webView.settings.allowFileAccess=false; webView.settings.allowContentAccess=false; webView.settings.javaScriptCanOpenWindowsAutomatically=false; webView.webChromeClient=WebChromeClient()
        webView.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                val uri=request.url;val host=uri.host?:return true
                if(host=="daniramirez0979-rgb.github.io"||host=="edypzlttwcdrvwwvcouj.supabase.co"||host=="cdn.jsdelivr.net")return false
                if(uri.scheme=="https"){startActivity(Intent(Intent.ACTION_VIEW,uri));return true};return true
            }
        }
        setContentView(webView);if(savedInstanceState==null)webView.loadUrl(appUrl)else webView.restoreState(savedInstanceState)
    }
    @Deprecated("Deprecated in Android API; retained for compatibility")
    override fun onBackPressed(){if(::webView.isInitialized&&webView.canGoBack())webView.goBack()else super.onBackPressed()}
    override fun onSaveInstanceState(outState:Bundle){if(::webView.isInitialized)webView.saveState(outState);super.onSaveInstanceState(outState)}
    override fun onDestroy(){if(::webView.isInitialized)webView.destroy();super.onDestroy()}
}
