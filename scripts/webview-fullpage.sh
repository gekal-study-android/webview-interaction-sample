#!/usr/bin/env bash
#
# 実機の WebView が表示しているページ全体を 1 枚の PNG にする。
#
# 使い方:
#   scripts/webview-fullpage.sh [--out <file>] [--run] [--port <port>] [<serial>]
#
# 例:
#   scripts/webview-fullpage.sh                    # 既定の出力先に保存
#   scripts/webview-fullpage.sh --out /tmp/x.png   # 出力先を指定
#   scripts/webview-fullpage.sh --run              # アプリを起動してから撮る
#   scripts/webview-fullpage.sh <serial>           # 端末シリアルを明示指定
#
# 端末のロックを解除し、アプリを前面に出した状態で実行してください。
# 画面に出ていない WebView は描画されず、撮影要求が返ってきません。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELF="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

APPLICATION_ID="cn.gekal.android.myapplicationwebviewinteractionsample"
MAIN_ACTIVITY="${APPLICATION_ID}/.MainActivity"

OUT="app/build/reports/e2e/webview-fullpage.png"
PORT=9222
SERIAL=""
LAUNCH=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT="${2:-}"
      [[ -n "$OUT" ]] || { echo "❌ --out には出力先のパスを指定してください。" >&2; exit 1; }
      shift 2
      ;;
    --port)
      PORT="${2:-}"
      [[ "$PORT" =~ ^[0-9]+$ ]] || { echo "❌ --port には番号を指定してください。" >&2; exit 1; }
      shift 2
      ;;
    --run)
      LAUNCH=true
      shift
      ;;
    -h|--help)
      sed -n '2,15p' "$SELF" | sed 's/^# \{0,1\}//'
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

# ── adb の解決 ──────────────────────────────────────────────────────────
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[[ -x "$ADB" ]] || ADB="$(command -v adb || true)"
if [[ ! -x "${ADB:-}" ]]; then
  echo "❌ adb が見つかりません。Android SDK platform-tools を PATH に追加するか、" >&2
  echo "   ANDROID_HOME を設定してください。" >&2
  exit 1
fi

# ── 対象端末の決定 ──────────────────────────────────────────────────────
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

# ── アプリの起動（任意）────────────────────────────────────────────────
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
if [[ "$LAUNCH" == true ]]; then
  echo "▶ アプリを起動します: $MAIN_ACTIVITY"
  "$ADB" -s "$SERIAL" shell am start -n "$MAIN_ACTIVITY" >/dev/null
  sleep 6
fi

PID="$("$ADB" -s "$SERIAL" shell pidof "$APPLICATION_ID" | tr -d '\r')"
if [[ -z "$PID" ]]; then
  echo "❌ アプリが起動していません。--run を付けるか、端末で起動してください。" >&2
  exit 1
fi

# ── DevTools への接続 ───────────────────────────────────────────────────
# WebView 側は MainActivity の setWebContentsDebuggingEnabled(true) で開いている
SOCKET="webview_devtools_remote_$PID"
if ! "$ADB" -s "$SERIAL" shell cat /proc/net/unix | grep -q "$SOCKET"; then
  echo "❌ WebView の devtools ソケットが見つかりません: $SOCKET" >&2
  echo "   デバッグビルドか、setWebContentsDebuggingEnabled(true) を確認してください。" >&2
  exit 1
fi

remove_forward() {
  "$ADB" -s "$SERIAL" forward --remove "tcp:$PORT" >/dev/null 2>&1 || true
}
trap remove_forward EXIT
"$ADB" -s "$SERIAL" forward "tcp:$PORT" "localabstract:$SOCKET" >/dev/null
echo "▶ DevTools に接続します: localhost:$PORT ($SOCKET)"

# 画面に出ていない WebView は描画されず、撮影要求が返ってこないまま固まる
VISIBLE="$(curl -s -m 10 "http://127.0.0.1:$PORT/json/list" |
  python3 -c 'import json,sys; t=[x for x in json.load(sys.stdin) if x.get("type")=="page"]; print(json.loads(t[0]["description"]).get("visible") if t else False)' 2>/dev/null || echo False)"
if [[ "$VISIBLE" != "True" ]]; then
  echo "❌ WebView が画面に出ていません（ロック画面・画面消灯・バックグラウンド）。" >&2
  echo "   端末のロックを解除し、アプリを前面に出してから実行してください。" >&2
  exit 1
fi

# ── 撮影 ────────────────────────────────────────────────────────────────
mkdir -p "$(dirname "$OUT")"
python3 "$SCRIPT_DIR/webview-fullpage.py" --port "$PORT" --out "$OUT"
