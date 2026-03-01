# Webview Interaction Sample

Android の WebView と JavaScript 間の相互作用をデモンストレーションするサンプルプロジェクトです。

## 特徴

- **JavaScript Interface**: Android と WebView 内の JavaScript 間で相互にメソッドを呼び出す方法。
- **エラーハンドリング**: WebView での HTTP エラー (404, 500) や SSL エラー時のネイティブエラー画面表示。
- **E2E テスト**: Playwright を使用した WebView の自動テスト（`e2e` ディレクトリ）。

## プロジェクト構造

- `app/`: Android アプリケーションのソースコード。
- `docs/`: GitHub Pages で公開されている Web コンテンツ。
- `e2e/`: Playwright を使用したエンドツーエンドテスト。

## Web 側 (GitHub Pages)

WebView が読み込むコンテンツは以下で公開されています。
- URL: <https://gekal-study-android.github.io/webview-interaction-sample/index.html>
- ソース: `/docs/index.html`

## 相互作用の仕組み

### Android から JavaScript を実行
`MainActivity.kt` で `webView.evaluateJavascript` を使用して JS 関数を呼び出します。

### JavaScript から Android を実行
`JavaScriptInterface.kt` で定義されたメソッドを、JS から `AndroidInterface.methodName()` の形式で呼び出します。

## E2E テストの実行

`e2e` ディレクトリに移動してテストを実行します。

```shell
cd e2e
pnpm install
pnpm test
```

## 署名 (リリースビルド用)

```shell
keytool -genkeypair \
  -v \
  -keystore debug.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias samle-sign-alias \
  -storepass 123456 \
  -keypass 123456 \
  -dname "CN=gekal, OU=Development, O=Gekal Inc., L=Chiba, ST=Chiba, C=JP"
```

## Firebase App Distribution へのアップロード

```shell
# 認証情報の設定
export GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/google-credentials.json

# ビルドとアップロード
./gradlew clean assembleRelease appDistributionUploadRelease
```
