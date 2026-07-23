package cn.gekal.android.myapplicationwebviewinteractionsample

/** リンクをどこで開くか。 */
enum class Navigation {
  /** WebView 内でそのまま読み込む。 */
  IN_WEB_VIEW,

  /** 端末のアプリ（電話・メール・ブラウザなど）に渡す。 */
  EXTERNAL_APP,
}

/** 外部アプリに渡すときに使う Intent の種類。実際の action への変換は呼び出し側で行う。 */
enum class ExternalIntent {
  /** 電話番号を入れたダイヤル画面を開く（発信はしない）。 */
  DIAL,

  /** 宛先付きの作成画面を開く（メール / SMS）。 */
  SEND_TO,

  VIEW,
}

/**
 * リンクの遷移先を決めるロジック。
 *
 * Android SDK に依存しないため、端末やエミュレータなしの JVM ユニットテストで検証できる。
 */
object LinkPolicy {
  private val WEB_SCHEMES = setOf("http", "https")

  /**
   * @param scheme リンクのスキーム
   * @param host リンクのホスト
   * @param targetHost 配信元（`BuildConfig.WEBVIEW_URL`）のホスト
   *
   * 配信元と同じホストの http(s) だけ WebView 内で読み込む。
   * 外部サイトを WebView 内で開くと戻る手段がなく、デモページに戻れなくなるため。
   */
  fun resolve(scheme: String?, host: String?, targetHost: String?): Navigation {
    val isWebScheme = scheme?.lowercase() in WEB_SCHEMES
    val isSameHost = targetHost != null && host.equals(targetHost, ignoreCase = true)
    return if (isWebScheme && isSameHost) Navigation.IN_WEB_VIEW else Navigation.EXTERNAL_APP
  }

  fun externalIntentFor(scheme: String?): ExternalIntent = when (scheme?.lowercase()) {
    "tel" -> ExternalIntent.DIAL
    "mailto", "sms", "smsto" -> ExternalIntent.SEND_TO
    else -> ExternalIntent.VIEW
  }
}
