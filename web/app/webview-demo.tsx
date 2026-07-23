'use client';

import { useEffect, useState } from 'react';

export function WebViewDemo() {
  const [message, setMessage] = useState('');
  // ハイドレーション完了（= handleReturnValue 登録済み）まではボタンを押せないようにする
  const [ready, setReady] = useState(false);

  // Native -> JS: Android の `evaluateJavascript` から呼ばれるため window に公開する
  useEffect(() => {
    window.handleReturnValue = (value: string) => setMessage(`Received: ${value}`);
    setReady(true);
    return () => {
      delete window.handleReturnValue;
    };
  }, []);

  // JS -> Native
  const showToast = () => {
    window.AndroidInterface?.showToast('Hello from WebView!');
  };

  return (
    <>
      <button type="button" onClick={showToast} disabled={!ready}>
        Show Toast
      </button>
      <p id="message">{message}</p>
    </>
  );
}
