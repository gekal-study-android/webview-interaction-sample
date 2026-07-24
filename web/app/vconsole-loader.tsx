'use client';

import { useEffect } from 'react';

/**
 * vConsole を有効にするか判定する。
 *
 * 有効・無効はネイティブのビルド variant が決める（`app/configs/*.json` → `BuildConfig.VCONSOLE`）。
 * アプリは `?vconsole=1` / `0` を URL に付けて渡すので、ここではそれだけを見る。
 * ブラウザで手元確認するときも `?vconsole=1` で出せる。
 */
function isEnabled(search: string): boolean {
  const flag = new URLSearchParams(search).get('vconsole');
  return flag === '1' || flag === 'true';
}

/**
 * WebView 上でログとネットワーク通信を確認するための vConsole を読み込む。
 *
 * バンドルを膨らませないよう動的 import で、有効なときだけ読み込む。
 * ネットワークパネルは XHR / fetch を捕捉するため、なるべく早く初期化する。
 */
export function VConsoleLoader() {
  useEffect(() => {
    if (!isEnabled(window.location.search)) {
      return;
    }

    let cancelled = false;
    let instance: { destroy: () => void } | undefined;

    import('vconsole').then(({ default: VConsole }) => {
      if (cancelled) {
        return;
      }
      // アプリの配色に合わせる（InitColorSchemeScript が <html> に付けたクラスで判定）
      const dark = document.documentElement.classList.contains('dark');
      instance = new VConsole({ theme: dark ? 'dark' : 'light' });
    });

    return () => {
      cancelled = true;
      instance?.destroy();
    };
  }, []);

  return null;
}
