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

## ローカル開発の準備

ビルドには Firebase の設定ファイルが必要です。Firebase CLI を使用して最新の設定を取得できます。

### 必要なファイルの取得と配置

#### 1. Firebase CLI のセットアップ
Firebase CLI がインストールされていない場合は、以下のコマンドでインストールし、ログインしてください。

```shell
curl -sL https://firebase.tools | bash
firebase login
```

#### 2. google-services.json の取得
Firebase CLI の `apps:sdkconfig` コマンドを使用して、Android アプリの設定を取得します。

```shell
# プロジェクト ID を指定して設定を表示
# アプリケーション ID: cn.gekal.android.myapplicationwebviewinteractionsample
firebase apps:sdkconfig ANDROID --project webview-interaction-sample > app/google-services.json
```

※ `<YOUR_PROJECT_ID>` は Firebase コンソールで確認できるプロジェクト ID に置き換えてください。出力された内容が JSON 形式でない場合は、手動で整形するか Firebase コンソールから直接ダウンロードしてください。

#### 3. google-credentials.json (Firebase App Distribution 用)
このファイルは Google Cloud のサービスアカウントキーです。セキュリティ上の理由から CLI で直接「取得」はできませんが、新しく作成することは可能です。

```shell
# サービスアカウントキーの作成（権限がある場合）
gcloud iam service-accounts keys create google-credentials.json \
    --iam-account=firebase-app-distribution@<YOUR_PROJECT_ID>.iam.gserviceaccount.com
```

### 設定状況の確認 (GitHub)
GitHub Actions で使用されているシークレットや変数の名前を確認するには、GitHub CLI (`gh`) を使用します。

```shell
# Secrets の一覧を確認 (値の取得は不可)
gh secret list --repo gekal-study-android/webview-interaction-sample --env application

# Variables の一覧を確認
gh variable list --repo gekal-study-android/webview-interaction-sample --env application
```

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
