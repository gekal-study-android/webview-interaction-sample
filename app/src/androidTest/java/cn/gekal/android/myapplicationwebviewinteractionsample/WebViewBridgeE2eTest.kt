package cn.gekal.android.myapplicationwebviewinteractionsample

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 実機（またはエミュレータ）でアプリを起動し、配信中のデモページとの往復を検証する E2E。
 *
 * `e2e/` の Playwright はブラウザ上で `AndroidInterface` をモックするため、
 * 実際の [JavaScriptInterface] が返す値やネイティブ側への反映までは確認できない。
 * ここでは本物の WebView・ブリッジ・SharedPreferences を通して確認する。
 *
 * 実行には端末と、[BuildConfig.WEBVIEW_URL] に届くネットワークが必要:
 * `scripts/test.sh e2e`
 */
@RunWith(AndroidJUnit4::class)
class WebViewBridgeE2eTest {
  @get:Rule
  val activityRule = ActivityScenarioRule(MainActivity::class.java)

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var web: WebViewDriver

  @Before
  fun setUp() {
    web = WebViewDriver.awaitDemoPage(activityRule.scenario)
  }

  @Test
  fun `ブリッジが注入され配信元のデモページが表示される`() {
    web.awaitTrue("AndroidInterface の注入", "typeof window.AndroidInterface === 'object'")
    // Native -> JS のエントリポイントが window に登録されていること
    web.awaitTrue("handleReturnValue の登録", "typeof window.handleReturnValue === 'function'")
    web.awaitTrue("onNativeEvent の登録", "typeof window.onNativeEvent === 'function'")

    assertEquals(BuildConfig.WEBVIEW_URL, web.eval("location.href"))
  }

  @Test
  fun `showToast がネイティブを経由して handleReturnValue で返る`() {
    web.clickButton("Show Toast")

    // ネイティブが Toast を出したあとに evaluateJavascript で呼び返す文言
    web.awaitTrue(
      "handleReturnValue の受信内容",
      "document.getElementById('message').textContent.trim() === " +
        "'Received: Hello from Android!'",
    )
    web.awaitBodyText("handleReturnValue('Hello from Android!')")
  }

  @Test
  fun `端末情報に実機の値が返る`() {
    // モックではなく Build / BatteryManager から取れた値が表示される
    web.awaitBodyText(Build.MODEL)
    web.awaitBodyText(Build.MANUFACTURER)
    web.awaitBodyText(context.packageName)
  }

  @Test
  fun `非同期コールバックが onNativeEvent で返る`() {
    web.clickButton("コールバックを要求")

    // 既定の遅延は 1000ms。ネイティブが postDelayed で呼び返すまで待つ
    web.awaitBodyText("後に応答しました", CALLBACK_TIMEOUT_MILLIS)
  }

  @Test
  fun `配色の切り替えがネイティブにも保存される`() {
    val preference = ThemePreference(context)
    val initial = web.colorScheme()
    // 画面が出た時点で setAppTheme() 済み。ここがずれると次回起動でちらつく
    awaitThemePreference(preference, AppTheme.from(initial))

    val toggled = if (initial == "dark") "light" else "dark"
    web.clickSelector("button[aria-label='カラーテーマを切り替える']")

    web.awaitTrue(
      "$toggled への切り替え",
      "document.documentElement.className.indexOf('$toggled') >= 0",
    )
    awaitThemePreference(preference, AppTheme.from(toggled))

    // 端末に配色が残ると次のテストや手動確認の前提が変わるため、元に戻す
    web.clickSelector("button[aria-label='カラーテーマを切り替える']")
    awaitThemePreference(preference, AppTheme.from(initial))
  }

  @Test
  fun `reloadPage でネイティブがページを読み直す`() {
    web.eval("window.__e2eMarker = true")
    web.awaitTrue("目印の設置", "window.__e2eMarker === true")

    web.clickButton("再読み込み")

    // 読み直されれば JS の実行コンテキストごと作り直され、目印は消える
    web.awaitTrue(
      "再読み込み後の再表示",
      "typeof window.__e2eMarker === 'undefined' && " +
        "typeof window.AndroidInterface === 'object'",
      RELOAD_TIMEOUT_MILLIS,
    )
  }

  /** `setAppTheme()` はメインスレッドに post されるため、反映されるまで少し待つ。 */
  private fun awaitThemePreference(preference: ThemePreference, expected: AppTheme) {
    val deadline = SystemClock.uptimeMillis() + THEME_TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
      if (preference.load() == expected) return
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    }
    assertEquals("ネイティブに保存された配色", expected, preference.load())
  }

  private companion object {
    const val CALLBACK_TIMEOUT_MILLIS = 20_000L
    const val RELOAD_TIMEOUT_MILLIS = 60_000L
    const val THEME_TIMEOUT_MILLIS = 5_000L
    const val POLL_INTERVAL_MILLIS = 100L
  }
}
