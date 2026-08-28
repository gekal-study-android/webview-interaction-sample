#!/usr/bin/env python3
"""実機 E2E（connectedDebugAndroidTest）の結果と画面キャプチャを 1 枚の HTML にまとめる。

`scripts/test.sh e2e` から自動で呼ばれる。単体でも実行できる。

    scripts/e2e-report.py [--out <dir>]

Gradle が出す HTML レポートには画面キャプチャが載らず、キャプチャは別ディレクトリに
ファイル名だけで並ぶ。どのテストのどの段階の画面かを突き合わせられるよう、
JUnit XML の結果とキャプチャをテスト単位で並べ直す。
"""

from __future__ import annotations

import argparse
import html
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = ROOT / "app/build/outputs/androidTest-results/connected/debug"
SHOTS_DIR = ROOT / "app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected"
OUT_DIR = ROOT / "app/build/reports/e2e"

# ScreenshotRule が付けるファイル名: <クラス>-<メソッド>-<連番>-<ステップ>.png
SHOT_NAME = re.compile(r"^(?P<cls>[^-]+)-(?P<method>.+)-(?P<index>\d{2})-(?P<step>.+)\.png$")
# ScreenshotRule.safe() と同じ置き換え。メソッド名から実際のファイル名を作るのに使う
UNSAFE = re.compile(r'[\\/:*?"<>|\s]')
# logcat のファイル名では、ASCII 以外の文字が 1 文字ずつ "_" に置き換わる
LOGCAT_UNSAFE = re.compile(r"[^A-Za-z0-9_.\-]")

LOGCAT_LINE = re.compile(
    r"^\d\d-\d\d (?P<time>\d\d:\d\d:\d\d\.\d\d\d)\s+\d+\s+\d+ "
    r"(?P<level>[VDIWEF]) (?P<tag>[^:]*?)\s*: (?P<message>.*)$"
)
# 端末側の雑多なログに埋もれるため、テストの流れが追える行だけを残す
LOGCAT_KEEP = re.compile(
    r"TestRunner:|\[Bridge\]|INFO:CONSOLE|ScreenshotRule|ExternalLinkOpener"
    r"|ActivityTaskManager: Displayed|LifecycleMonitor: Lifecycle status change"
    r"|FATAL EXCEPTION|AndroidRuntime: |Instrumentation: "
)
# WebView のコンソール出力に付く長い source 情報は落とす
CONSOLE_SOURCE = re.compile(r", source: \S+ \(\d+\)$")
# [INFO:CONSOLE:1] "…" の包みを外して、ページが出したメッセージだけにする
CONSOLE_WRAP = re.compile(r'^\[INFO:CONSOLE:\d+\]\s*"(?P<body>.*)"$')
LOGCAT_HEAD = 40
LOGCAT_TAIL = 110

# ステップ名は英字のままファイル名になるため、表示用の日本語をここで与える
STEP_LABELS = {
    "demo-loaded": "デモページの読み込み完了",
    "bridge-connected": "ブリッジの検出",
    "toast-returned": "showToast() の往復",
    "device-info": "端末情報の取得",
    "callback-resolved": "非同期コールバックの受信",
    "reloaded": "再読み込み後",
    "error-screen": "ネイティブのエラー画面",
    "recovered": "再試行で復帰",
    "overlay-shown": "アプリ内オーバーレイの表示",
    "overlay-closed": "戻る操作で復帰",
    "failed": "失敗時の画面",
}


@dataclass
class Shot:
    index: int
    step: str
    path: Path

    @property
    def label(self) -> str:
        return STEP_LABELS.get(self.step, self.step)


@dataclass
class Case:
    classname: str
    name: str
    time: float
    failure: str | None = None
    shots: list[Shot] = field(default_factory=list)
    logcat: Path | None = None
    logcat_lines: list[str] = field(default_factory=list)
    logcat_total: int = 0

    @property
    def simple_class(self) -> str:
        return self.classname.rsplit(".", 1)[-1]

    @property
    def passed(self) -> bool:
        return self.failure is None

    @property
    def file_prefix(self) -> str:
        return f"{self.simple_class}-{UNSAFE.sub('_', self.name)}"


@dataclass
class Suite:
    device: str
    cases: list[Case]
    generated_at: str

    @property
    def failures(self) -> int:
        return sum(1 for c in self.cases if not c.passed)

    @property
    def duration(self) -> float:
        return sum(c.time for c in self.cases)


