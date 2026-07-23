package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/** オーバーレイ内の WebView の状態。 */
private sealed interface OverlayState {
  /** 読み込み中。[progress] は 0..100。 */
  data class Loading(val progress: Int) : OverlayState

  data object Ready : OverlayState

  data class Failed(val detail: String) : OverlayState
}

/**
 * 外部サイトをアプリ内に重ねて表示するオーバーレイ。
 *
 * Custom Tabs と違ってブラウザアプリに依存しないため、どの端末でも同じ見た目になる。
 * 代わりにブラウザのセッションやセキュリティ表示は共有されないので、
 * 実サービスのログイン（OAuth）には使わないこと。Google は WebView でのサインインを拒否する。
 *
 * 外部サイトを読み込むため、[JavaScriptInterface] は **注入しない**。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowser(url: String, onClose: () -> Unit) {
  var state by remember(url) { mutableStateOf<OverlayState>(OverlayState.Loading(0)) }
  var reloadKey by remember(url) { mutableIntStateOf(0) }
  val host = remember(url) { url.toUri().host.orEmpty() }
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

  // 端末の戻る操作でオーバーレイを閉じる
  BackHandler(onBack = onClose)

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      ExternalSiteBar(host = host, state = state, onClose = onClose)

      Box(modifier = Modifier.fillMaxSize()) {
        key(reloadKey) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
              WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                setBackgroundColor(backgroundColor)

                webChromeClient = object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // 何 % まで進んだかを出して、止まっていないことを示す
                    (state as? OverlayState.Loading)?.let {
                      state =
                        OverlayState.Loading(newProgress)
                    }
                  }
                }

                webViewClient = object : WebViewClient() {
                  override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, pageUrl, favicon)
                    state = OverlayState.Loading(0)
                  }

                  /** 最初の描画ができた時点で読み込み中の表示を消す。 */
                  override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                    super.onPageCommitVisible(view, pageUrl)
                    if (state is OverlayState.Loading) state = OverlayState.Ready
                  }

                  override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    if (state is OverlayState.Loading) state = OverlayState.Ready
                  }

                  override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                  ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                      state = OverlayState.Failed(
                        LoadStateReducer.resourceErrorDetail(
                          error?.description,
                          error?.errorCode ?: 0,
                        ),
                      )
                    }
                  }
                }

                loadUrl(url)
              }
            },
          )
        }

        // 読み込み中と失敗は WebView の上に重ねて出す
        when (val current = state) {
          is OverlayState.Loading -> LoadingOverlay(host = host, progress = current.progress)

          is OverlayState.Failed -> FailedOverlay(
            detail = current.detail,
            onRetry = {
              state = OverlayState.Loading(0)
              reloadKey += 1
            },
            onClose = onClose,
          )

          OverlayState.Ready -> Unit
        }
      }
    }
  }
}

/** 読み込み中であることを画面全体で示す。 */
@Composable
private fun LoadingOverlay(host: String, progress: Int) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = "読み込み中…", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = host,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    LinearProgressIndicator(
      progress = { progress / 100f },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "$progress%",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** 読み込みに失敗したことを示し、再試行と閉じるを出す。 */
@Composable
private fun FailedOverlay(detail: String, onRetry: () -> Unit, onClose: () -> Unit) {
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
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = detail,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRetry) {
      Text("再試行")
    }
    TextButton(onClick = onClose) {
      Text("閉じる")
    }
  }
}

/** 外部サイトであることと接続先、進捗を示すヘッダー。 */
@Composable
private fun ExternalSiteBar(host: String, state: OverlayState, onClose: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Material Icons を依存に追加しないため、閉じるボタンは記号で表現する
        IconButton(onClick = onClose) {
          Text(text = "✕", style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (state is OverlayState.Loading) "外部サイト · 読み込み中" else "外部サイト",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = host,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (state is OverlayState.Loading) {
        LinearProgressIndicator(
          progress = { state.progress / 100f },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
