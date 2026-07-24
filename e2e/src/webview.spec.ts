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
      // 実際にページを再読み込みするとモックが消えるため、呼び出しの記録だけを行う
      reloadPage: () => {
        (window as any).__pageCalls = [...((window as any).__pageCalls ?? []), 'reloadPage'];
      },
      simulateLoadError: () => {
        (window as any).__pageCalls = [...((window as any).__pageCalls ?? []), 'simulateLoadError'];
      },
      // 外部サイトの開き方は複数あり、どれが指定されたかを記録する
      openExternalLink: (url: string, mode: string) => {
        (window as any).__externalCalls = [...((window as any).__externalCalls ?? []), [mode, url]];
      },
    };
  });
});

const appThemeCalls = (page: Page) => page.evaluate(() => (window as any).__appThemeCalls ?? []);
const pageCalls = (page: Page) => page.evaluate(() => (window as any).__pageCalls ?? []);
const externalCalls = (page: Page) => page.evaluate(() => (window as any).__externalCalls ?? []);

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

test('should expose the phone number as a tel: link', async ({ page }) => {
  await openDemo(page);

  // ネイティブは shouldOverrideUrlLoading でこの href を受け取り、電話アプリに渡す
  const phone = page.getByRole('link', { name: '03-1234-5678' });
  await expect(phone).toHaveAttribute('href', 'tel:+81312345678');

  // ブラウザでは tel: に遷移しないよう、クリックはせず記録だけ確認する
  await phone.evaluate((el) => el.dispatchEvent(new MouseEvent('click', { bubbles: true })));
  await expect(page.getByRole('list', { name: 'イベントログ' }).getByText('tel:+81312345678')).toBeVisible();
});

test.describe('リンクの種類', () => {
  // ネイティブは shouldOverrideUrlLoading でこれらを受け取り、対応するアプリに渡す
  const CASES: Array<[string, string]> = [
    ['メールを作成', 'mailto:support@example.com?subject=WebView%20Interaction%20Sample'],
    ['SMS を作成', 'sms:+81312345678?body=WebView%20Interaction%20Sample'],
    ['地図で開く', 'geo:35.681236,139.767125?q=東京駅'],
  ];

  for (const [label, href] of CASES) {
    test(`should expose "${label}" as a link`, async ({ page }) => {
      await openDemo(page);
      await expect(page.getByRole('link', { name: new RegExp(label) })).toHaveAttribute('href', href);
    });
  }
});

test.describe('外部リンクの開き方', () => {
  const EXTERNAL_URL = 'https://developer.android.com/develop/ui/views/layout/webapps/webview';
  const APP_LINK_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
  // TWA は自サイトを開く（assetlinks.json で検証できるのが自分のオリジンだけのため）
  const OWN_SITE_URL = 'https://webview-interaction-sample.demo.gekal.cn/twa.html';

  // ボタンのラベル -> ネイティブに渡す ExternalOpenMode と URL
  const MODES: Array<[string, string, string]> = [
    ['アプリ内オーバーレイ', 'IN_APP_OVERLAY', EXTERNAL_URL],
    ['Custom Tabs（全画面）', 'CUSTOM_TAB', EXTERNAL_URL],
    ['Custom Tabs（部分表示）', 'PARTIAL_CUSTOM_TAB', EXTERNAL_URL],
    ['Custom Tabs（事前ウォームアップ）', 'WARMED_CUSTOM_TAB', EXTERNAL_URL],
    ['対応アプリで開く（App Links）', 'APP_LINK', APP_LINK_URL],
    ['アプリを選ばせる', 'BROWSER_CHOOSER', EXTERNAL_URL],
    ['別タスクとして開く', 'NEW_DOCUMENT', EXTERNAL_URL],
    ['intent:// で開く', 'INTENT_URI', ''],
    ['Trusted Web Activity', 'TRUSTED_WEB_ACTIVITY', OWN_SITE_URL],
  ];

  for (const [label, mode, url] of MODES) {
    test(`should pass ${mode} to the native side`, async ({ page }) => {
      await openDemo(page);

      await page.getByRole('button', { name: label, exact: true }).click();

      await expect.poll(() => externalCalls(page)).toHaveLength(1);
      const calls = await externalCalls(page);

      expect(calls[0][0]).toBe(mode);
      if (url) {
        expect(calls[0][1]).toBe(url);
      } else {
        // intent:// はフォールバック URL を含む
        expect(calls[0][1]).toContain('intent://');
      }
    });
  }

  // window.open の戻り値は Android WebView とブラウザで挙動が違う（実機は null になりうる）。
  // 挙動に依存せずロジックを検証するため、window.open をスタブして戻り値を固定する。
  test('should not warn when the bridge is present even if window.open returns null', async ({ page }) => {
    // ブリッジあり（＝WebView 相当）。ネイティブが onCreateWindow で処理し、戻り値は null になりうる
    await page.addInitScript(() => {
      window.open = () => null;
    });
    await openDemo(page);

    await page.getByRole('button', { name: 'window.open() で開く' }).click();

    // 呼び出しはログに残るが、戻り値が null でも誤ってブロック警告を出さない。
    // log() と notify() は同じハンドラ内で呼ばれ同時に描画されるため、ログが出た時点で
    // 警告があるかを一発（リトライなし）で判定する。自動で消える前に確実に捕まえる。
    await expect(page.getByRole('list', { name: 'イベントログ' }).getByText('window.open(')).toBeVisible();
    expect(await page.getByText('ブロックされました').count()).toBe(0);
  });

  test('should warn about a real popup block only in a plain browser', async ({ page }) => {
    // ブリッジなし（素のブラウザ）で、window.open がブロックされて null を返す状況
    await page.addInitScript(() => {
      delete (window as any).AndroidInterface;
      window.open = () => null;
    });
    await page.goto(WEBVIEW_URL);

    await page.getByRole('button', { name: 'window.open() で開く' }).click();

    await expect(page.getByText('ブラウザにポップアップをブロックされました')).toBeVisible();
  });

  test('should not warn in a plain browser when the popup opens', async ({ page }) => {
    // ブリッジなしでも、window.open が成功（非 null）ならブロック警告は出さない
    await page.addInitScript(() => {
      delete (window as any).AndroidInterface;
      window.open = () => ({ closed: false }) as unknown as Window;
    });
    await page.goto(WEBVIEW_URL);

    await page.getByRole('button', { name: 'window.open() で開く' }).click();

    await expect(page.getByRole('list', { name: 'イベントログ' }).getByText('window.open(')).toBeVisible();
    expect(await page.getByText('ブロックされました').count()).toBe(0);
  });
});

