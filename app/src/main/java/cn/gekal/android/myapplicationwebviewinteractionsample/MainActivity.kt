package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {
  private lateinit var webView: WebView
  private lateinit var errorLayout: LinearLayout
  private lateinit var retryButton: Button

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val mainView = findViewById<View>(R.id.main)
    ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    WebView.setWebContentsDebuggingEnabled(true);

    webView = findViewById(R.id.webView)
    errorLayout = findViewById(R.id.errorLayout)
    retryButton = findViewById(R.id.retryButton)

    retryButton.setOnClickListener {
      errorLayout.visibility = View.GONE
      webView.visibility = View.VISIBLE
      webView.reload()
    }

    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    webView.settings.javaScriptEnabled = true

    webView.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // ページ読み込み開始時にエラー表示を隠す
        errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        super.onReceivedError(view, request, error)
        // メインフレームのエラーのみハンドリング
        if (request?.isForMainFrame == true) {
          showError()
        }
      }

      @Deprecated("Deprecated in Java")
      @Suppress("DEPRECATION")
      override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
      ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        showError()
      }

      override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
      ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // 404や500などのHTTPエラーもメインフレームならエラー画面を表示
        if (request?.isForMainFrame == true) {
          showError()
        }
      }

      override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
      ) {
        // SSL証明書エラー時もエラー画面を表示（デフォルトではキャンセルされる）
        showError()
      }
    }

    webView.addJavascriptInterface(JavaScriptInterface(this, webView), "AndroidInterface")
    webView.loadUrl("https://gekal-study-android.github.io/webview-interaction-sample/index.html")
  }

  private fun showError() {
    errorLayout.visibility = View.VISIBLE
    webView.visibility = View.GONE
  }
}
