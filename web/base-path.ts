/**
 * GitHub Pages のプロジェクトサイト（https://<user>.github.io/<repo>/）で配信するための basePath。
 *
 * `next.config.ts` と、アイコンなど自前で URL を組み立てるメタデータの両方から参照する。
 * `BASE_PATH=` を指定すればサイトルート配信やローカル確認に切り替えられる。
 */
export const basePath = process.env.BASE_PATH ?? '/webview-interaction-sample';
