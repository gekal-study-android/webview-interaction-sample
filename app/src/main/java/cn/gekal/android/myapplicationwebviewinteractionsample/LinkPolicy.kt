package cn.gekal.android.myapplicationwebviewinteractionsample

/** リンクをどこで開くか。 */
enum class Navigation {
  /** WebView 内でそのまま読み込む。 */
  IN_WEB_VIEW,

  /**
   * Custom Tabs（アプリ内ブラウザ）でアプリの上に重ねて開く。
   * URL バーが出るので外部サイトだと分かり、閉じれば元の画面に戻れる。
   */
  CUSTOM_TAB,

  /** 端末のアプリ（電話・メール・地図など）に渡す。 */
  EXTERNAL_APP,
}

/** 外部サイトの開き方。WebView 側から明示的に指定できるようにしている。 */
enum class ExternalOpenMode {
  /** アプリ内オーバーレイ（2 つ目の WebView）。ブラウザに依存せず見た目が一定。 */
  IN_APP_OVERLAY,

  /** Custom Tabs（全画面）。ブラウザアプリが描画するため、実サービスのログインでも使える。 */
  CUSTOM_TAB,

  /** Custom Tabs をボトムシート状に部分表示する。下にアプリが見えたまま重なる。 */
  PARTIAL_CUSTOM_TAB,

  /** 事前に接続とウォームアップをしてから開く Custom Tabs。表示が速くなる。 */
  WARMED_CUSTOM_TAB,

  /** 対応アプリがあればそちらで開き、無ければ Custom Tabs に落とす（App Links）。 */
  APP_LINK,

  /** 既定のブラウザに直行させず、ユーザーにアプリを選ばせる。 */
  BROWSER_CHOOSER,

  /** 「最近のアプリ」に別項目として並べて開く。 */
  NEW_DOCUMENT,

  /** `intent://` URI からアプリを起動する。任意のコンポーネントを起動させないよう要防御。 */
  INTENT_URI,

  /** Trusted Web Activity。所有を検証したドメインなら URL バーなしの全画面になる。 */
  TRUSTED_WEB_ACTIVITY,
  ;

  companion object {
    /** WebView から渡される文字列を変換する。未知の値は最も無難な [CUSTOM_TAB] にする。 */
    fun from(value: String?): ExternalOpenMode =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CUSTOM_TAB
  }
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
   * 外部サイトを WebView 内で開くと、URL が見えないまま別サイトを表示することになり、
   * 戻る手段もない。Custom Tabs なら URL バーで接続先が分かり、閉じれば元の画面に戻れる。
   */
  fun resolve(scheme: String?, host: String?, targetHost: String?): Navigation {
    if (scheme?.lowercase() !in WEB_SCHEMES) {
      return Navigation.EXTERNAL_APP
    }
    val isSameHost = targetHost != null && host.equals(targetHost, ignoreCase = true)
    return if (isSameHost) Navigation.IN_WEB_VIEW else Navigation.CUSTOM_TAB
  }

  /**
   * WebView から渡された URL をブラウザ表示に使ってよいか。
   * `javascript:` や `file:` などを開かせないよう、http(s) だけを許可する。
   */
  fun isBrowsableUrl(scheme: String?): Boolean = scheme?.lowercase() in WEB_SCHEMES

  /**
   * 開き方ごとに許可するスキーム。
   * [ExternalOpenMode.INTENT_URI] だけは `intent:` を受け取る必要がある。
   */
  fun isAllowedFor(mode: ExternalOpenMode, scheme: String?): Boolean =
    if (mode == ExternalOpenMode.INTENT_URI) {
      scheme?.lowercase() == "intent"
    } else {
      isBrowsableUrl(scheme)
    }

  fun externalIntentFor(scheme: String?): ExternalIntent = when (scheme?.lowercase()) {
    "tel" -> ExternalIntent.DIAL
    "mailto", "sms", "smsto" -> ExternalIntent.SEND_TO
    else -> ExternalIntent.VIEW
  }
}
