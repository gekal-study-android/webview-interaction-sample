package cn.gekal.android.myapplicationwebviewinteractionsample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/**
 * WebView 内の JavaScript から `AndroidInterface.<method>()` として呼び出されるブリッジ。
 *
 * `@JavascriptInterface` が付いたメソッドは WebView 専用のバックグラウンドスレッドで実行されるため、
 * UI に触れる処理は必ずメインスレッドへ post する。
 *
 * Web 側の型定義は `web/types/android.d.ts` にある。
 */
class JavaScriptInterface(
  private val context: Context,
  private val webView: WebView,
  private val onAppThemeChanged: (String) -> Unit = {},
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  /** 引数 1 つの呼び出し。既定は [Toast.LENGTH_SHORT]。 */
  @JavascriptInterface
  fun showToast(message: String) = showToast(message, false)

  /** JS からは引数の数でこちらのオーバーロードが選択される。 */
  @JavascriptInterface
  fun showToast(message: String, longDuration: Boolean) {
    val duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    mainHandler.post {
      Toast.makeText(context, message, duration).show()
      webView.evaluateJavascript("javascript: handleReturnValue('Hello from Android!')", null)
    }
  }

  /**
   * WebView 側の配色をネイティブにも反映させる。
   * システムバー周辺の余白が WebView の背景と食い違わないようにするために使う。
   *
   * @param theme `"light"` / `"dark"` / `"system"`
   */
  @JavascriptInterface
  fun setAppTheme(theme: String) {
    mainHandler.post { onAppThemeChanged(theme) }
  }

  /** 端末情報を JSON 文字列として同期的に返す。 */
  @JavascriptInterface
  fun getDeviceInfo(): String = JSONObject()
    .put("manufacturer", Build.MANUFACTURER)
    .put("model", Build.MODEL)
    .put("androidVersion", Build.VERSION.RELEASE)
    .put("sdkInt", Build.VERSION.SDK_INT)
    .put("appVersion", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    .put("packageName", context.packageName)
    .put("locale", Locale.getDefault().toString())
    .put("timeZone", TimeZone.getDefault().id)
    .toString()

  /** バッテリー残量と充電状態を JSON 文字列として同期的に返す。 */
  @JavascriptInterface
  fun getBatteryStatus(): String {
    val batteryManager = context.getSystemService(BatteryManager::class.java)
    return JSONObject()
      .put("level", batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
      .put("charging", batteryManager?.isCharging == true)
      .toString()
  }

  /** 端末を振動させる。`android.permission.VIBRATE` が必要。 */
  @JavascriptInterface
  fun vibrate(milliseconds: Int) {
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    val duration = milliseconds.coerceIn(MIN_VIBRATION_MILLIS, MAX_VIBRATION_MILLIS).toLong()
    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
  }

  /** クリップボードにテキストをコピーする。 */
  @JavascriptInterface
  fun copyToClipboard(label: String, text: String) {
    mainHandler.post {
      context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
  }

  /** 共有シート (ACTION_SEND) を開く。 */
  @JavascriptInterface
  fun shareText(text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    mainHandler.post {
      context.startActivity(
        Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }

  /**
   * 指定ミリ秒後に JS の `window.onNativeEvent()` を呼び返す。
   * Native -> JS の非同期コールバックのデモ。
   */
  @JavascriptInterface
  fun requestNativeCallback(requestId: String, delayMillis: Int) {
    val delay = delayMillis.coerceIn(0, MAX_CALLBACK_DELAY_MILLIS).toLong()
    val payload = JSONObject()
      .put("type", "callback")
      .put("requestId", requestId)
      .put("message", "ネイティブが ${delay}ms 後に応答しました")
      .toString()

    mainHandler.postDelayed({
      // JSONObject.quote() で JS の文字列リテラルとして安全に埋め込む
      webView.evaluateJavascript(
        "javascript: window.onNativeEvent(${JSONObject.quote(payload)})",
        null,
      )
    }, delay)
  }

  private companion object {
    const val MIN_VIBRATION_MILLIS = 1
    const val MAX_VIBRATION_MILLIS = 2_000
    const val MAX_CALLBACK_DELAY_MILLIS = 10_000
  }
}
