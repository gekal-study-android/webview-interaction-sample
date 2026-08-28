# Webview Interaction Sample

Android の WebView と JavaScript 間の相互作用をデモンストレーションするサンプルプロジェクトです。

## 特徴

- **JavaScript Interface**: Android と WebView 内の JavaScript 間で相互にメソッドを呼び出す方法。
- **エラーハンドリング**: WebView での HTTP エラー (404, 500) や SSL エラー時のネイティブエラー画面表示。
- **E2E テスト**: ブラウザ上での Playwright（`e2e` ディレクトリ）と、実機でアプリごと動かす計装テスト（`app/src/androidTest`）。
- **デモ画面**: Next.js + MUI で構築した、ブリッジの動作を一覧できるデモ UI（`web` ディレクトリ）。

### デモ機能

| 機能 | 方向 | 内容 |
| --- | --- | --- |
| トースト表示 | JS → Native → JS | メッセージと表示時間を指定して `showToast()` を呼び、`handleReturnValue()` の応答を表示 |
| 非同期コールバック | JS → Native → JS | 遅延を指定して `requestNativeCallback()` を呼び、`onNativeEvent()` の応答を待機 |
| 端末情報 | JS → Native | `getDeviceInfo()` / `getBatteryStatus()` の戻り値（JSON）を表示 |
| 端末機能の呼び出し | JS → Native | バイブレーション・クリップボードへのコピー・共有シート |
| 実行環境 | Web | ブリッジの検出状況、利用可能なメソッド、User Agent、`env` クエリなど |
| イベントログ | - | JS ⇄ Native のやり取りを時系列で記録 |

ブラウザで直接開いた場合は `AndroidInterface` が存在しないため、ネイティブ呼び出しは実行されずログにのみ記録されます。

### デバッグ (vConsole)

