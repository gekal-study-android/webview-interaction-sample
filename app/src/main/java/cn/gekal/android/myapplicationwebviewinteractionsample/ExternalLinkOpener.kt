package cn.gekal.android.myapplicationwebviewinteractionsample

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import androidx.core.net.toUri

private const val TAG = "ExternalLinkOpener"

/** Custom Tabs の見た目。アプリの配色に合わせるために渡す。 */
data class CustomTabStyle(val toolbarColor: Int, val partialHeightPx: Int)

/**
 * 外部リンクの開き方をまとめたもの。
 *
 * [ExternalOpenMode] ごとに Android の別々の仕組みを使う。
 * どれを選ぶかの判断は [LinkPolicy] にあり、ここは実際に起動するだけ。
 */
fun openExternalLink(context: Context, url: String, mode: ExternalOpenMode, style: CustomTabStyle) {
  val uri = url.toUri()

  if (!LinkPolicy.isAllowedFor(mode, uri.scheme)) {
    Log.w(TAG, "$mode では扱えない URL のため無視します: $url")
    return
  }

  when (mode) {
    // オーバーレイは Compose 側の状態で表示するため、ここには来ない
    ExternalOpenMode.IN_APP_OVERLAY -> Unit

    ExternalOpenMode.CUSTOM_TAB -> launchCustomTab(context, uri, style, partial = false)

    ExternalOpenMode.PARTIAL_CUSTOM_TAB -> launchPartialCustomTab(context, uri, style)

    ExternalOpenMode.WARMED_CUSTOM_TAB -> launchWarmedCustomTab(context, uri, style)

    ExternalOpenMode.APP_LINK -> launchAppLink(context, uri, style)

    ExternalOpenMode.BROWSER_CHOOSER -> launchWithChooser(context, uri)

    ExternalOpenMode.NEW_DOCUMENT -> launchAsNewDocument(context, uri)

    ExternalOpenMode.INTENT_URI -> launchIntentUri(context, url)

    ExternalOpenMode.TRUSTED_WEB_ACTIVITY -> launchTrustedWebActivity(context, uri, style)
  }
}

/**
 * Custom Tabs で開く。
 *
 * [partial] が true のときはボトムシート状に部分表示する（下にアプリが見えたまま重なる）。
 * 描画するのはブラウザアプリ本体で、対応ブラウザがない端末では通常のブラウザ起動に静かに退化する。
 */
private fun launchCustomTab(
  context: Context,
  uri: Uri,
  style: CustomTabStyle,
  partial: Boolean,
  session: CustomTabsSession? = null,
) {
  warnIfNoProvider(context, uri)

  val builder = session?.let { CustomTabsIntent.Builder(it) } ?: CustomTabsIntent.Builder()
  builder
    .setDefaultColorSchemeParams(
      CustomTabColorSchemeParams.Builder().setToolbarColor(style.toolbarColor).build(),
    )
    .setShowTitle(true)
    .setUrlBarHidingEnabled(false)

  if (partial) {
    builder
      .setInitialActivityHeightPx(style.partialHeightPx)
      .setToolbarCornerRadiusDp(PARTIAL_CORNER_RADIUS_DP)
  }

  try {
    // Activity 以外の Context だと別タスクで開いてしまい、戻る導線がなくなる
    builder.build().launchUrl(context.findActivity() ?: context, uri)
  } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "Custom Tabs を開けないためブラウザにフォールバックします: $uri", e)
    openWithExternalApp(context, uri)
  }
}

/**
 * ボトムシート状の部分表示で開く。
 *
 * **[CustomTabsSession] を渡さないと部分表示にならず全画面になる。**
 * 公式の要件は「`CustomTabsServiceConnection` で張ったセッションを渡す」か
 * 「`startActivityForResult()` で起動する」のどちらかで、ここでは前者を使う。
 *
 * このほか、以下の場合も全画面に落ちる。
 * - 既定のブラウザが部分表示に対応していない（エクストラが無視される）
 * - 指定した高さが画面の 50% 未満（Chrome が 50% に引き上げる）
 * - 横向きやマルチウィンドウ
 */
