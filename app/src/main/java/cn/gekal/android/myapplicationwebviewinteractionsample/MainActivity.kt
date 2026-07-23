package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import cn.gekal.android.myapplicationwebviewinteractionsample.ui.theme.myApplicationWebviewInteractionSampleTheme

/** エラー時に WebView を空にするための URL。 */
private const val BLANK_URL = "about:blank"

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

/** WebView の読み込み状態。 */
sealed interface LoadState {
  data object Loading : LoadState

  data object Loaded : LoadState

  /** メインフレームの読み込みに失敗した状態。[detail] は原因の概要。 */
  data class Error(val detail: String) : LoadState
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
  var loadState by remember { mutableStateOf<LoadState>(LoadState.Loading) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  val targetUrl = BuildConfig.WEBVIEW_URL

  // factory は一度しか実行されないため、最新のコールバックを参照できるようにする
  val currentOnAppThemeChanged by rememberUpdatedState(onAppThemeChanged)
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

  val startLoad: (String) -> Unit = { url ->
    loadState = LoadState.Loading
    webViewInstance?.loadUrl(url)
  }

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
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              super.onPageStarted(view, url, favicon)
              // エラー後に読み込む about:blank は状態を変えない
              if (url != BLANK_URL) {
                loadState = LoadState.Loading
              }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              if (url != BLANK_URL && loadState is LoadState.Loading) {
                loadState = LoadState.Loaded
              }
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              super.onReceivedError(view, request, error)
              if (request?.isForMainFrame == true) {
                loadState = LoadState.Error("${error?.description} (code: ${error?.errorCode})")
                view?.loadUrl(BLANK_URL)
              }
            }

            override fun onReceivedHttpError(
              view: WebView?,
              request: WebResourceRequest?,
              errorResponse: WebResourceResponse?,
            ) {
              super.onReceivedHttpError(view, request, errorResponse)
              if (request?.isForMainFrame == true) {
                loadState =
                  LoadState.Error(
                    "HTTP ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}",
                  )
                view?.loadUrl(BLANK_URL)
              }
            }

            override fun onReceivedSslError(
              view: WebView?,
              handler: SslErrorHandler?,
              error: SslError?,
            ) {
              loadState = LoadState.Error("SSL エラー (primaryError: ${error?.primaryError})")
              handler?.cancel()
              view?.loadUrl(BLANK_URL)
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
        it.visibility = if (loadState is LoadState.Error) {
          android.view.View.GONE
        } else {
          android.view.View.VISIBLE
        }
        it.setBackgroundColor(backgroundColor)
      },
    )

    when (val state = loadState) {
      is LoadState.Loading -> LoadingView()
      is LoadState.Error -> ErrorView(detail = state.detail, onRetry = { startLoad(targetUrl) })
      is LoadState.Loaded -> Unit
    }
  }
}

@Composable
fun LoadingView() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
fun ErrorView(detail: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "ページを読み込めませんでした",
      color = MaterialTheme.colorScheme.error,
      fontSize = 18.sp,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "通信状況を確認してから再試行してください。",
      color = MaterialTheme.colorScheme.onBackground,
      fontSize = 14.sp,
      textAlign = TextAlign.Center,
    )
    if (detail.isNotBlank()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = detail,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("再試行")
    }
  }
}
