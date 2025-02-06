package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
  private lateinit var webView: WebView

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webView)
    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webView.settings.javaScriptEnabled = true
    webView.webViewClient = WebViewClient()
    webView.addJavascriptInterface(JavaScriptInterface(this, webView), "AndroidInterface")
    webView.loadUrl("https://gekal-study-android.github.io/webview-interaction-sample/index.html")
  }
}
