package cn.gekal.android.myapplicationwebviewinteractionsample

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WebView の依頼で出るネイティブのエラー画面を、実機で確認する E2E。
 *
 * エラー画面は Compose の描画なので、ブラウザで動く Playwright では「ネイティブに依頼が
 * 届いたところ」までしか検証できない。ここでは実際に画面が切り替わり、再試行で元に
 * 戻れるところまでを確認する。
 *
 * Compose のノードはアクセシビリティ経由（UiAutomator）では見えないため、画面の確認には
 * Compose のテスト API を使う。Activity は [ActivityScenarioRule] で起動するので、
 * 空の Compose ルールと組み合わせている。
 *
 * 実行には端末と、[BuildConfig.WEBVIEW_URL] に届くネットワークが必要:
 * `scripts/test.sh e2e`
 */
@RunWith(AndroidJUnit4::class)
class NativeErrorScreenE2eTest {
  @get:Rule(order = 0)
  val composeRule = createEmptyComposeRule()

  @get:Rule(order = 1)
  val activityRule = ActivityScenarioRule(MainActivity::class.java)

  private lateinit var web: WebViewDriver

  @Before
  fun setUp() {
    web = WebViewDriver.awaitDemoPage(activityRule.scenario)
  }

  @Test
  fun `読み込みエラーの再現でネイティブのエラー画面が出て再試行で復帰する`() {
    web.clickButton("読み込みエラーを再現")

    // 失敗したページを残さないよう、WebView は about:blank にして隠される
    web.awaitState("エラー画面への切り替え") { webView ->
      webView.visibility == View.GONE && webView.url == LoadStateReducer.BLANK_URL
    }
    composeRule.onNodeWithText("ページを読み込めませんでした").assertIsDisplayed()

    composeRule.onNodeWithText("再試行").performClick()

    // 再試行で配信 URL を読み直し、デモページに戻れること
    web.awaitState("WebView の再表示", LOAD_TIMEOUT_MILLIS) { it.visibility == View.VISIBLE }
    web.awaitBodyText("接続済み", LOAD_TIMEOUT_MILLIS)
  }

  private companion object {
    const val LOAD_TIMEOUT_MILLIS = 60_000L
  }
}
