/**
 * Android の WebView から注入される JavaScript Interface と、
 * ネイティブ側が `evaluateJavascript` で呼び出すグローバル関数の型定義。
 *
 * @see app/src/main/java/cn/gekal/android/myapplicationwebviewinteractionsample/JavaScriptInterface.kt
 */
interface AndroidInterface {
  showToast(message: string): void;
}

declare global {
  interface Window {
    /** JS -> Native。Android 側で `addJavascriptInterface(..., "AndroidInterface")` として注入される。 */
    AndroidInterface?: AndroidInterface;
    /** Native -> JS。Android 側から `handleReturnValue('...')` として呼び出される。 */
    handleReturnValue?: (value: string) => void;
  }
}

export {};
