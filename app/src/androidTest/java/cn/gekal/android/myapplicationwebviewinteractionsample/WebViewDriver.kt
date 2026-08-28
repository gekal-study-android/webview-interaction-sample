package cn.gekal.android.myapplicationwebviewinteractionsample

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 実機で動いている WebView をテストから操作するための薄いドライバ。
 *
 * Web 側の操作は指タップではなく `evaluateJavascript` で DOM を直接叩く。
 * デモページは縦に長く、実機のタップだと画面外のボタンまでスクロールが必要で不安定になるため。
 * ネイティブ側（[JavaScriptInterface] / `WebViewClient` / `Toast` / SharedPreferences）は
 * 本物がそのまま動くので、ブラウザ相手の Playwright では確認できない往復を実機で検証できる。
 */
class WebViewDriver private constructor(private val webView: WebView) {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /**
   * JS を評価して結果を文字列で返す。真偽値は `"true"` / `"false"`、`null` は空文字になる。
   *
   * `evaluateJavascript` のコールバックはメインスレッドで呼ばれるため、テストスレッドからは
   * ラッチで待ち受ける。
   */
  fun eval(script: String): String {
    val latch = CountDownLatch(1)
    var raw = NULL_LITERAL
    instrumentation.runOnMainSync {
      webView.evaluateJavascript(script) { value ->
        raw = value ?: NULL_LITERAL
        latch.countDown()
      }
    }
    check(latch.await(EVAL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
      "JS の評価がタイムアウトしました: $script"
    }
    // 戻り値は JSON リテラル（文字列なら引用符付き）で返るため、配列に包んで取り出す
    return if (raw == NULL_LITERAL) "" else JSONArray("[$raw]").optString(0, "")
  }

  /** JS の条件式が成立するまで待つ。時間切れなら最後の評価結果を添えて失敗させる。 */
  fun awaitTrue(
    description: String,
    expression: String,
    timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS,
  ) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var last: String
    do {
      last = eval(guarded(expression))
      if (last == "true") return
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    } while (SystemClock.uptimeMillis() < deadline)
    throw AssertionError(
      "$description が ${timeoutMillis}ms 以内に成立しませんでした（最後の評価: 「$last」）",
    )
  }

  /** 画面に指定の文字列が現れるまで待つ。 */
  fun awaitBodyText(text: String, timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS) {
    awaitTrue(
      "「$text」の表示",
      "document.body.innerText.indexOf(${quote(text)}) >= 0",
      timeoutMillis,
    )
  }

  /** 表示中のラベルでボタンを押す。押せる状態（ハイドレーション後）になるまで再試行する。 */
  fun clickButton(label: String) {
    awaitTrue("ボタン「$label」の押下", "$CLICK_BY_LABEL(${quote(label)})")
  }

  /** CSS セレクタで要素を押す。ラベルを持たないアイコンボタン用。 */
  fun clickSelector(selector: String) {
    awaitTrue("要素 $selector の押下", "$CLICK_BY_SELECTOR(${quote(selector)})")
  }

  /** MUI が `<html>` に付けている配色クラスから、いま適用されている配色を読む。 */
  fun colorScheme(): String =
    if (eval("document.documentElement.className").contains("dark")) "dark" else "light"

  /** WebView 自体の状態をメインスレッドで読む。 */
  fun <T> read(block: (WebView) -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block(webView) }

    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  /** WebView 自体の状態が条件を満たすまで待つ。 */
  fun awaitState(
    description: String,
    timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS,
    predicate: (WebView) -> Boolean,
  ) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    do {
      if (read(predicate)) return
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    } while (SystemClock.uptimeMillis() < deadline)
    throw AssertionError("$description が ${timeoutMillis}ms 以内に成立しませんでした")
  }

  companion object {
    /** 実機の通信は遅いことがあるため、ページの読み込みだけ長めに待つ。 */
    private const val LOAD_TIMEOUT_MILLIS = 60_000L
    private const val AWAIT_TIMEOUT_MILLIS = 15_000L
    private const val EVAL_TIMEOUT_MILLIS = 10_000L
    private const val VIEW_TIMEOUT_MILLIS = 10_000L
    private const val POLL_INTERVAL_MILLIS = 250L
    private const val NULL_LITERAL = "null"

    /** ラベルが一致して押せる状態のボタンをクリックする。見つからなければ false。 */
    private val CLICK_BY_LABEL =
      """
      (function (label) {
        var buttons = Array.prototype.slice.call(document.querySelectorAll('button'));
        var target = buttons.filter(function (button) {
          return (button.textContent || '').trim() === label && !button.disabled;
        })[0];
        if (!target) { return false; }
        target.click();
        return true;
      })
      """.trimIndent()

    /** セレクタに一致して押せる状態の要素をクリックする。見つからなければ false。 */
    private val CLICK_BY_SELECTOR =
      """
      (function (selector) {
        var target = document.querySelector(selector);
        if (!target || target.disabled) { return false; }
        target.click();
        return true;
      })
      """.trimIndent()

    /**
     * デモページが読み込まれ、ブリッジが使える状態になるまで待ってからドライバを返す。
     *
     * ヘッダーの「接続済み」はハイドレーション完了と `AndroidInterface` の検出の両方を
     * 満たしたときだけ出るため、操作を始めてよい合図として使える。
     */
    fun awaitDemoPage(scenario: ActivityScenario<MainActivity>): WebViewDriver {
      val driver = WebViewDriver(awaitWebView(scenario))
      driver.awaitBodyText("接続済み", LOAD_TIMEOUT_MILLIS)
      return driver
    }

    /** Compose の `AndroidView` が生成する WebView は少し遅れて現れるため、見つかるまで待つ。 */
    private fun awaitWebView(scenario: ActivityScenario<MainActivity>): WebView {
      val deadline = SystemClock.uptimeMillis() + VIEW_TIMEOUT_MILLIS
      var found: WebView? = null
      while (found == null && SystemClock.uptimeMillis() < deadline) {
        scenario.onActivity { activity -> found = findWebView(activity.window.decorView) }
        if (found == null) SystemClock.sleep(POLL_INTERVAL_MILLIS)
      }
      return requireNotNull(found) { "WebView がビュー階層に見つかりませんでした" }
    }

    private fun findWebView(view: View): WebView? = when (view) {
      is WebView -> view

      is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull {
        findWebView(view.getChildAt(it))
      }

      else -> null
    }

    /** 評価中の例外でテストが落ちないよう、条件式を真偽値に丸めて返す。 */
    private fun guarded(expression: String) =
      """
      (function () {
        try { return !!($expression); } catch (error) { return 'ERROR: ' + error; }
      })()
      """.trimIndent()

    /** Kotlin の文字列を JS の文字列リテラルとして安全に埋め込む。 */
    private fun quote(value: String): String = JSONObject.quote(value)
  }
}