private fun launchPartialCustomTab(context: Context, uri: Uri, style: CustomTabStyle) {
  CustomTabsSessionHolder.withSession(context) { session ->
    if (session == null) {
      Log.w(TAG, "セッションを取得できないため部分表示にならず全画面で開きます: $uri")
      Toast.makeText(
        context,
        "ブラウザのセッションを取得できないため、全画面で開きます",
        Toast.LENGTH_LONG,
      ).show()
    }
    launchCustomTab(context, uri, style, partial = true, session = session)
  }
}

/** 事前に接続・ウォームアップしてから Custom Tabs を開く。表示までの待ちが短くなる。 */
private fun launchWarmedCustomTab(context: Context, uri: Uri, style: CustomTabStyle) {
  CustomTabsSessionHolder.withSession(context) { session ->
    session?.mayLaunchUrl(uri, null, null)
    launchCustomTab(context, uri, style, partial = false, session = session)
  }
}

/**
 * 対応アプリがあればそのアプリで開く（App Links）。
 *
 * `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` はブラウザ以外が処理できないときに例外を投げる。
 * それを捕まえて Custom Tabs に落とすことで、「アプリがあればアプリ、無ければブラウザ」になる。
 */
private fun launchAppLink(context: Context, uri: Uri, style: CustomTabStyle) {
  val intent = Intent(Intent.ACTION_VIEW, uri)
    .addCategory(Intent.CATEGORY_BROWSABLE)
    .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or Intent.FLAG_ACTIVITY_NEW_TASK)

  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    Log.i(TAG, "このリンクを開けるアプリがないため Custom Tabs で開きます: $uri", e)
    launchCustomTab(context, uri, style, partial = false)
  }
}

/** 既定のブラウザに直行させず、ユーザーにアプリを選ばせる。 */
private fun launchWithChooser(context: Context, uri: Uri) {
  val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "リンクを開くアプリを選択")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  startActivitySafely(context, chooser, uri)
}

/** 「最近のアプリ」にこのアプリとは別の項目として並べて開く。 */
private fun launchAsNewDocument(context: Context, uri: Uri) {
  val intent = Intent(Intent.ACTION_VIEW, uri)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK)
  startActivitySafely(context, intent, uri)
}

/**
 * `intent://` URI からアプリを起動する。
 *
 * そのまま起動すると **Web ページから任意のコンポーネントを呼び出せてしまう**ため、
 * `component` と `selector` を落とし、`CATEGORY_BROWSABLE` を必須にして
 * 「ブラウザから開かれてよい入口」に限定する。
 */
private fun launchIntentUri(context: Context, url: String) {
  val intent = try {
    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
  } catch (e: java.net.URISyntaxException) {
    Log.w(TAG, "intent URI を解析できません: $url", e)
    return
  }

  intent.addCategory(Intent.CATEGORY_BROWSABLE)
  intent.component = null
  intent.selector = null
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    // intent:// には S.browser_fallback_url でフォールバック先を書ける
    val fallback = intent.getStringExtra("browser_fallback_url")
    if (fallback != null) {
      Log.i(TAG, "対応アプリがないため fallback URL を開きます: $fallback", e)
      openWithExternalApp(context, fallback.toUri())
    } else {
      Log.w(TAG, "この intent URI を開けるアプリがありません: $url", e)
      Toast.makeText(context, "対応するアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
    }
  }
}

/**
 * Trusted Web Activity で開く。
 *
 * Digital Asset Links（対象ドメインの `/.well-known/assetlinks.json`）でアプリとの
 * 関連付けを検証できたときだけ URL バーなしの全画面になる。
 * 検証できない場合はブラウザ側の判断で通常の Custom Tabs 表示に落ちる。
 */
