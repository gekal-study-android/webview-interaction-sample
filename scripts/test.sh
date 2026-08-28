#!/usr/bin/env bash
#
# テストを実行する。
#
# 使い方:
#   scripts/test.sh [unit|e2e|all] [<serial>] [--class <FQCN>]
#
# 例:
#   scripts/test.sh                    # JVM ユニットテストのみ
#   scripts/test.sh e2e                # 実機での E2E（計装テスト）のみ
#   scripts/test.sh all                # ユニット + E2E
#   scripts/test.sh e2e <serial>       # 端末シリアルを明示指定
#   scripts/test.sh e2e --class cn.gekal.android.myapplicationwebviewinteractionsample.WebViewBridgeE2eTest
#
# E2E は接続中の実機でアプリを起動し、配信中のデモページ (BuildConfig.WEBVIEW_URL) を
# 読み込むため、端末側にネットワーク接続が必要。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

SCOPE="unit"
SERIAL=""
TEST_CLASS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    unit|e2e|all)
      SCOPE="$1"
      shift
      ;;
    --class)
      TEST_CLASS="${2:-}"
      if [[ -z "$TEST_CLASS" ]]; then
        echo "❌ --class にはテストクラスの完全修飾名を指定してください。" >&2
        exit 1
      fi
      shift 2
      ;;
    -h|--help)
      sed -n '2,17p' "$SELF" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*)
      echo "❌ 不明なオプション: $1" >&2
      exit 1
      ;;
    *)
      SERIAL="$1"
      shift
      ;;
  esac
done

if [[ ! -x "./gradlew" ]]; then
  echo "❌ ./gradlew が見つかりません（または実行権限がありません）。" >&2
  echo "   権限のみの問題なら: chmod +x ./gradlew" >&2
  exit 1
fi

TASKS=()
[[ "$SCOPE" == "unit" || "$SCOPE" == "all" ]] && TASKS+=(testDebugUnitTest)

if [[ "$SCOPE" == "e2e" || "$SCOPE" == "all" ]]; then
  # ── adb の解決 ────────────────────────────────────────────────────────
  ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
  [[ -x "$ADB" ]] || ADB="$(command -v adb || true)"
  if [[ ! -x "${ADB:-}" ]]; then
    echo "❌ adb が見つかりません。Android SDK platform-tools を PATH に追加するか、" >&2
    echo "   ANDROID_HOME を設定してください。" >&2
    exit 1
  fi

  # ── 対象端末の決定 ────────────────────────────────────────────────────
  # E2E は実機での挙動（WebView の実装差・実際の通信）を見るためのものなので、
  # 明示指定がない限りエミュレータは選ばない。
  if [[ -z "$SERIAL" ]]; then
    DEVICES="$("$ADB" devices | awk '$2=="device" && $1 !~ /^emulator-/ {print $1}')"
    COUNT="$(printf '%s\n' "$DEVICES" | grep -c . || true)"
    if [[ "$COUNT" -eq 0 ]]; then
      echo "❌ 接続中の実機が見つかりません。USB デバッグを有効にして接続してください。" >&2
      "$ADB" devices >&2
      exit 1
    elif [[ "$COUNT" -gt 1 ]]; then
      echo "❌ 実機が複数あります。--help を参照し、シリアルを指定してください:" >&2
      printf '%s\n' "$DEVICES" >&2
      exit 1
    fi
    SERIAL="$(printf '%s\n' "$DEVICES" | head -n1)"
  fi
  echo "▶ 対象端末: $SERIAL"

  # 端末がネットワークに繋がっていないと、デモページの読み込み待ちで必ず失敗する
  if ! "$ADB" -s "$SERIAL" shell settings get global airplane_mode_on | grep -q '^0'; then
    echo "⚠️ 端末が機内モードの可能性があります。E2E は配信中のページを読み込みます。" >&2
  fi

  # ── 実行中だけ画面を点けておく ──────────────────────────────────────────
  # 途中で画面が消えるとロック画面が写り込み、画面キャプチャが当てにならなくなる。
  # 元の設定は終了時に必ず戻す。
  STAY_ON_BEFORE="$("$ADB" -s "$SERIAL" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
  restore_stay_on() {
    if [[ "$STAY_ON_BEFORE" =~ ^[0-9]+$ ]]; then
      "$ADB" -s "$SERIAL" shell settings put global stay_on_while_plugged_in "$STAY_ON_BEFORE" || true
      echo "▶ 画面の点灯設定を戻しました: $STAY_ON_BEFORE"
    fi
  }
  trap restore_stay_on EXIT
  "$ADB" -s "$SERIAL" shell svc power stayon true
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
  "$ADB" -s "$SERIAL" shell wm dismiss-keyguard || true

  export ANDROID_SERIAL="$SERIAL"
  TASKS+=(connectedDebugAndroidTest)
fi

GRADLE_ARGS=()
if [[ -n "$TEST_CLASS" ]]; then
  GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS")
fi

echo "▶ テストを実行します ($SCOPE): ./gradlew ${TASKS[*]} ${GRADLE_ARGS[*]:-}"
# 失敗しても結果とキャプチャからレポートを作りたいので、ここでは終了させない
set +e
./gradlew "${TASKS[@]}" ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}
GRADLE_STATUS=$?
set -e

if [[ "$SCOPE" != "unit" ]]; then
  echo "▶ レポートを生成します"
  if ! python3 "$SCRIPT_DIR/e2e-report.py"; then
    echo "⚠️ レポートの生成に失敗しました（テスト結果そのものは Gradle のレポートで確認できます）" >&2
  fi
  echo "   Gradle レポート: app/build/reports/androidTests/connected/debug/index.html"
fi

if [[ "$GRADLE_STATUS" -ne 0 ]]; then
  echo "❌ テストに失敗しました" >&2
  exit "$GRADLE_STATUS"
fi
echo "✅ テスト完了"
