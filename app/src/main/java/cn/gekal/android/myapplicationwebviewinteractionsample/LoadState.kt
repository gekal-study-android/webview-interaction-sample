package cn.gekal.android.myapplicationwebviewinteractionsample

/** WebView の読み込み状態。 */
sealed interface LoadState {
  data object Loading : LoadState

  data object Loaded : LoadState

  /** メインフレームの読み込みに失敗した状態。[detail] は原因の概要。 */
  data class Error(val detail: String) : LoadState
}

/**
 * `WebViewClient` のコールバックから次の [LoadState] を決めるロジック。
 *
 * Android SDK に依存しないため、端末やエミュレータなしの JVM ユニットテストで検証できる。
 * `MainActivity` の `WebViewClient` はこの結果をそのまま state に反映するだけにしている。
 */
object LoadStateReducer {
  /** エラー時に WebView を空にするための URL。状態遷移の対象にしない。 */
  const val BLANK_URL = "about:blank"

  /** 読み込みの開始（初回 / 再試行 / 再読み込み）。 */
  fun onLoadRequested(): LoadState = LoadState.Loading

  fun onPageStarted(current: LoadState, url: String?): LoadState =
    if (url == BLANK_URL) current else LoadState.Loading

  fun onPageFinished(current: LoadState, url: String?): LoadState =
    if (url != BLANK_URL && current is LoadState.Loading) LoadState.Loaded else current

  /**
   * 読み込みエラー。サブフレーム（画像や iframe など）の失敗でエラー画面を出さないよう、
   * メインフレームのときだけ [LoadState.Error] に遷移する。
   */
  fun onResourceError(current: LoadState, isForMainFrame: Boolean, detail: String): LoadState =
    if (isForMainFrame) LoadState.Error(detail) else current

  fun resourceErrorDetail(description: CharSequence?, errorCode: Int): String =
    "$description (code: $errorCode)"

  fun httpErrorDetail(statusCode: Int, reasonPhrase: String?): String =
    "HTTP $statusCode ${reasonPhrase.orEmpty()}".trimEnd()

  fun sslErrorDetail(primaryError: Int): String = "SSL エラー (primaryError: $primaryError)"
}
