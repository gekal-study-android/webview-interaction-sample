package cn.gekal.android.myapplicationwebviewinteractionsample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
  fun `別ホストの https は Custom Tabs で開く`() {
    // WebView 内で開くと URL が見えず、外部サイトだと分からないまま操作させてしまう
    assertEquals(Navigation.CUSTOM_TAB, resolve("https", "developer.android.com"))
    assertEquals(Navigation.CUSTOM_TAB, resolve("http", "example.com"))
  }

  @Test
  fun `WebView が読み込めないスキームは外部アプリに渡す`() {
    assertEquals(Navigation.EXTERNAL_APP, resolve("tel", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("mailto", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("sms", null))
    assertEquals(Navigation.EXTERNAL_APP, resolve("geo", null))
  }

  @Test
  fun `配信元のホストが不明なときは WebView 内で開かない`() {
    assertEquals(Navigation.CUSTOM_TAB, LinkPolicy.resolve("https", "example.com", null))
  }

  @Test
  fun `ブラウザ表示に使えるのは http と https だけ`() {
    // WebView から任意の URL を渡せるため、javascript: や file: を開かせない
    assertTrue(LinkPolicy.isBrowsableUrl("https"))
    assertTrue(LinkPolicy.isBrowsableUrl("http"))
    assertFalse(LinkPolicy.isBrowsableUrl("javascript"))
    assertFalse(LinkPolicy.isBrowsableUrl("file"))
    assertFalse(LinkPolicy.isBrowsableUrl("content"))
    assertFalse(LinkPolicy.isBrowsableUrl(null))
  }

  @Test
  fun `intent スキームは INTENT_URI のときだけ許可する`() {
    // intent:// は任意のコンポーネントを起動しうるため、明示的に選んだときだけ通す
    assertTrue(LinkPolicy.isAllowedFor(ExternalOpenMode.INTENT_URI, "intent"))
    assertFalse(LinkPolicy.isAllowedFor(ExternalOpenMode.INTENT_URI, "https"))
    assertFalse(LinkPolicy.isAllowedFor(ExternalOpenMode.CUSTOM_TAB, "intent"))
    assertTrue(LinkPolicy.isAllowedFor(ExternalOpenMode.CUSTOM_TAB, "https"))
    assertTrue(LinkPolicy.isAllowedFor(ExternalOpenMode.IN_APP_OVERLAY, "http"))
    assertFalse(LinkPolicy.isAllowedFor(ExternalOpenMode.PARTIAL_CUSTOM_TAB, "javascript"))
  }

  @Test
  fun `開き方の名前を変換し、未知の値は Custom Tabs にする`() {
    assertEquals(ExternalOpenMode.PARTIAL_CUSTOM_TAB, ExternalOpenMode.from("PARTIAL_CUSTOM_TAB"))
    assertEquals(ExternalOpenMode.APP_LINK, ExternalOpenMode.from("app_link"))
    assertEquals(
      ExternalOpenMode.TRUSTED_WEB_ACTIVITY,
      ExternalOpenMode.from("Trusted_Web_Activity"),
    )
    // WebView から任意の文字列が来ても壊れないこと
    assertEquals(ExternalOpenMode.CUSTOM_TAB, ExternalOpenMode.from("nope"))
    assertEquals(ExternalOpenMode.CUSTOM_TAB, ExternalOpenMode.from(null))
    assertEquals(ExternalOpenMode.CUSTOM_TAB, ExternalOpenMode.from(""))
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
