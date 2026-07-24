package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import cn.gekal.android.myapplicationwebviewinteractionsample.ui.theme.myApplicationWebviewInteractionSampleTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val themePreference = ThemePreference(this)
    setContent {
      // 前回の選択を初期値にして、WebView が読み込まれるまでのちらつきを防ぐ。
      // 選択の真実の源は WebView 側（MUI が localStorage に保存）で、
      // 読み込み後に setAppTheme で上書きされる。
      var appTheme by remember { mutableStateOf(themePreference.load()) }
      val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
      }

      // ステータスバー / ナビゲーションバーのアイコン色を配色に追従させる
      val view = LocalView.current
      LaunchedEffect(darkTheme) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = !darkTheme
          isAppearanceLightNavigationBars = !darkTheme
        }
      }

      myApplicationWebviewInteractionSampleTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
          MainScreen(
            onAppThemeChanged = {
              appTheme = it
              themePreference.save(it)
            },
          )
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
  // null 以外のとき、この URL をアプリ内オーバーレイで表示する
  var overlayUrl by remember { mutableStateOf<String?>(null) }

  // vConsole の有効・無効はビルド variant（app/configs/*.json → BuildConfig）で決める。
  // デバッグは有効・リリースは無効。Web 側は ?vconsole= を見て切り替える。
  val targetUrl = remember {
    BuildConfig.WEBVIEW_URL.toUri()
      .buildUpon()
      .appendQueryParameter("vconsole", if (BuildConfig.VCONSOLE) "1" else "0")
      .build()
      .toString()
  }

  // 配信元のホスト。これ以外の http(s) は端末のブラウザで開く
  val targetHost = remember(targetUrl) { targetUrl.toUri().host }

  val localContext = LocalContext.current
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

  // Custom Tabs のツールバーもアプリと同じ配色にして、アプリ内の表示だと分かるようにする
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  val tabStyle = CustomTabStyle(
    toolbarColor = MaterialTheme.colorScheme.surface.toArgb(),
    // 部分表示は画面の 7 割程度にして、下にアプリが見えるようにする
    partialHeightPx = with(density) { (configuration.screenHeightDp.dp * 0.7f).roundToPx() },
  )

  // factory は一度しか実行されないため、最新のコールバックを参照できるようにする
  val currentOnAppThemeChanged by rememberUpdatedState(onAppThemeChanged)
  val currentOpenExternal by rememberUpdatedState<(String, ExternalOpenMode) -> Unit> { url, mode ->
    if (mode == ExternalOpenMode.IN_APP_OVERLAY) {
      if (LinkPolicy.isBrowsableUrl(url.toUri().scheme)) overlayUrl = url
    } else {
      openExternalLink(localContext, url, mode, tabStyle)
    }
  }

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
          // localStorage を有効にする。既定は無効で、有効にしないと MUI がテーマ選択を
          // 保存できず、次回起動時にシステム設定へ戻ってしまう。
          settings.domStorageEnabled = true
          // window.open() / target="_blank" を onCreateWindow で受け取れるようにする。
          // 無効（既定）のままだと同じ WebView に読み込まれてしまう。
          settings.setSupportMultipleWindows(true)
          // ユーザー操作を起点としない window.open() も onCreateWindow に届くようにする
          settings.javaScriptCanOpenWindowsAutomatically = true
          // 読み込み完了までの白い一瞬を防ぐ
          setBackgroundColor(backgroundColor)

          // window.open() は shouldOverrideUrlLoading を通らないため、ここで受け取って
          // 通常のリンクと同じ経路（Custom Tabs / 外部アプリ）に流す
          webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
              view: WebView?,
              isDialog: Boolean,
              isUserGesture: Boolean,
              resultMsg: Message?,
            ): Boolean {
              val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

              // 遷移先 URL を知る手段がないため、捨てる前提の WebView に一度渡して受け取る
              val probe = WebView(context)
              probe.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  probeView: WebView?,
                  request: WebResourceRequest?,
                ): Boolean {
                  request?.url?.let { popupUrl ->
                    openExternalLink(
                      context,
                      popupUrl.toString(),
                      ExternalOpenMode.CUSTOM_TAB,
                      tabStyle,
                    )
                  }
                  probeView?.destroy()
                  return true
                }
              }

              transport.webView = probe
              resultMsg.sendToTarget()
              return true
            }
          }

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

                Navigation.CUSTOM_TAB ->
                  openExternalLink(context, uri.toString(), ExternalOpenMode.CUSTOM_TAB, tabStyle)

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
              onOpenExternal = { url, mode -> currentOpenExternal(url, mode) },
            ),
            "AndroidInterface",
          )
          loadUrl(targetUrl)
          webViewInstance = this
        }
      },
      update = {
        // オーバーレイ表示中は下の WebView を隠す。
        // 出したままだと AndroidView 同士でタッチと描画が競合し、操作を受け付けなくなる。
        it.visibility = if (loadState is LoadState.Error || overlayUrl != null) {
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

    // 外部サイトを現在の WebView の上に重ねて表示する
    overlayUrl?.let { url ->
      InAppBrowser(url = url, onClose = { overlayUrl = null })
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
