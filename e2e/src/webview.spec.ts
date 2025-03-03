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

test('should be able to run tests', async ({ page }) => {
  await page.goto(process.env.WEBVIEW_URL);

  await page.locator('button[onclick="showToast()"]').click();

  await page
    .locator('#message')
    .innerText()
    .then((message) => expect(message).toBe('Received: Hello from Mocked!'));
});
