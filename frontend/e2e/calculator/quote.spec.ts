import { expect, test } from '@playwright/test';

// A closed 10 mm cube. Keeping it inline makes the fixture synthetic and reproducible.
const cubeFaces: number[][][] = [
  [[0, 0, 0], [10, 10, 0], [10, 0, 0]], [[0, 0, 0], [0, 10, 0], [10, 10, 0]],
  [[0, 0, 10], [10, 0, 10], [10, 10, 10]], [[0, 0, 10], [10, 10, 10], [0, 10, 10]],
  [[0, 0, 0], [10, 0, 0], [10, 0, 10]], [[0, 0, 0], [10, 0, 10], [0, 0, 10]],
  [[0, 10, 0], [0, 10, 10], [10, 10, 10]], [[0, 10, 0], [10, 10, 10], [10, 10, 0]],
  [[0, 0, 0], [0, 0, 10], [0, 10, 10]], [[0, 0, 0], [0, 10, 10], [0, 10, 0]],
  [[10, 0, 0], [10, 10, 0], [10, 10, 10]], [[10, 0, 0], [10, 10, 10], [10, 0, 10]],
];
const cubeStl = Buffer.from(`solid e2e-cube\n${cubeFaces.map((face) =>
  `facet normal 0 0 0\nouter loop\n${face.map((vertex) => `vertex ${vertex.join(' ')}`).join('\n')}\nendloop\nendfacet`,
).join('\n')}\nendsolid e2e-cube\n`);

async function uploadCube(page: import('@playwright/test').Page) {
  await page.locator('app-upload-form app-dropzone input[type="file"]').setInputFiles({
    name: 'e2e-cube.stl', mimeType: 'model/stl', buffer: cubeStl,
  });
  await expect(page.locator('.file-card .file-name')).toHaveText('e2e-cube.stl');
}

test('CALC-001: calculator redirects to basic and preserves an uploaded draft across modes', async ({ page }) => {
  await page.goto('/it/calculator');
  await expect(page).toHaveURL(/\/it\/calculator\/basic\/?$/);
  await expect(page.locator('app-upload-form app-button[type="submit"] button')).toBeDisabled();
  await uploadCube(page);
  await expect(page.locator('app-upload-form app-button[type="submit"] button')).toBeEnabled();
  await page.locator('.mode-option').last().click();
  await expect(page).toHaveURL(/\/it\/calculator\/advanced\/?$/);
  await expect(page.locator('.file-card .file-name')).toHaveText('e2e-cube.stl');
  await expect(page.locator('app-print-settings')).toBeVisible();
  await page.locator('.mode-option').first().click();
  await expect(page.locator('.file-card .file-name')).toHaveText('e2e-cube.stl');
  await page.locator('.file-card .btn-remove').click();
  await expect(page.locator('.file-card')).toHaveCount(0);
  await expect(page.locator('app-upload-form app-button[type="submit"] button')).toBeDisabled();
});

test('CALC-002: a real model produces a quote and a resumable session', async ({ page, browser }) => {
  test.setTimeout(240_000);
  await page.goto('/it/calculator/basic');
  await uploadCube(page);
  await page.locator('app-upload-form app-button[type="submit"] button').click();
  await expect(page.locator('app-quote-result')).toBeVisible({ timeout: 210_000 });
  await expect(page.locator('app-quote-result .file-name')).toHaveText('e2e-cube.stl');
  await expect(page.locator('app-quote-result app-price-breakdown')).toContainText('CHF');
  await expect(page.locator('app-session-email')).toBeVisible();

  const linkResponse = page.waitForResponse((response) =>
    response.url().includes('/api/quote-sessions/') && response.url().endsWith('/link') && response.request().method() === 'POST',
  );
  await page.locator('app-session-email .session-links app-button').first().click();
  const linkResult = await (await linkResponse).json() as { url: string };
  expect(linkResult.url).toBeTruthy();
  const resumed = await browser.newContext();
  try {
    const resumedPage = await resumed.newPage();
    await resumedPage.goto(new URL(linkResult.url, page.url()).toString());
    await expect(resumedPage.locator('app-quote-result .file-name')).toHaveText('e2e-cube.stl');
  } finally {
    await resumed.close();
  }
});
