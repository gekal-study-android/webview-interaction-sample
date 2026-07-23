import { WebViewDemo } from './webview-demo';

export default function Home() {
  return (
    <main>
      <h1>WebView Example</h1>
      <WebViewDemo />
      <div>
        <ul style={{ listStyleType: 'none' }}>
          <li>
            <span>[※] First item（項目１）</span>
          </li>
          <li>
            <span>[※] Second item（項目２）</span>
          </li>
          <li>
            <span>[※] Third item（項目３）</span>
          </li>
        </ul>
      </div>
    </main>
  );
}
