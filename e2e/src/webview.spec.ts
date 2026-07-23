import { test, expect, type Page } from '@playwright/test';

const WEBVIEW_URL = process.env.WEBVIEW_URL ?? 'http://localhost:3000/index.html';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    // Native側のAndroidInterfaceをモックする
    (window as any).AndroidInterface = {
      showToast: (message: string, longDuration?: boolean) => {
        console.log(`Mocked AndroidInterface.showToast called with: ${message} (long=${longDuration ?? false})`);
        // Webviewのスクリプトを評価する
        (window as any).handleReturnValue('Hello from Mocked!');
      },
      getDeviceInfo: () =>
        JSON.stringify({
          manufacturer: 'Mocked',
          model: 'Pixel Mock',
          androidVersion: '15',
          sdkInt: 35,
        }),
      getBatteryStatus: () => JSON.stringify({ level: 55, charging: false }),
      vibrate: () => {},
      copyToClipboard: () => {},
      shareText: () => {},
      requestNativeCallback: (requestId: string, delayMillis: number) => {
        setTimeout(() => {
          (window as any).onNativeEvent(JSON.stringify({ type: 'callback', requestId, message: 'Mocked callback' }));
        }, delayMillis);
      },
      // ネイティブに通知された配色を検証できるよう記録しておく
      setAppTheme: (theme: string) => {
        (window as any).__appThemeCalls = [...((window as any).__appThemeCalls ?? []), theme];
      },
    };
  });
});

const appThemeCalls = (page: Page) => page.evaluate(() => (window as any).__appThemeCalls ?? []);

const openDemo = async (page: Page) => {
  await page.goto(WEBVIEW_URL);
  // ハイドレーションが完了するとボタンが有効になる
  await expect(page.getByRole('button', { name: 'Show Toast' })).toBeEnabled();
};

test('should be able to run tests', async ({ page }, testInfo) => {
  await openDemo(page);
  await page.screenshot({
    path: `test-results/screenshots/${testInfo.title}/${testInfo.project.name}/after-loading.png`,
  });

  await page.getByRole('button', { name: 'Show Toast' }).click();
  await page.screenshot({
    path: `test-results/screenshots/${testInfo.title}/${testInfo.project.name}/after-click.png`,
  });

  await expect(page.locator('#message')).toHaveText('Received: Hello from Mocked!');
});

test('should render the device info returned by the native bridge', async ({ page }) => {
  await openDemo(page);

  // getDeviceInfo() / getBatteryStatus() は戻り値のある同期呼び出し
  await expect(page.getByRole('cell', { name: 'Pixel Mock' })).toBeVisible();
  await expect(page.getByText('バッテリー 55%')).toBeVisible();
});

test('should resolve the asynchronous native callback', async ({ page }) => {
  await openDemo(page);

  await page.getByRole('button', { name: 'コールバックを要求' }).click();

  // ネイティブが onNativeEvent() で呼び返すまで待つ
  await expect(page.getByRole('alert').filter({ hasText: 'Mocked callback' })).toBeVisible({
    timeout: 15000,
  });
});

test('should log every interaction between JS and native', async ({ page }) => {
  await openDemo(page);

  await page.getByRole('button', { name: 'Show Toast' }).click();

  const log = page.getByRole('list', { name: 'イベントログ' });
  await expect(log.getByText("showToast('Hello from WebView!')")).toBeVisible();
  await expect(log.getByText("handleReturnValue('Hello from Mocked!')")).toBeVisible();
});

test.describe('カラーテーマ', () => {
  const toggle = (page: Page) => page.getByRole('button', { name: 'カラーテーマを切り替える' });

  test('should notify the native side of the initial color scheme', async ({ page }) => {
    await openDemo(page);

    // 初回マウント時点でネイティブに反映されていないと、システムバー周辺の色が食い違う
    await expect.poll(() => appThemeCalls(page)).toEqual(['light']);
  });

  test.describe('システムがダークの場合', () => {
    test.use({ colorScheme: 'dark' });

    test('should switch on the first tap even when mode is still "system"', async ({ page }) => {
      await openDemo(page);

      // mode は 'system' のままだが、適用されている配色はダーク
      await expect.poll(() => appThemeCalls(page)).toEqual(['dark']);
      await expect(page.locator('html')).toHaveClass(/dark/);

      // 初回タップでライトへ切り替わること（mode === 'dark' 判定だとここが反応しなかった）
      await toggle(page).click();
      await expect(page.locator('html')).toHaveClass(/light/);
      await expect.poll(() => appThemeCalls(page)).toEqual(['dark', 'light']);

      await toggle(page).click();
      await expect(page.locator('html')).toHaveClass(/dark/);
      await expect.poll(() => appThemeCalls(page)).toEqual(['dark', 'light', 'dark']);
    });
  });
});