test.describe('vConsole', () => {
  const withQuery = (q: string) => WEBVIEW_URL + (WEBVIEW_URL.includes('?') ? '&' : '?') + q;

  test('should show vConsole when explicitly enabled', async ({ page }) => {
    await page.goto(withQuery('vconsole=1'));

    // vConsole のフローティングボタンが出る
    await expect(page.locator('.vc-switch')).toBeVisible();
  });

  test('should not show vConsole by default', async ({ page }) => {
    await openDemo(page);

    // 動的読込のため少し待ってから、出ていないことを確認する
    await expect(page.getByRole('button', { name: 'Show Toast' })).toBeEnabled();
    expect(await page.locator('.vc-switch').count()).toBe(0);
  });

  test('should stay off when only env=debug is set', async ({ page }) => {
    // 有効・無効はビルド時フラグ（?vconsole=）だけで決まる。env=debug では自動有効にしない
    await page.goto(withQuery('env=debug'));

    await expect(page.getByRole('button', { name: 'Show Toast' })).toBeEnabled();
    expect(await page.locator('.vc-switch').count()).toBe(0);
  });

  test('should stay off when explicitly disabled', async ({ page }) => {
    await page.goto(withQuery('vconsole=0'));

    await expect(page.getByRole('button', { name: 'Show Toast' })).toBeEnabled();
    expect(await page.locator('.vc-switch').count()).toBe(0);
  });
});

test.describe('TWA 判定ページ', () => {
  const twaUrl = () => WEBVIEW_URL.replace(/index\.html.*$/, 'twa.html');

  // このページは TWA / Custom Tabs として Chrome 側で開かれるため、ブリッジは注入されない
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      delete (window as any).AndroidInterface;
    });
  });

  test('should report that it is not running as a TWA in a browser', async ({ page }) => {
    await page.goto(twaUrl());

    // 通常のブラウザでは standalone でも android-app:// でもない
    await expect(page.getByText('TWA としては表示されていません')).toBeVisible();
    await expect(page.getByText('display-mode: standalone')).toBeVisible();
    await expect(page.getByRole('link', { name: '/.well-known/assetlinks.json' })).toBeVisible();
  });

  test('should detect a TWA launch from the referrer and display mode', async ({ page }) => {
    // TWA からの起動を模して、判定に使う 2 つの値を差し替える
    await page.addInitScript(() => {
      Object.defineProperty(document, 'referrer', {
        get: () => 'android-app://cn.gekal.android.myapplicationwebviewinteractionsample',
      });
      const original = window.matchMedia.bind(window);
      window.matchMedia = ((query: string) =>
        query === '(display-mode: standalone)'
          ? ({ matches: true, media: query, addEventListener() {}, removeEventListener() {} } as any)
          : original(query)) as typeof window.matchMedia;
    });

    await page.goto(twaUrl());

    await expect(page.getByText('検証済みの TWA として表示中')).toBeVisible();
    await expect(page.getByText('起動元: cn.gekal.android.myapplicationwebviewinteractionsample')).toBeVisible();

    // TWA として開かれている場合、戻るボタンは「閉じる」動作になる
    await expect(page.getByRole('button', { name: 'アプリに戻る' })).toBeVisible();
  });

  test('should fall back to navigation when the page cannot close itself', async ({ page }) => {
    await page.goto(twaUrl());

    // アプリから開かれていなければ閉じる対象がないため、そのまま遷移する
    const back = page.getByRole('button', { name: 'デモ画面に戻る' });
    await expect(back).toBeEnabled();
    await back.click();

    await expect(page.getByRole('button', { name: 'Show Toast' })).toBeVisible();
  });
});

test.describe('ページの読み込み', () => {
  test('should ask the native side to reload the page', async ({ page }) => {
    await openDemo(page);

    await page.getByRole('button', { name: '再読み込み' }).click();

    await expect.poll(() => pageCalls(page)).toEqual(['reloadPage']);
    await expect(page.getByRole('list', { name: 'イベントログ' }).getByText('reloadPage()')).toBeVisible();
  });

  test('should ask the native side to show the error screen', async ({ page }) => {
    await openDemo(page);

    await page.getByRole('button', { name: '読み込みエラーを再現' }).click();

    await expect.poll(() => pageCalls(page)).toEqual(['simulateLoadError']);
  });
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
