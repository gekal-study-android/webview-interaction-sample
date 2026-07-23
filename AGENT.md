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
- `web/next.config.ts`: 静的エクスポート設定 (`output: 'export'`, `basePath`)
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
| `openInAppBrowser(url)` | 外部サイトをアプリ内オーバーレイ（2 つ目の WebView）で開く |
| `openInCustomTab(url)` | 外部サイトを Custom Tabs で開く |
| `reloadPage()` | 表示中のページを再読み込み |
| `simulateLoadError()` | 到達できない URL を読み込み、エラー画面を再現（デモ用） |

### リンクの扱い (`MainActivity.shouldOverrideUrlLoading`)
判断は `LinkPolicy` に切り出してあり、JVM ユニットテストで検証している。

| リンク | 遷移先 |
| --- | --- |
| 配信元と同じホストの http(s) | WebView 内で読み込む |
| 別ホストの http(s) | Custom Tabs |
| `tel:` / `mailto:` / `sms:` / `geo:` など | 対応する端末のアプリ |

外部サイトの表示方法は 2 つ用意してあり、デモから選べる。

| 方式 | 実装 | 性質 |
| --- | --- | --- |
| アプリ内オーバーレイ | `InAppBrowser.kt`（2 つ目の WebView + Compose） | ブラウザに依存せず見た目が一定。`JavaScriptInterface` は注入しない。**実サービスのログインには使えない** |
| Custom Tabs | `MainActivity.openInCustomTab` | 描画するのはブラウザアプリ本体で、アプリと同じタスクに開く。OAuth ではこちらが推奨。対応ブラウザがない端末では通常のブラウザ起動に静かに退化する |

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
pnpm dev    # 開発サーバー (http://localhost:3000/webview-interaction-sample)
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
6. **静的エクスポート**: `web/` はサーバー機能（Route Handlers、SSR、`next/image` の最適化など）を
   使わず、`output: 'export'` でビルドできる状態を保ってください。GitHub Pages のプロジェクトサイト配信のため
   `basePath` の指定も必須です。
7. **ブラウザでの動作**: `AndroidInterface` が無い環境（ブラウザ・E2E）でも壊れないよう、
   ネイティブ呼び出しは必ず `callNative()` 経由で存在チェックを行ってください。
8. **ハイドレーション**: ネイティブを呼ぶボタンは `useBridge().hydrated` が true になるまで
   `disabled` にしてください（ハイドレーション前のクリックが無視されるのを防ぐため）。
