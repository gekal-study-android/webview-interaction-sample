# Webview 交互サンプル

## Github Pages で Webview 公開

/docs/index.html

<https://gekal-study-android.github.io/webview-interaction-sample/index.html>

## 署名

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

## App Distribution

```shell
export GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/google-credentials.json

./gradlew clean assembleRelease appDistributionUploadRelease

./gradlew appDistributionUploadRelease
```
