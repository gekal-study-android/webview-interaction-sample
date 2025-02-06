package cn.gekal.android.myapplicationwebviewinteractionsample

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast

class JavaScriptInterface(
  private val context: Context,
  private val webView: WebView,
) {
  @JavascriptInterface
  fun showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    Handler(Looper.getMainLooper()).post {
      webView.evaluateJavascript("javascript: handleReturnValue('Hello from Android!')", null)
    }
  }
}