WebView 上でログとネットワーク通信を確認できるよう [vConsole](https://github.com/Tencent/vConsole) を組み込んでいます。
有効・無効は **Web のビルド時フラグ** `NEXT_PUBLIC_VCONSOLE` で決まります（`true` / `1` で有効、既定は無効）。

```shell
cd web
NEXT_PUBLIC_VCONSOLE=true pnpm build   # vConsole 有効でビルド
```

GitHub Actions では、リポジトリ変数 `NEXT_PUBLIC_VCONSOLE` を `true` にすると公開サイトで有効になります。
デプロイ済みのページを手元で確認したいときは、URL に `?vconsole=1`（無効化は `?vconsole=0`）を付けて上書きできます。

## プロジェクト構造

- `app/`: Android アプリケーションのソースコード。
- `web/`: GitHub Pages で公開する Web コンテンツ（Next.js 静的エクスポート + MUI）。
- `e2e/`: Playwright を使用したエンドツーエンドテスト（ブラウザ上でブリッジをモック）。
- `app/src/androidTest/`: 実機でアプリを起動して行うエンドツーエンドテスト。
- `scripts/`: 開発用スクリプト（テスト実行など）。

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

`app/google-services.json` は `.gitignore` 済みで、リポジトリには入っていません。
無い状態でビルドすると次のエラーで失敗します。

```
File google-services.json is missing. The Google Services Plugin cannot function without it.
```

Firebase CLI の `apps:sdkconfig` で取り直します。`-o` を付けると余計なログが混ざらず、
そのままファイルに書き出せます（`>` でのリダイレクトは進捗ログが混ざることがあります）。

```shell
firebase login

# Android アプリの App ID を確認する
firebase apps:list ANDROID --project webview-interaction-sample

# 設定を取得して配置する（App ID は上のコマンドの出力に合わせる）
firebase apps:sdkconfig ANDROID 1:533859828379:android:394d68aacbb28c553a3c3e \
  --project webview-interaction-sample \
  -o app/google-services.json
```

取得できたら、`applicationId` と一致しているかだけ確認してからビルドします。

```shell
# package_name が cn.gekal.android.myapplicationwebviewinteractionsample であること
python3 -c "import json;print(json.load(open('app/google-services.json'))['client'][0]['client_info']['android_client_info']['package_name'])"

./gradlew :app:assembleDebug
```

| 項目 | 値 |
| --- | --- |
| プロジェクト ID | `webview-interaction-sample` |
| プロジェクト番号 | `533859828379` |
| Android App ID | `1:533859828379:android:394d68aacbb28c553a3c3e` |
| applicationId | `cn.gekal.android.myapplicationwebviewinteractionsample` |

CLI が使えない場合は、Firebase コンソール（プロジェクトの設定 → マイアプリ）から
`google-services.json` をダウンロードして `app/` に置いても同じです。

なお GitHub Actions ではこのファイルを取得せず、`.github/workflows/android.yml` が
ダミーの `google-services.json` を生成しています（デバッグビルドは Firebase に接続しないため）。
リリースワークフローだけがシークレットから実物を書き出します。

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

WebView が読み込むコンテンツは Next.js の静的エクスポート (`output: 'export'`) で生成し、GitHub Actions から GitHub Pages へデプロイしています。

- URL: <https://webview-interaction-sample.android.demo.gekal.cn/index.html>（カスタムドメイン。ルート配信）
- ソース: `web/`（App Router + TypeScript + MUI）
- デプロイ: `.github/workflows/pages.yml`（`main` への push で自動デプロイ / PR ではビルド検証のみ）

カスタムドメインでルート配信しているため `basePath` は空です（`web/base-path.ts`）。
`<user>.github.io/<repo>/` 形式に戻す場合は `BASE_PATH=/webview-interaction-sample` を指定してビルドしてください。

`web/public/CNAME` と `web/public/.well-known/assetlinks.json` は成果物にそのまま含まれます。
後者は Trusted Web Activity の検証に使います（[詳細](web/public/.well-known/README.md)）。

### ローカルでの開発

```shell
cd web
pnpm install
pnpm dev    # http://localhost:3000
```

### 静的エクスポートのビルド

```shell
cd web
pnpm build  # web/out/ に出力される
```

## 相互作用の仕組み

### Android から JavaScript を実行
`MainActivity.kt` で `webView.evaluateJavascript` を使用して JS 関数を呼び出します。

### JavaScript から Android を実行
`JavaScriptInterface.kt` で定義されたメソッドを、JS から `AndroidInterface.methodName()` の形式で呼び出します。

## E2E テストの実行

同じデモページに対して、2 段構えでテストしています。

| | 対象 | 実行環境 | 分かること |
| --- | --- | --- | --- |
| Playwright (`e2e/`) | Web のロジック | ブラウザ（CI で自動実行） | 画面の表示・ネイティブに渡す引数。`AndroidInterface` はモック |
| 計装テスト (`app/src/androidTest/`) | ブリッジの往復 | 実機（手動実行） | 本物の `JavaScriptInterface` の戻り値、ネイティブ画面、設定の保存 |

### ブラウザ (Playwright)

```shell
cd e2e
pnpm install
pnpm test
```

### 実機 (計装テスト)

接続中の実機でアプリを起動し、配信中のデモページを読み込んで検証します。
端末側にネットワーク接続が必要です（`BuildConfig.WEBVIEW_URL` を読み込むため）。

```shell
scripts/test.sh e2e                 # 実機での E2E のみ
scripts/test.sh all                 # JVM ユニットテスト + 実機での E2E
scripts/test.sh e2e --class cn.gekal.android.myapplicationwebviewinteractionsample.WebViewBridgeE2eTest
```

実行が終わると、結果・画面キャプチャ・logcat を 1 枚にまとめた HTML を自動生成します。
テストが失敗したときも生成します（失敗時こそキャプチャとログが要るため）。

```
app/build/reports/e2e/index.html
```

| 中身 | 内容 |
| --- | --- |
| 結果の一覧 | テストごとの成否と所要時間。失敗があれば先頭に並べる |
| 画面キャプチャ | テストの節目ごとの画面。クリックで原寸表示 |
| logcat | テスト単位で、ブリッジのやり取り・ライフサイクル・失敗だけを抜粋（全文へのリンク付き） |

レポートだけを作り直したいときは、テストを流し直さずに次を実行します。

```shell
scripts/e2e-report.py            # 直近の実行結果から再生成
```

元データの出力先:

| 種類 | パス |
| --- | --- |
| Gradle の HTML レポート | `app/build/reports/androidTests/connected/debug/index.html` |
| 画面キャプチャ | `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<device>/` |
| logcat（テスト単位） | `app/build/outputs/androidTest-results/connected/debug/<device>/logcat-*.txt` |

画面キャプチャは `ScreenshotRule` が各テストの節目で撮り、失敗時は自動で 1 枚残します。
実行中は端末の画面を点灯したままにします（消灯するとロック画面が写り、待ち合わせも不安定になるため）。
設定は終了時に元へ戻します。

実機が要るため GitHub Actions では実行していません（CI はユニットテストと Playwright まで）。

| テスト | 内容 |
| --- | --- |
| `WebViewBridgeE2eTest` | ブリッジの注入、`showToast()` の往復、実機の端末情報、非同期コールバック、配色のネイティブ保存、`reloadPage()` |
| `NativeErrorScreenE2eTest` | `simulateLoadError()` のエラー画面の表示と、再試行での復帰 |
| `InAppOverlayE2eTest` | アプリ内オーバーレイの表示と、戻る操作で閉じてデモ画面に復帰すること |

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
