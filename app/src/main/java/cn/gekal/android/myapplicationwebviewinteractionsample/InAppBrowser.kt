package cn.gekal.android.myapplicationwebviewinteractionsample

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

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
  var isLoading by remember(url) { mutableStateOf(true) }
  val host = remember(url) { Uri.parse(url).host.orEmpty() }
  val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

  // 端末の戻る操作でオーバーレイを閉じる
  BackHandler(onBack = onClose)

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      ExternalSiteBar(host = host, onClose = onClose)

      if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }

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

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(
                view: WebView?,
                pageUrl: String?,
                favicon: android.graphics.Bitmap?,
              ) {
                super.onPageStarted(view, pageUrl, favicon)
                isLoading = true
              }

              override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                isLoading = false
              }
            }

            loadUrl(url)
          }
        },
      )
    }
  }
}

/** 外部サイトであることと接続先を示すヘッダー。 */
@Composable
private fun ExternalSiteBar(host: String, onClose: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
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
          text = "外部サイト",
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
  }
}