private fun launchTrustedWebActivity(context: Context, uri: Uri, style: CustomTabStyle) {
  CustomTabsSessionHolder.withSession(context) { session ->
    if (session == null) {
      Log.w(TAG, "Custom Tabs のセッションを取れないため Custom Tabs で開きます: $uri")
      launchCustomTab(context, uri, style, partial = false)
      return@withSession
    }

    try {
      TrustedWebActivityIntentBuilder(uri)
        .setDefaultColorSchemeParams(
          CustomTabColorSchemeParams.Builder().setToolbarColor(style.toolbarColor).build(),
        )
        .build(session)
        .launchTrustedWebActivity(context.findActivity() ?: context)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "TWA を開けないため Custom Tabs で開きます: $uri", e)
      launchCustomTab(context, uri, style, partial = false)
    }
  }
}

/**
 * `tel:` / `mailto:` などの URI を対応するアプリで開く。
 *
 * 電話は [Intent.ACTION_DIAL]（ダイヤル画面を開くだけ）を使う。
 * [Intent.ACTION_CALL] は即座に発信してしまい `CALL_PHONE` 権限も必要になるため使わない。
 */
fun openWithExternalApp(context: Context, uri: Uri) {
  val action = when (LinkPolicy.externalIntentFor(uri.scheme)) {
    ExternalIntent.DIAL -> Intent.ACTION_DIAL
    ExternalIntent.SEND_TO -> Intent.ACTION_SENDTO
    ExternalIntent.VIEW -> Intent.ACTION_VIEW
  }
  startActivitySafely(context, Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), uri)
}

private fun startActivitySafely(context: Context, intent: Intent, uri: Uri) {
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "この URI を開けるアプリがありません: $uri", e)
    Toast.makeText(context, "対応するアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
  }
}

/**
 * Custom Tabs を描画するのはブラウザアプリ本体。対応ブラウザがない端末では
 * `CustomTabsIntent` のエクストラが無視され、ただのブラウザ起動に「静かに」なる。
 * 何が起きたか分かるよう、その場合はログとトーストで知らせる。
 */
private fun warnIfNoProvider(context: Context, uri: Uri) {
  if (CustomTabsClient.getPackageName(context, null) == null) {
    Log.w(TAG, "Custom Tabs 対応ブラウザが見つかりません。通常のブラウザで開きます: $uri")
    Toast.makeText(
      context,
      "Custom Tabs 対応ブラウザがないため、通常のブラウザで開きます",
      Toast.LENGTH_LONG,
    ).show()
  }
}

/**
 * Custom Tabs サービスへの接続を 1 つだけ保持する。
 *
 * ウォームアップと TWA はどちらも [CustomTabsSession] を必要とし、その取得は非同期。
 * デモでは接続を張りっぱなしにしている（プロセスに 1 つだけ）。
 * 実アプリでは画面のライフサイクルに合わせて unbind すること。
 */
private object CustomTabsSessionHolder {
  private var session: CustomTabsSession? = null
  private var connecting = false
  private val pending = mutableListOf<(CustomTabsSession?) -> Unit>()

  fun withSession(context: Context, block: (CustomTabsSession?) -> Unit) {
    session?.let {
      block(it)
      return
    }

    val provider = CustomTabsClient.getPackageName(context, null)
    if (provider == null) {
      block(null)
      return
    }

    pending += block
    if (connecting) return
    connecting = true

    val connection = object : CustomTabsServiceConnection() {
      override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
        client.warmup(0)
        session = client.newSession(null)
        connecting = false
        pending.forEach { it(session) }
        pending.clear()
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        session = null
        connecting = false
      }
    }

    // Activity のリークを避けるため application context で bind する
    if (!CustomTabsClient.bindCustomTabsService(context.applicationContext, provider, connection)) {
      connecting = false
      pending.forEach { it(null) }
      pending.clear()
    }
  }
}

/** [ContextWrapper] をたどって Activity を取り出す。 */
tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
  is ComponentActivity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

private const val PARTIAL_CORNER_RADIUS_DP = 16
