package cn.gekal.android.myapplicationwebviewinteractionsample

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「読み込みエラーを再現」ボタンからエラー画面が出て、「再試行」で復帰するまでの
 * 状態遷移を検証する。端末やエミュレータなしで実行できる。
 */
class LoadStateReducerTest {
  @Test
  fun `メインフレームのエラーでエラー画面に遷移する`() {
    val detail = LoadStateReducer.resourceErrorDetail("net::ERR_NAME_NOT_RESOLVED", -2)

    val state = LoadStateReducer.onResourceError(
      LoadState.Loading,
      isForMainFrame = true,
      detail = detail,
    )

    assertEquals(LoadState.Error("net::ERR_NAME_NOT_RESOLVED (code: -2)"), state)
  }

  @Test
  fun `サブフレームのエラーでは状態を変えない`() {
    val state = LoadStateReducer.onResourceError(
      LoadState.Loaded,
      isForMainFrame = false,
      detail = "画像の読み込み失敗",
    )

    assertEquals(LoadState.Loaded, state)
  }

  @Test
  fun `エラー後に読み込む about_blank では状態を変えない`() {
    val error = LoadState.Error("net::ERR_NAME_NOT_RESOLVED (code: -2)")

    // エラー画面を出したまま WebView だけ空にするため、about:blank は無視する必要がある
    assertEquals(error, LoadStateReducer.onPageStarted(error, LoadStateReducer.BLANK_URL))
    assertEquals(error, LoadStateReducer.onPageFinished(error, LoadStateReducer.BLANK_URL))
  }

  @Test
  fun `再試行で読み込み中に戻り、完了で読み込み済みになる`() {
    val error = LoadState.Error("net::ERR_NAME_NOT_RESOLVED (code: -2)")

    val retrying = LoadStateReducer.onLoadRequested()
    assertEquals(LoadState.Loading, retrying)

    val started = LoadStateReducer.onPageStarted(retrying, "https://example.com/index.html")
    assertEquals(LoadState.Loading, started)

    val finished = LoadStateReducer.onPageFinished(started, "https://example.com/index.html")
    assertEquals(LoadState.Loaded, finished)

    // エラー状態から直接遷移していないこと（再試行を経由する）を明示する
    assertEquals(error, LoadStateReducer.onPageFinished(error, "https://example.com/index.html"))
  }

  @Test
  fun `新しい読み込みが始まると読み込み中に戻る`() {
    assertEquals(
      LoadState.Loading,
      LoadStateReducer.onPageStarted(LoadState.Loaded, "https://example.com/"),
    )
  }

  @Test
  fun `エラーの詳細を種類ごとに組み立てる`() {
    assertEquals("HTTP 404 Not Found", LoadStateReducer.httpErrorDetail(404, "Not Found"))
    assertEquals("HTTP 500", LoadStateReducer.httpErrorDetail(500, null))
    assertEquals("SSL エラー (primaryError: 3)", LoadStateReducer.sslErrorDetail(3))
  }
}
