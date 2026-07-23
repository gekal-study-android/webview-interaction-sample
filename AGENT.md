# Agent Guide for WebView Interaction Sample

このファイルは、AI エージェントがこのプロジェクトを理解し、効率的に開発を支援するためのガイドラインです。

## プロジェクト概要
Android の `WebView` と JavaScript 間の相互作用をデモンストレーションするサンプルプロジェクトです。

## 技術スタック
- **Android**: Kotlin, Jetpack Compose
- **WebView**: `androidx.compose.ui:ui-viewinterop:AndroidView` を使用
- **Web Content**: Next.js (App Router / TypeScript) + MUI の静的エクスポート (GitHub Pages でホスト)
- **CI/CD**: GitHub Actions, Firebase App Distribution
- **依存関係管理**: Dependabot (`.github/dependabot.yml`) — GitHub Actions / npm (web, e2e) / Gradle を週次で更新
- **Testing**: Playwright (E2E)

## 重要ファイル
- `app/src/main/java/cn/gekal/android/myapplicationwebviewinteractionsample/MainActivity.kt`: WebView のセットアップとエラーハンドリング
- `app/src/main/java/cn/gekal/android/myapplicationwebviewinteractionsample/JavaScriptInterface.kt`: JS から呼び出されるネイティブメソッドの定義
- `web/app/bridge-provider.tsx`: JS ⇄ Native ブリッジの状態管理（呼び出し・受信・ログ）
- `web/app/components/`: デモ機能ごとの MUI カードコンポーネント
- `web/app/theme.ts`: MUI テーマ（ライト/ダークのカラースキーム定義）
- `web/next.config.ts` / `web/base-path.ts`: 静的エクスポート設定。カスタムドメインでルート配信のため `basePath` は空
- `web/public/.well-known/assetlinks.json`: Trusted Web Activity の検証用（署名証明書の SHA-256）
- `web/app/twa/`: TWA 判定用の専用ページ (`/twa.html`)。`display-mode: standalone` と `document.referrer` で判定
- `web/types/android.d.ts`: `AndroidInterface` / `handleReturnValue` / `onNativeEvent` の型定義
- `app/build.gradle.kts`: ビルド設定、BuildConfig 定義
- `app/configs/{debug|release}.json`: 各環境の WebView URL 設定

## WebView 相互作用の仕様
- **JavaScript Interface 名**: `AndroidInterface`
- **Native -> JS**: `webView.evaluateJavascript("javascript: functionName()", null)`
- **JS -> Native**: `window.AndroidInterface.methodName()`

### JS -> Native (`JavaScriptInterface.kt`)
| メソッド | 内容 |
| --- | --- |
| `showToast(message)` / `showToast(message, longDuration)` | トースト表示。応答として `handleReturnValue()` を呼び返す |
| `getDeviceInfo(): String` | 端末情報を JSON 文字列で同期的に返す |
| `getBatteryStatus(): String` | バッテリー残量・充電状態を JSON 文字列で同期的に返す |
| `vibrate(milliseconds)` | 端末を振動させる（`VIBRATE` 権限が必要） |
| `copyToClipboard(label, text)` | クリップボードにコピー |
| `shareText(text)` | 共有シート (ACTION_SEND) を開く |
| `requestNativeCallback(requestId, delayMillis)` | 遅延後に `onNativeEvent()` で非同期応答 |
| `openExternalLink(url, mode)` | 外部リンクを指定方式で開く（`mode` は `ExternalOpenMode` の名前） |
| `reloadPage()` | 表示中のページを再読み込み |
| `simulateLoadError()` | 到達できない URL を読み込み、エラー画面を再現（デモ用） |

### リンクの扱い (`MainActivity.shouldOverrideUrlLoading`)
判断は `LinkPolicy` に切り出してあり、JVM ユニットテストで検証している。

| リンク | 遷移先 |
| --- | --- |
| 配信元と同じホストの http(s) | WebView 内で読み込む |
| 別ホストの http(s) | Custom Tabs |
| `tel:` / `mailto:` / `sms:` / `geo:` など | 対応する端末のアプリ |

外部サイトの開き方は `ExternalOpenMode` で選べる。実装は `ExternalLinkOpener.kt`。

