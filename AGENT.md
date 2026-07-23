# Agent Guide for WebView Interaction Sample

このファイルは、AI エージェントがこのプロジェクトを理解し、効率的に開発を支援するためのガイドラインです。

## プロジェクト概要
Android の `WebView` と JavaScript 間の相互作用をデモンストレーションするサンプルプロジェクトです。

## 技術スタック
- **Android**: Kotlin, Jetpack Compose
- **WebView**: `androidx.compose.ui:ui-viewinterop:AndroidView` を使用
- **Web Content**: Next.js (App Router / TypeScript) の静的エクスポート (GitHub Pages でホスト)
- **CI/CD**: GitHub Actions, Firebase App Distribution
- **Testing**: Playwright (E2E)

## 重要ファイル
- `app/src/main/java/cn/gekal/android/myapplicationwebviewinteractionsample/MainActivity.kt`: WebView のセットアップとエラーハンドリング
- `app/src/main/java/cn/gekal/android/myapplicationwebviewinteractionsample/JavaScriptInterface.kt`: JS から呼び出されるネイティブメソッドの定義
- `web/app/page.tsx` / `web/app/webview-demo.tsx`: WebView に表示される Web コンテンツ
- `web/next.config.ts`: 静的エクスポート設定 (`output: 'export'`, `basePath`)
- `web/types/android.d.ts`: `AndroidInterface` / `handleReturnValue` の型定義
- `app/build.gradle.kts`: ビルド設定、BuildConfig 定義
- `app/configs/{debug|release}.json`: 各環境の WebView URL 設定

## WebView 相互作用の仕様
- **JavaScript Interface 名**: `AndroidInterface`
- **Native -> JS**: `webView.evaluateJavascript("javascript: functionName()", null)`
- **JS -> Native**: `window.AndroidInterface.methodName()`

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
5. **Web の契約維持**: ネイティブは `handleReturnValue` をグローバル関数として呼び出すため、
   クライアントコンポーネントの `useEffect` で `window.handleReturnValue` に登録する構成を維持してください。
6. **静的エクスポート**: `web/` はサーバー機能（Route Handlers、SSR、`next/image` の最適化など）を
   使わず、`output: 'export'` でビルドできる状態を保ってください。GitHub Pages のプロジェクトサイト配信のため
   `basePath` の指定も必須です。