def read_cases(results_dir: Path) -> tuple[str, list[Case]]:
    files = sorted(results_dir.glob("TEST-*.xml"))
    if not files:
        raise SystemExit(f"❌ テスト結果 XML が見つかりません: {results_dir}")

    device = ""
    cases: list[Case] = []
    for file in files:
        suite = ET.parse(file).getroot()
        device = device or suite.attrib.get("hostname") or suite.attrib.get("name", "")
        for node in suite.iter("testcase"):
            failure = None
            for kind in ("failure", "error"):
                found = node.find(kind)
                if found is not None:
                    failure = (found.attrib.get("message") or "") + "\n" + (found.text or "")
                    failure = failure.strip()
            cases.append(
                Case(
                    classname=node.attrib.get("classname", ""),
                    name=node.attrib.get("name", ""),
                    time=float(node.attrib.get("time", "0") or 0),
                    failure=failure,
                )
            )
    # 失敗を先頭に、あとはクラスとテスト名の順に並べる
    cases.sort(key=lambda c: (c.passed, c.classname, c.name))
    return device, cases


def attach_shots(cases: list[Case], shots_dir: Path) -> tuple[Path | None, int]:
    """キャプチャをテストに割り当て、キャプチャの置き場と総数を返す。"""
    device_dirs = [d for d in sorted(shots_dir.glob("*")) if d.is_dir()] if shots_dir.exists() else []
    if not device_dirs:
        return None, 0

    by_prefix: dict[str, list[Shot]] = {}
    total = 0
    for device_dir in device_dirs:
        for png in sorted(device_dir.glob("*.png")):
            matched = SHOT_NAME.match(png.name)
            if not matched:
                continue
            prefix = f"{matched['cls']}-{matched['method']}"
            by_prefix.setdefault(prefix, []).append(
                Shot(index=int(matched["index"]), step=matched["step"], path=png)
            )
            total += 1

    for case in cases:
        case.shots = sorted(by_prefix.get(case.file_prefix, []), key=lambda s: s.index)
    return device_dirs[0], total


def logcat_matches(suffix: str, method: str) -> bool:
    """logcat のファイル名（ASCII 以外が "_" になっている）がテスト名と一致するか。"""
    expected = LOGCAT_UNSAFE.sub("_", method)
    if suffix == expected:
        return True
    # 置き換え規則が変わっても拾えるよう、"_" は任意の 1 文字として扱う
    return len(suffix) == len(method) and all(
        actual == original or actual == "_" for actual, original in zip(suffix, method)
    )


def excerpt(path: Path) -> tuple[list[str], int]:
    """テストの流れが追える行だけを抜き出して整形する。"""
    kept: list[str] = []
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not LOGCAT_KEEP.search(raw):
            continue
        matched = LOGCAT_LINE.match(raw)
        if not matched:
            continue
        message = CONSOLE_SOURCE.sub("", matched["message"]).strip()
        wrapped = CONSOLE_WRAP.match(message)
        if wrapped:
            message = wrapped["body"]
        kept.append(f"{matched['time']} {matched['level']} {matched['tag']}: {message}")

    total = len(kept)
    if total > LOGCAT_HEAD + LOGCAT_TAIL:
        omitted = total - LOGCAT_HEAD - LOGCAT_TAIL
        kept = kept[:LOGCAT_HEAD] + [f"… 中略（{omitted} 行）…"] + kept[-LOGCAT_TAIL:]
    return kept, total


def attach_logcat(cases: list[Case], results_dir: Path) -> None:
    files: list[Path] = []
    for device_dir in sorted(d for d in results_dir.glob("*") if d.is_dir()):
        files.extend(sorted(device_dir.glob("logcat-*.txt")))

    for case in cases:
        prefix = f"logcat-{case.classname}-"
        for file in files:
            if not file.name.startswith(prefix):
                continue
            if logcat_matches(file.name[len(prefix):-len(".txt")], case.name):
                case.logcat = file
                case.logcat_lines, case.logcat_total = excerpt(file)
                break


def copy_logcat(cases: list[Case], out_dir: Path) -> None:
    logcat_out = out_dir / "logcat"
    if logcat_out.exists():
        shutil.rmtree(logcat_out)
    logcat_out.mkdir(parents=True)
    for case in cases:
        if case.logcat:
            shutil.copy2(case.logcat, logcat_out / case.logcat.name)