| mode | 仕組み | 性質 |
| --- | --- | --- |
| `IN_APP_OVERLAY` | 2 つ目の WebView（`InAppBrowser.kt`） | ブラウザに依存せず見た目が一定。`JavaScriptInterface` は注入しない。**実サービスのログインには使えない** |
| `CUSTOM_TAB` | `CustomTabsIntent` | ブラウザアプリが描画。アプリと同じタスク。OAuth 推奨 |
| `PARTIAL_CUSTOM_TAB` | `setInitialActivityHeightPx()` + `CustomTabsSession` | ボトムシート状に部分表示。**セッション必須**（無いと全画面）。高さは画面の 50% 以上、横向き・マルチウィンドウ・未対応ブラウザでは全画面 |
| `WARMED_CUSTOM_TAB` | `warmup()` + `mayLaunchUrl()` | 事前接続・先読みで表示が速い |
| `APP_LINK` | `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` | 対応アプリがあればアプリ、無ければ Custom Tabs |
| `BROWSER_CHOOSER` | `Intent.createChooser()` | 既定ブラウザに直行させない |
| `NEW_DOCUMENT` | `FLAG_ACTIVITY_NEW_DOCUMENT` | 「最近のアプリ」に別項目として並ぶ |
| `INTENT_URI` | `Intent.parseUri()` | **要防御**。`component` / `selector` を落とし `CATEGORY_BROWSABLE` を強制する |
| `TRUSTED_WEB_ACTIVITY` | `TrustedWebActivityIntentBuilder` | 専用ページ `/twa.html` を開く。`assetlinks.json` と manifest の `asset_statements` が対になって初めて URL バーが隠れる |

`AndroidManifest.xml` の `<queries>` は必須。Android 11 (API 30) 以降のパッケージ可視性制限により、
宣言がないと端末に Chrome があっても `CustomTabsClient.getPackageName()` が null を返し、
Custom Tabs のセッションが張れず部分表示も効かなくなる。

`window.open()` / `target="_blank"` は `shouldOverrideUrlLoading` を通らないため、
`setSupportMultipleWindows(true)` と `WebChromeClient.onCreateWindow` で受け取り同じ経路に流している。

### Native -> JS (グローバル関数)
| 関数 | 内容 |
| --- | --- |
| `handleReturnValue(value)` | ネイティブからの同期的な応答 |
| `onNativeEvent(json)` | `requestId` を含む JSON 文字列による非同期イベント |

## 開発コマンド
### Android ビルド & 実行
```bash
./gradlew assembleDebug
```

### コードスタイル適用 (Spotless)
```bash
./gradlew spotlessApply
```

### Web コンテンツ (Next.js)
```bash
cd web
pnpm install
pnpm dev    # 開発サーバー (http://localhost:3000)
pnpm build  # 静的エクスポート -> web/out/
```

### E2E テスト実行 (Playwright)
```bash
cd e2e
pnpm install
pnpm test
```

## エージェントへの指示事項
1. **Compose の使用**: UI は Jetpack Compose で記述してください。
2. **エラーハンドリング**: WebView のメインフレームでのエラー（HTTP/SSL）は `MainActivity.kt` の `WebViewClient` で捕捉し、ネイティブのエラー画面を表示する設計を維持してください。
3. **設定の分離**: URL などの環境依存値は `app/configs/*.json` を通じて `BuildConfig` に反映させる仕組みを利用してください。
4. **一貫性**: インデントやスタイルは `.editorconfig` および Spotless の設定に従ってください。
5. **Web の契約維持**: ネイティブは `handleReturnValue` / `onNativeEvent` をグローバル関数として呼び出すため、
   `bridge-provider.tsx` の `useEffect` で `window` に登録する構成を維持してください。
   また、ネイティブメソッドの追加時は `web/types/android.d.ts` と `KNOWN_METHODS` も更新してください。
6. **配信 URL**: `app/configs/*.json` の `webview_url` はカスタムドメイン
   `webview-interaction-sample.demo.gekal.cn` を指しています。ドメインを変える場合は
   `web/base-path.ts` / `web/public/CNAME` / `assetlinks.json` / `asset_statements` も揃えてください。
7. **静的エクスポート**: `web/` はサーバー機能（Route Handlers、SSR、`next/image` の最適化など）を
   使わず、`output: 'export'` でビルドできる状態を保ってください。GitHub Pages のプロジェクトサイト配信のため
   `basePath` の指定も必須です。
8. **ブラウザでの動作**: `AndroidInterface` が無い環境（ブラウザ・E2E）でも壊れないよう、
   ネイティブ呼び出しは必ず `callNative()` 経由で存在チェックを行ってください。
9. **ハイドレーション**: ネイティブを呼ぶボタンは `useBridge().hydrated` が true になるまで
   `disabled` にしてください（ハイドレーション前のクリックが無視されるのを防ぐため）。
