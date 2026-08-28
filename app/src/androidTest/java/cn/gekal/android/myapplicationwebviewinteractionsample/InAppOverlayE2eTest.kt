package cn.gekal.android.myapplicationwebviewinteractionsample

import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 外部サイトをアプリ内オーバーレイ（2 つ目の WebView）で開く経路を、実機で確認する E2E。
 *
 * Playwright ではネイティブに `IN_APP_OVERLAY` を渡すところまでしか見られない。
 * ここでは実際にオーバーレイが重なり、戻る操作で閉じてデモ画面に復帰することを確認する。
 *
 * Compose のテストルールは**使わない**。入れるとフレームクロックがテスト側の制御に移り、
 * こちらから Compose の API を呼ばない限りオーバーレイの合成が進まなくなる。
 * このテストは WebView と Activity の状態だけで検証できるため、ルールなしで十分。
 *
 * 実行には端末と、[BuildConfig.WEBVIEW_URL] に届くネットワークが必要:
 * `scripts/test.sh e2e`
 */
@RunWith(AndroidJUnit4::class)
class InAppOverlayE2eTest {
  @get:Rule
  val activityRule = ActivityScenarioRule(MainActivity::class.java)

  private lateinit var web: WebViewDriver

  @Before
  fun setUp() {
    web = WebViewDriver.awaitDemoPage(activityRule.scenario)
  }

  @Test
  fun `アプリ内オーバーレイで外部サイトを開き戻る操作で閉じる`() {
    web.clickButton("アプリ内オーバーレイ")

    // オーバーレイ表示中はデモページの WebView を隠す。出したままだと AndroidView 同士で
    // タッチと描画が競合するため、隠れたことがオーバーレイが出た証拠になる。
    web.awaitState("アプリ内オーバーレイの表示") { it.visibility == View.GONE }

    // オーバーレイの BackHandler が登録されるのを待つ。登録前に戻ると Activity ごと終了する
    awaitActivity("戻る操作の受け取り") { it.onBackPressedDispatcher.hasEnabledCallbacks() }

    // 端末の戻る操作で閉じられること。閉じられないとデモ画面に戻れなくなる
    activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    web.awaitState("アプリ内オーバーレイを閉じたあとの再表示") { it.visibility == View.VISIBLE }

    // 下に隠れていたデモページが、そのまま操作できる状態で戻ってくること
    web.clickButton("Show Toast")
    web.awaitBodyText("handleReturnValue('Hello from Android!')")
  }

  /** Activity の状態が条件を満たすまで待つ。 */
  private fun awaitActivity(description: String, predicate: (MainActivity) -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + ACTIVITY_TIMEOUT_MILLIS
    do {
      var matched = false
      activityRule.scenario.onActivity { matched = predicate(it) }
      if (matched) return
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    } while (SystemClock.uptimeMillis() < deadline)
    throw AssertionError("$description が ${ACTIVITY_TIMEOUT_MILLIS}ms 以内に成立しませんでした")
  }

  private companion object {
    const val ACTIVITY_TIMEOUT_MILLIS = 15_000L
    const val POLL_INTERVAL_MILLIS = 100L
  }
}
