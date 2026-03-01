package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          MainScreen()
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen() {
  var isError by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  val targetUrl = BuildConfig.WEBVIEW_URL

  Box(
    modifier = Modifier
      .fillMaxSize()
      .systemBarsPadding(),
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        WebView(context).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
          WebView.setWebContentsDebuggingEnabled(true)
          settings.cacheMode = WebSettings.LOAD_NO_CACHE
          settings.javaScriptEnabled = true

          webViewClient = object : WebViewClient() {
            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) {
                isError = true
                view?.loadUrl("about:blank")
              }
            }

            override fun onReceivedHttpError(
              view: WebView?,
              request: WebResourceRequest?,
              errorResponse: WebResourceResponse?,
            ) {
              super.onReceivedHttpError(view, request, errorResponse)
              if (request?.isForMainFrame == true) {
                isError = true
                view?.loadUrl("about:blank")
              }
            }

            override fun onReceivedSslError(
              view: WebView?,
              handler: SslErrorHandler?,
              error: SslError?,
            ) {
              isError = true
              handler?.cancel()
              view?.loadUrl("about:blank")
            }
          }

          addJavascriptInterface(JavaScriptInterface(context, this), "AndroidInterface")
          loadUrl(targetUrl)
          webViewInstance = this
        }
      },
      update = {
        it.visibility = if (isError) android.view.View.GONE else android.view.View.VISIBLE
      },
    )

    if (isError) {
      ErrorView(onRetry = {
        isError = false
        webViewInstance?.loadUrl(targetUrl)
      })
    }
  }
}

@Composable
fun ErrorView(onRetry: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "ネットワークエラーが発生しました",
      color = Color.Red,
      fontSize = 18.sp,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("再試行")
    }
  }
}
