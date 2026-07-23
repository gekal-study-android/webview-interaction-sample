import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    // Native側のAndroidInterface#showToastをモックする
    (window as any).AndroidInterface = {
      showToast: (message: string) => {
        console.log(
          `Mocked AndroidInterface.showToast called with: ${message}`,
        );
        // Webviewのスクリプトを評価する
        (window as any).handleReturnValue('Hello from Mocked!');
      },
    };
  });
});

test('should be able to run tests', async ({ page }, testInfo) => {
  await page.goto(process.env.WEBVIEW_URL);
  await page.screenshot({ path: `test-results/screenshots/${testInfo.title}/${testInfo.project.name}/after-loading.png` });

  // ハイドレーションが完了するとボタンが有効になる（click は enabled になるまで待機する）
  await page.getByRole('button', { name: 'Show Toast' }).click();
  await page.screenshot({ path: `test-results/screenshots/${testInfo.title}/${testInfo.project.name}/after-click.png` });

  await expect(page.locator('#message')).toHaveText('Received: Hello from Mocked!');
});
