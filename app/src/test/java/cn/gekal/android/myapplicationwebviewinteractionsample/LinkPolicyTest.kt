package cn.gekal.android.myapplicationwebviewinteractionsample

import org.junit.Assert.assertEquals
import org.junit.Test

/** デモページのリンクが WebView 内 / 外部アプリのどちらに振り分けられるかを検証する。 */
class LinkPolicyTest {
  private val targetHost = "gekal-study-android.github.io"

  private fun resolve(scheme: String?, host: String?) = LinkPolicy.resolve(scheme, host, targetHost)

  @Test
  fun `配信元と同じホストの https は WebView 内で読み込む`() {
    assertEquals(Navigation.IN_WEB_VIEW, resolve("https", targetHost))
    assertEquals(Navigation.IN_WEB_VIEW, resolve("HTTPS", targetHost.uppercase()))
    assertEquals(Navigation.IN_WEB_VIEW, resolve("http", targetHost))
  }

  @Test
  fun `別ホストの https は端末のブラウザに渡す`() {
    // WebView 内で開くと戻る手段がなく、デモページに戻れなくなる
    assertEquals(Navigation.EXTERNAL_APP, resolve("https", "developer.android.com"))
  }

  @Test
  fun `WebView が読み込めないスキームは外部アプリに渡す`() {
    assertEquals(Navigation.EXTERNAL_APP, resolve("tel", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("mailto", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("sms", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("geo", null))
  }

  @Test
  fun `配信元のホストが不明なときは外部アプリに渡す`() {
    assertEquals(Navigation.EXTERNAL_APP, LinkPolicy.resolve("https", "example.com", null))
  }

  @Test
  fun `スキームごとに使う Intent の種類が決まる`() {
    // 発信は DIAL（ダイヤル画面を開くだけ）。CALL は権限が必要で即発信になるため使わない
    assertEquals(ExternalIntent.DIAL, LinkPolicy.externalIntentFor("tel"))
    assertEquals(ExternalIntent.SEND_TO, LinkPolicy.externalIntentFor("mailto"))
    assertEquals(ExternalIntent.SEND_TO, LinkPolicy.externalIntentFor("sms"))
    assertEquals(ExternalIntent.VIEW, LinkPolicy.externalIntentFor("geo"))
    assertEquals(ExternalIntent.VIEW, LinkPolicy.externalIntentFor("https"))
  }
}
