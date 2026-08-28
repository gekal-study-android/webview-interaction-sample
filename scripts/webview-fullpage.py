#!/usr/bin/env python3
"""実機の WebView が表示しているページ全体を 1 枚の PNG にする。

`scripts/webview-fullpage.sh` から呼ばれる。adb forward 済みの DevTools（CDP）に
つないで、スクロールしながら撮った画面を連結する。

Android WebView では、ページ全体を一度に撮る手段が使えない:

- `Page.captureScreenshot` に `clip` + `captureBeyondViewport` を渡すと応答が返らない
- `Emulation.setDeviceMetricsOverride` で画面を伸ばすと、同じ画面が繰り返し写る
- DOM をいじった直後の撮影も応答が返らない（固定ヘッダーを CSS で消す手が使えない）

そのためページには一切手を触れず、スクロールと切り取りだけで組み立てる。
固定ヘッダーの分だけ重ねてスクロールし、2 枚目以降はヘッダーを落として貼る。
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import os
import socket
import struct
import sys
import time
import urllib.request

HOST = "127.0.0.1"


class Cdp:
    """DevTools プロトコルの最小クライアント（WebSocket は手書き）。"""

    def __init__(self, port: int, timeout: float) -> None:
        self.host, self.port = HOST, port
        # macOS のシステムプロキシ設定を拾うと localhost 宛でも詰まるため無効化する
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        targets = json.load(opener.open(f"http://{HOST}:{port}/json/list", timeout=10))
        pages = [t for t in targets if t.get("type") == "page"]
        if not pages:
            raise SystemExit("❌ WebView のページが見つかりません。アプリが前面にあるか確認してください。")
        self.target = pages[0]
        path = self.target["webSocketDebuggerUrl"].split(f"{HOST}:{port}", 1)[1]
        self.sock = self._connect(path, timeout)
        self.counter = 0

    def _connect(self, path: str, timeout: float) -> socket.socket:
        sock = socket.create_connection((self.host, self.port), timeout=timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        sock.sendall(
            (
                f"GET {path} HTTP/1.1\r\nHost: {self.host}:{self.port}\r\n"
                f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
                f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
            ).encode()
        )
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += sock.recv(4096)
        if b"101" not in buf.split(b"\r\n")[0]:
            raise SystemExit(f"❌ DevTools への接続に失敗しました: {buf.splitlines()[0]!r}")
        return sock

    def _send(self, payload: str) -> None:
        data = payload.encode()
        header = bytearray([0x81])
        mask = os.urandom(4)
        length = len(data)
        if length < 126:
            header.append(0x80 | length)
        elif length < 1 << 16:
            header.append(0x80 | 126)
            header += struct.pack(">H", length)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", length)
        header += mask
        self.sock.sendall(bytes(header) + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _recv_exact(self, size: int) -> bytes:
        out = b""
        while len(out) < size:
            chunk = self.sock.recv(size - len(out))
            if not chunk:
                raise EOFError("DevTools との接続が切れました")
            out += chunk
        return out

    def _recv(self) -> dict:
        message = b""
        while True:
            first, second = self._recv_exact(2)
            fin, opcode, length = first & 0x80, first & 0x0F, second & 0x7F
            if length == 126:
                length = struct.unpack(">H", self._recv_exact(2))[0]
            elif length == 127:
                length = struct.unpack(">Q", self._recv_exact(8))[0]
            message += self._recv_exact(length)
            if fin:
                if opcode in (0x0, 0x1):
                    return json.loads(message)
                message = b""  # ping/pong などは読み飛ばす

    def call(self, method: str, params: dict | None = None) -> dict:
        self.counter += 1
        self._send(json.dumps({"id": self.counter, "method": method, "params": params or {}}))
        while True:
            message = self._recv()
            if message.get("id") == self.counter:
                if "error" in message:
                    raise RuntimeError(f"{method}: {message['error']}")
                return message.get("result", {})

    def js(self, expression: str):
        result = self.call("Runtime.evaluate", {"expression": expression, "returnByValue": True})
        if "exceptionDetails" in result:
            raise RuntimeError(f"JS の評価に失敗しました: {result['exceptionDetails']}")
        return result["result"].get("value")


METRICS_JS = """JSON.stringify({
  dpr: window.devicePixelRatio,
  width: document.documentElement.clientWidth,
  viewport: window.innerHeight,
  total: Math.max(document.documentElement.scrollHeight, document.body.scrollHeight),
  header: (document.querySelector('header') || { getBoundingClientRect: () => ({ height: 0 }) })
            .getBoundingClientRect().height
})"""


def main() -> int:
    parser = argparse.ArgumentParser(description="WebView のページ全体を 1 枚の PNG にする")
    parser.add_argument("--port", type=int, default=9222, help="adb forward したポート")
    parser.add_argument("--out", required=True, help="出力する PNG のパス")
    parser.add_argument("--settle", type=float, default=0.5, help="スクロール後に描画を待つ秒数")
    parser.add_argument("--timeout", type=float, default=25.0, help="DevTools の応答待ち秒数")
    parser.add_argument("--retries", type=int, default=3, help="撮影が返らないときの再試行回数")
    args = parser.parse_args()

    try:
        from PIL import Image
    except ImportError:
        raise SystemExit(
            "❌ Pillow が必要です: python3 -m pip install --user pillow"
        )

    cdp = Cdp(args.port, args.timeout)
    print(f"▶ 対象: {cdp.target.get('url')}")
    cdp.call("Page.enable")
    cdp.call("Runtime.enable")

    metrics = json.loads(cdp.js(METRICS_JS))
    dpr = metrics["dpr"]
    header = round(metrics["header"])
    viewport, total = metrics["viewport"], metrics["total"]
    print(
        f"▶ 表示領域 {metrics['width']}x{viewport} / ページ全体の高さ {total}"
        f" / 固定ヘッダー {header} (CSS px, dpr={dpr})"
    )

    def capture(target: float) -> tuple[float, "Image.Image"]:
        """指定位置までスクロールして撮る。

        この WebView は、新しいフレームが描かれるまで撮影要求に応答しない。
        静止したページをそのまま撮ろうとすると返ってこないため、
        1px ずらしてから戻し、必ず描き直させてから撮る。
        """
        for attempt in range(1, args.retries + 1):
            nudge = target + 1 if target + viewport < total else max(0, target - 1)
            cdp.js(f"window.scrollTo(0, {nudge})")
            time.sleep(0.15)
            scrolled = cdp.js(f"window.scrollTo(0, {target}); window.scrollY")
            time.sleep(args.settle)
            try:
                data = cdp.call("Page.captureScreenshot", {"format": "png"})["data"]
                return scrolled, Image.open(io.BytesIO(base64.b64decode(data)))
            except (TimeoutError, socket.timeout):
                if attempt == args.retries:
                    raise SystemExit(
                        "❌ 撮影の応答がありません。端末のロックが解除され、"
                        "アプリが前面にあるか確認してください。"
                    )
                print(f"  ⚠️ 応答がないため再試行します ({attempt}/{args.retries})")

    canvas = Image.new("RGB", (round(metrics["width"] * dpr), round(total * dpr)), "white")
    # 固定ヘッダーが隠している分だけ重ねて進む
    step = max(1, viewport - header)
    offset, shots = 0, 0
    while True:
        scrolled, tile = capture(offset)
        if shots == 0:
            canvas.paste(tile, (0, 0))
        else:
            # 2 枚目以降は、スクロールしても残る固定ヘッダーを落としてから貼る
            cropped = tile.crop((0, round(header * dpr), tile.width, tile.height))
            canvas.paste(cropped, (0, round((scrolled + header) * dpr)))
        shots += 1
        print(f"  {shots}: scrollY={round(scrolled)}")
        if scrolled + viewport >= total - 1:
            break
        offset = scrolled + step

    cdp.js("window.scrollTo(0, 0)")
    canvas.save(args.out)
    print(f"✅ 保存しました: {args.out}")
    print(f"   {canvas.width}x{canvas.height} / {os.path.getsize(args.out) // 1024} KB / {shots} 枚を連結")
    return 0


if __name__ == "__main__":
    sys.exit(main())