def copy_shots(cases: list[Case], out_dir: Path) -> None:
    shots_out = out_dir / "screenshots"
    if shots_out.exists():
        shutil.rmtree(shots_out)
    shots_out.mkdir(parents=True)
    for case in cases:
        for shot in case.shots:
            shutil.copy2(shot.path, shots_out / shot.path.name)


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def render_case(case: Case) -> str:
    status = (
        '<span class="badge pass">成功</span>'
        if case.passed
        else '<span class="badge fail">失敗</span>'
    )
    failure = ""
    if case.failure:
        failure = f'<pre class="failure">{esc(case.failure)}</pre>'

    if case.shots:
        cards = "\n".join(
            f"""          <figure class="shot">
            <a href="screenshots/{esc(shot.path.name)}" target="_blank" rel="noopener">
              <img src="screenshots/{esc(shot.path.name)}" alt="{esc(case.name)} / {esc(shot.label)}" loading="lazy">
            </a>
            <figcaption><span class="num">{shot.index:02d}</span>{esc(shot.label)}</figcaption>
          </figure>"""
            for shot in case.shots
        )
        shots = f'        <div class="shots">\n{cards}\n        </div>'
    else:
        shots = '        <p class="noshot">このテストには画面キャプチャがありません。</p>'

    if case.logcat_lines:
        omitted = "" if case.logcat_total == len(case.logcat_lines) else f"（全 {case.logcat_total} 行から抜粋）"
        body = esc("\n".join(case.logcat_lines))
        logcat = f"""        <details class="logcat"{' open' if not case.passed else ''}>
          <summary>logcat {omitted}<a href="logcat/{esc(case.logcat.name)}" target="_blank" rel="noopener">全文</a></summary>
          <pre>{body}</pre>
        </details>"""
    elif case.logcat:
        logcat = f"""        <details class="logcat">
          <summary>logcat（抜き出せる行がありませんでした）<a href="logcat/{esc(case.logcat.name)}" target="_blank" rel="noopener">全文</a></summary>
        </details>"""
    else:
        logcat = ""

    return f"""      <article class="case{'' if case.passed else ' is-fail'}" id="{esc(case.file_prefix)}">
        <header>
          <p class="case-class">{esc(case.simple_class)}</p>
          <h3>{esc(case.name)}</h3>
          <p class="case-meta">{status}<span class="time">{case.time:.2f} s</span></p>
        </header>
{failure}
{shots}
{logcat}
      </article>"""


