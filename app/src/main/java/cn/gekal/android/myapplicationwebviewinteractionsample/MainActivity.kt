package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import cn.gekal.android.myapplicationwebviewinteractionsample.ui.theme.myApplicationWebviewInteractionSampleTheme

/** アプリの配色。既定は [SYSTEM]（端末のダークモード設定に追従）。 */
enum class AppTheme {
  SYSTEM,
  LIGHT,
  DARK,
  ;

  companion object {
    /** WebView から渡される文字列を [AppTheme] に変換する。未知の値は [SYSTEM] にフォールバックする。 */
    fun from(value: String): AppTheme =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      // 起動時は端末のシステム設定に従い、WebView 側の切り替えで上書きされる
      var appTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
      val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
      }

      // ステータスバー / ナビゲーションバーのアイコン色を配色に追従させる
      val view = LocalView.current
      LaunchedEffect(darkTheme) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = !darkTheme
          isAppearanceLightNavigationBars = !darkTheme
        }
      }

      myApplicationWebviewInteractionSampleTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
          MainScreen(onAppThemeChanged = { appTheme = it })
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(onAppThemeChanged: (AppTheme) -> Unit) {
  var isError by remember { mutableStateOf(false) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  val targetUrl = BuildConfig.WEBVIEW_URL

  // factory は一度しか実行されないため、最新のコールバックを参照できるようにする
  val currentOnAppThemeChanged by rememberUpdatedState(onAppThemeChanged)
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

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
          // 読み込み完了までの白い一瞬を防ぐ
          setBackgroundColor(backgroundColor)

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

          addJavascriptInterface(
            JavaScriptInterface(
              context = context,
              webView = this,
              onAppThemeChanged = { currentOnAppThemeChanged(AppTheme.from(it)) },
            ),
            "AndroidInterface",
          )
          loadUrl(targetUrl)
          webViewInstance = this
        }
      },
      update = {
        it.visibility = if (isError) android.view.View.GONE else android.view.View.VISIBLE
        it.setBackgroundColor(backgroundColor)
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
      .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "ネットワークエラーが発生しました",
      color = MaterialTheme.colorScheme.error,
      fontSize = 18.sp,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("再試行")
    }
  }
}
