package cn.gekal.android.myapplicationwebviewinteractionsample

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(JavaScriptInterface(this), "AndroidInterface")
        webView.loadUrl("https://gekal-study-android.github.io/webview-interaction-sample/index.html")

        // WebView内でJavaScriptの関数を呼び出す
        webView.evaluateJavascript("javascript: handleReturnValue('Hello from Android!')", null)
    }
}