def render(suite: Suite, shots_total: int, shots_source: Path | None) -> str:
    rows = "\n".join(
        f"""            <tr>
              <td class="cls">{esc(c.simple_class)}</td>
              <td><a href="#{esc(c.file_prefix)}">{esc(c.name)}</a></td>
              <td class="num">{c.time:.2f}</td>
              <td>{'<span class="badge pass">成功</span>' if c.passed else '<span class="badge fail">失敗</span>'}</td>
            </tr>"""
        for c in suite.cases
    )
    cases = "\n".join(render_case(c) for c in suite.cases)
    ok = suite.failures == 0
    source = esc(str(shots_source)) if shots_source else "（キャプチャなし）"

    return f"""<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>WebView 実機 E2E レポート</title>
<style>
  :root {{
    --ground: #f4f7f5; --surface: #fff; --surface-2: #eef3f0;
    --ink: #0f1714; --muted: #5b6c65; --line: #d9e4de;
    --accent: #0d6b51; --accent-soft: #e0f2ea; --fail: #a3372c; --fail-soft: #fbe7e3;
    --body: "Hiragino Sans", "Noto Sans JP", system-ui, sans-serif;
    --display: "Hiragino Mincho ProN", "Yu Mincho", serif;
    --mono: "SFMono-Regular", Menlo, Consolas, monospace;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{
      --ground: #0b1210; --surface: #131b18; --surface-2: #182220;
      --ink: #e7f1ec; --muted: #92a79e; --line: #23302b;
      --accent: #64e0b4; --accent-soft: #113028; --fail: #f0a094; --fail-soft: #331915;
    }}
  }}
  * {{ box-sizing: border-box; }}
  body {{ margin: 0; background: var(--ground); color: var(--ink); font-family: var(--body);
          font-size: 15px; line-height: 1.8; -webkit-font-smoothing: antialiased; }}
  .wrap {{ max-width: 1100px; margin: 0 auto; padding: 0 24px 80px; }}
  a {{ color: var(--accent); }}
  code, .mono {{ font-family: var(--mono); font-size: .9em; }}
  h1, h2, h3 {{ font-family: var(--display); margin: 0; line-height: 1.4; text-wrap: balance; }}

  header.masthead {{ padding: 56px 0 32px; border-bottom: 1px solid var(--line); }}
  .eyebrow {{ font-family: var(--mono); font-size: 12px; letter-spacing: .12em; text-transform: uppercase;
              color: var(--accent); margin: 0 0 14px; }}
  h1 {{ font-size: clamp(28px, 5vw, 42px); font-weight: 700; }}
  .dek {{ color: var(--muted); margin: 14px 0 0; max-width: 64ch; }}

  .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 1px;
            background: var(--line); border: 1px solid var(--line); border-radius: 12px;
            overflow: hidden; margin-top: 32px; }}
  .stat {{ background: var(--surface); padding: 16px 18px; }}
  .stat b {{ display: block; font-family: var(--display); font-size: 28px; line-height: 1.2;
             font-variant-numeric: tabular-nums; }}
  .stat span {{ font-size: 12px; color: var(--muted); }}
  .stat.ok b {{ color: var(--accent); }}
  .stat.ng b {{ color: var(--fail); }}

  section {{ padding-top: 56px; }}
  h2 {{ font-size: 22px; font-weight: 700; }}
  .note {{ color: var(--muted); font-size: 14px; margin: 8px 0 0; }}

  .table-wrap {{ overflow-x: auto; margin-top: 20px; border: 1px solid var(--line);
                 border-radius: 12px; background: var(--surface); }}
  table {{ border-collapse: collapse; width: 100%; min-width: 620px; }}
  th, td {{ text-align: left; padding: 12px 16px; border-bottom: 1px solid var(--line); font-size: 14px; }}
  thead th {{ font-family: var(--mono); font-size: 11px; letter-spacing: .1em; text-transform: uppercase;
              color: var(--muted); font-weight: 500; background: var(--surface-2); }}
  tbody tr:last-child td {{ border-bottom: 0; }}
  td.cls {{ font-family: var(--mono); font-size: 12px; color: var(--muted); white-space: nowrap; }}
  td.num {{ font-variant-numeric: tabular-nums; text-align: right; white-space: nowrap; }}

  .badge {{ display: inline-block; font-size: 12px; padding: 1px 10px; border-radius: 999px; white-space: nowrap; }}
  .badge.pass {{ color: var(--accent); background: var(--accent-soft); }}
  .badge.fail {{ color: var(--fail); background: var(--fail-soft); }}

  .case {{ background: var(--surface); border: 1px solid var(--line); border-radius: 14px;
           padding: 24px; margin-top: 20px; scroll-margin-top: 16px; }}
  .case.is-fail {{ border-color: var(--fail); }}
  .case-class {{ font-family: var(--mono); font-size: 12px; color: var(--muted); margin: 0; }}
  .case h3 {{ font-size: 18px; font-weight: 700; margin: 4px 0 0; }}
  .case-meta {{ margin: 10px 0 0; display: flex; gap: 12px; align-items: center; }}
  .case-meta .time {{ font-family: var(--mono); font-size: 12px; color: var(--muted); }}
  .failure {{ font-family: var(--mono); font-size: 12.5px; line-height: 1.7; white-space: pre-wrap;
              background: var(--fail-soft); color: var(--fail); border-radius: 10px;
              padding: 14px 16px; margin: 16px 0 0; overflow-x: auto; }}

  .shots {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
            gap: 20px; margin-top: 20px; }}
  .shot {{ margin: 0; }}
  .shot a {{ display: block; border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }}
  .shot img {{ display: block; width: 100%; height: auto; }}
  .shot figcaption {{ margin-top: 8px; font-size: 13px; color: var(--muted); display: flex; gap: 8px; }}
  .shot .num {{ font-family: var(--mono); font-size: 11px; color: var(--accent); }}
  .noshot {{ color: var(--muted); font-size: 13px; margin: 16px 0 0; }}

  .logcat {{ margin-top: 20px; border-top: 1px solid var(--line); padding-top: 14px; }}
  .logcat summary {{ cursor: pointer; font-size: 13px; color: var(--muted); display: flex;
                     gap: 10px; align-items: baseline; }}
  .logcat summary::marker {{ color: var(--accent); }}
  .logcat summary a {{ font-size: 12px; }}
  .logcat pre {{ font-family: var(--mono); font-size: 11.5px; line-height: 1.65; margin: 12px 0 0;
                 background: var(--surface-2); border-radius: 10px; padding: 14px 16px;
                 max-height: 460px; overflow: auto; white-space: pre; }}

  .paths {{ margin-top: 20px; display: grid; gap: 10px; }}
  .path {{ display: grid; grid-template-columns: 150px 1fr; gap: 12px; align-items: baseline;
           border-top: 1px solid var(--line); padding-top: 10px; }}
  .path b {{ font-size: 13px; font-weight: 500; color: var(--muted); }}
  .path span {{ font-family: var(--mono); font-size: 12px; word-break: break-all; }}
  footer {{ margin-top: 56px; padding-top: 20px; border-top: 1px solid var(--line);
            color: var(--muted); font-size: 12.5px; }}
</style>
</head>
<body>
<div class="wrap">
  <header class="masthead">
    <p class="eyebrow">実機テスト · {esc(suite.device)}</p>
    <h1>WebView 実機 E2E レポート</h1>
    <p class="dek">
      配信中のデモページを実機のアプリで読み込み、JS ⇄ Native の往復とネイティブが出す画面を
      確認した結果です。画面キャプチャはテスト実行中に撮ったものをそのまま並べています。
    </p>
    <div class="stats">
      <div class="stat"><b>{len(suite.cases)}</b><span>テスト</span></div>
      <div class="stat {'ok' if ok else 'ng'}"><b>{suite.failures}</b><span>失敗</span></div>
      <div class="stat"><b>{suite.duration:.1f}<small>s</small></b><span>所要時間</span></div>
      <div class="stat"><b>{shots_total}</b><span>画面キャプチャ</span></div>
    </div>
  </header>

  <section>
    <h2>結果</h2>
    <p class="note">テスト名をクリックすると、そのテストのキャプチャに移動します。</p>
    <div class="table-wrap">
      <table>
        <thead><tr><th>クラス</th><th>テスト</th><th>時間 (s)</th><th>結果</th></tr></thead>
        <tbody>
{rows}
        </tbody>
      </table>
    </div>
  </section>

  <section>
    <h2>画面の記録</h2>
    <p class="note">キャプチャはテストの節目で撮っています（失敗時は自動で 1 枚）。画像をクリックすると原寸で開きます。
       logcat はテスト単位で、ブリッジのやり取りとライフサイクルだけを抜き出しています。</p>
{cases}
  </section>

  <section>
    <h2>元データ</h2>
    <div class="paths">
      <div class="path"><b>Gradle レポート</b><span>app/build/reports/androidTests/connected/debug/index.html</span></div>
      <div class="path"><b>JUnit XML</b><span>app/build/outputs/androidTest-results/connected/debug/</span></div>
      <div class="path"><b>キャプチャ</b><span>{source}</span></div>
      <div class="path"><b>logcat（全文）</b><span>app/build/reports/e2e/logcat/ にコピー済み（元は app/build/outputs/androidTest-results/connected/debug/&lt;device&gt;/）</span></div>
    </div>
  </section>

  <footer>
    生成: {esc(suite.generated_at)} · scripts/test.sh e2e · webview-interaction-sample
  </footer>
</div>
</body>
</html>
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="実機 E2E のレポートを生成する")
    parser.add_argument("--results", type=Path, default=RESULTS_DIR, help="JUnit XML のディレクトリ")
    parser.add_argument("--shots", type=Path, default=SHOTS_DIR, help="キャプチャのディレクトリ")
    parser.add_argument("--out", type=Path, default=OUT_DIR, help="出力先ディレクトリ")
    args = parser.parse_args()

    device, cases = read_cases(args.results)
    shots_source, shots_total = attach_shots(cases, args.shots)
    attach_logcat(cases, args.results)

    args.out.mkdir(parents=True, exist_ok=True)
    copy_shots(cases, args.out)
    copy_logcat(cases, args.out)

    suite = Suite(
        device=device or "unknown device",
        cases=cases,
        generated_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    )
    index = args.out / "index.html"
    index.write_text(render(suite, shots_total, shots_source), encoding="utf-8")

    print(f"✅ レポートを生成しました: {index}")
    logs = sum(1 for c in cases if c.logcat)
    print(f"   テスト {len(cases)} 件 / 失敗 {suite.failures} 件 / キャプチャ {shots_total} 枚 / logcat {logs} 件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
