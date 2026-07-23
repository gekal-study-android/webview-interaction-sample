import type { NextConfig } from 'next';

/**
 * GitHub Pages のプロジェクトサイト（https://<user>.github.io/<repo>/）で配信するため、
 * 静的エクスポート + リポジトリ名の basePath を指定する。
 * BASE_PATH を空にすればサイトルート配信やローカル確認も可能。
 */
const basePath = process.env.BASE_PATH ?? '/webview-interaction-sample';

const nextConfig: NextConfig = {
  output: 'export',
  basePath,
  assetPrefix: basePath || undefined,
  images: { unoptimized: true },
};

export default nextConfig;
