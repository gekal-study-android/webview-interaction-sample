'use client';

import { useEffect } from 'react';

/**
 * vConsole を有効にするか判定する。
 *
 * - `?vconsole=1` / `0` で明示的に切り替え
 * - 明示指定が無ければ、デバッグビルド（`?env=debug`）でのみ有効
 *
 * リリースビルド（`?env=release`）では既定で無効。`?vconsole=1` を付ければ出せる。
 */
function isEnabled(search: string): boolean {
  const params = new URLSearchParams(search);
  const flag = params.get('vconsole');
  if (flag === '1' || flag === 'true') return true;
  if (flag === '0' || flag === 'false') return false;
  return params.get('env') === 'debug';
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
