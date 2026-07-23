package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.core.net.toUri

private const val TAG = "MainActivity"

/**
 * 外部サイトを Custom Tabs（アプリ内ブラウザ）でアプリの上に重ねて開く。
 *
 * WebView 内に読み込むと URL が見えず、外部サイトだと分からないまま操作させてしまう。
 * Custom Tabs なら接続先が URL バーに表示され、閉じれば元の画面に戻る。
 * 対応ブラウザがない端末では通常のブラウザ起動にフォールバックする。
 */
private fun openInCustomTab(context: Context, uri: Uri, toolbarColor: Int) {
  val colors = CustomTabColorSchemeParams.Builder()
    .setToolbarColor(toolbarColor)
    .build()

  val customTabsIntent = CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(colors)
    // ページタイトルを出して、どのサイトを見ているかを分かりやすくする
    .setShowTitle(true)
    .setUrlBarHidingEnabled(false)
    .build()

  try {
    customTabsIntent.launchUrl(context, uri)
  } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "Custom Tabs を開けないためブラウザにフォールバックします: $uri", e)
    openWithExternalApp(context, uri)
  }
}

/**
 * `tel:` / `mailto:` などの URI を対応するアプリで開く。
 *
 * 電話は [Intent.ACTION_DIAL]（ダイヤル画面を開くだけ）を使う。
 * [Intent.ACTION_CALL] は即座に発信してしまい `CALL_PHONE` 権限も必要になるため使わない。
 */
private fun openWithExternalApp(context: Context, uri: Uri) {
  val action = when (LinkPolicy.externalIntentFor(uri.scheme)) {
    ExternalIntent.DIAL -> Intent.ACTION_DIAL
    ExternalIntent.SEND_TO -> Intent.ACTION_SENDTO
    ExternalIntent.VIEW -> Intent.ACTION_VIEW
  }
  val intent = Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "この URI を開けるアプリがありません: $uri", e)
    Toast.makeText(context, "対応するアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
  }
}

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
  var loadState by remember { mutableStateOf<LoadState>(LoadStateReducer.onLoadRequested()) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  val targetUrl = BuildConfig.WEBVIEW_URL

  // 配信元のホスト。これ以外の http(s) は端末のブラウザで開く
  val targetHost = remember(targetUrl) { targetUrl.toUri().host }

  // factory は一度しか実行されないため、最新のコールバックを参照できるようにする
  val currentOnAppThemeChanged by rememberUpdatedState(onAppThemeChanged)
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

  // Custom Tabs のツールバーもアプリと同じ配色にして、アプリ内の表示だと分かるようにする
  val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()

  val startLoad: (String) -> Unit = { url ->
    loadState = LoadStateReducer.onLoadRequested()
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
            /**
             * `tel:` や `mailto:` は WebView が読み込めずエラーになるため、端末のアプリに渡す。
             * http(s) でも配信元と異なるホストは端末のブラウザで開く。
             * WebView 内で遷移させると戻る手段がなく、デモページに戻れなくなるため。
             */
            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val uri = request?.url ?: return false
              when (LinkPolicy.resolve(uri.scheme, uri.host, targetHost)) {
                Navigation.IN_WEB_VIEW -> return false
                Navigation.CUSTOM_TAB -> openInCustomTab(context, uri, toolbarColor)
                Navigation.EXTERNAL_APP -> openWithExternalApp(context, uri)
              }
              return true
            }

            /** エラー画面を出すときは WebView を空にして、失敗したページを残さない。 */
            private fun handleMainFrameError(
              view: WebView?,
              isForMainFrame: Boolean,
              detail: String,
            ) {
              loadState = LoadStateReducer.onResourceError(loadState, isForMainFrame, detail)
              if (isForMainFrame) {
                view?.loadUrl(LoadStateReducer.BLANK_URL)
              }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              super.onPageStarted(view, url, favicon)
              loadState = LoadStateReducer.onPageStarted(loadState, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              loadState = LoadStateReducer.onPageFinished(loadState, url)
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              super.onReceivedError(view, request, error)
              handleMainFrameError(
                view,
                request?.isForMainFrame == true,
                LoadStateReducer.resourceErrorDetail(error?.description, error?.errorCode ?: 0),
              )
            }

            override fun onReceivedHttpError(
              view: WebView?,
              request: WebResourceRequest?,
              errorResponse: WebResourceResponse?,
            ) {
              super.onReceivedHttpError(view, request, errorResponse)
              handleMainFrameError(
                view,
                request?.isForMainFrame == true,
                LoadStateReducer.httpErrorDetail(
                  errorResponse?.statusCode ?: 0,
                  errorResponse?.reasonPhrase,
                ),
              )
            }

            override fun onReceivedSslError(
              view: WebView?,
              handler: SslErrorHandler?,
              error: SslError?,
            ) {
              handler?.cancel()
              handleMainFrameError(
                view,
                isForMainFrame = true,
                detail = LoadStateReducer.sslErrorDetail(error?.primaryError ?: 0),
              )
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
