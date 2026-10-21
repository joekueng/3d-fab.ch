import { readFile } from 'node:fs/promises';
import { expect, test, type Page } from '@playwright/test';

// Closed cube, exported in metres. Conversion must produce a 10 mm part.
const obj = Buffer.from([
  'mtllib /nonexistent/materials.mtl',
  'v 0 0 0', 'v 0.01 0 0', 'v 0.01 0.01 0', 'v 0 0.01 0',
  'v 0 0 0.01', 'v 0.01 0 0.01', 'v 0.01 0.01 0.01', 'v 0 0.01 0.01',
  'f 1 4 3 2', 'f 5 6 7 8', 'f 1 2 6 5',
  'f 4 8 7 3', 'f 1 5 8 4', 'f 2 3 7 6', '',
].join('\n'));

function dimensions(stl: Buffer): number[] {
  const vertices: number[][] = [];
  const count = stl.length >= 84 ? stl.readUInt32LE(80) : 0;
  if (count > 0 && stl.length === 84 + count * 50) {
    for (let face = 0; face < count; face++) {
      for (let vertex = 0; vertex < 3; vertex++) {
        const offset = 84 + face * 50 + 12 + vertex * 12;
        vertices.push([0, 4, 8].map(axis => stl.readFloatLE(offset + axis)));
      }
    }
  } else {
    for (const match of stl.toString().matchAll(/vertex\s+(\S+)\s+(\S+)\s+(\S+)/g)) {
      vertices.push(match.slice(1, 4).map(Number));
    }
  }
  expect(vertices.length).toBeGreaterThan(0);
  return [0, 1, 2].map(axis =>
    Math.max(...vertices.map(v => v[axis])) - Math.min(...vertices.map(v => v[axis])),
  );
}

async function upload(page: Page, name: string, buffer: Buffer) {
  const preview = page.waitForResponse(response =>
    response.url().endsWith('/api/model-previews') && response.request().method() === 'POST',
  );
  await page.locator('app-upload-form app-dropzone input[type="file"]').setInputFiles({
    name, mimeType: 'application/octet-stream', buffer,
  });
  await expect(page.locator('.file-card .file-name')).toHaveText(name);
  return preview;
}

async function calculate(page: Page) {
  const created = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/quote-sessions' && response.request().method() === 'POST',
  );
  const processed = page.waitForResponse(response =>
    /\/api\/quote-sessions\/[^/]+\/line-items$/.test(new URL(response.url()).pathname)
      && response.request().method() === 'POST', { timeout: 210_000 },
  );
  await page.locator('app-upload-form app-button[type="submit"] button').click();
  const sessionResponse = await created;
  expect(sessionResponse.ok()).toBe(true);
  const response = await processed;
  expect(response.ok()).toBe(true);
  const sessionId = new URL(response.url()).pathname.split('/')[3];
  const snapshot = await page.request.get(`/api/quote-sessions/${sessionId}`);
  expect(snapshot.ok()).toBe(true);
  const data = await snapshot.json() as {
    items: { id: string; status: string; modelUnit: string; errorCode?: string }[];
  };
  expect(data.items).toHaveLength(1);
  return { sessionId, item: data.items[0] };
}

test('CALC-003: real OBJ recovers from wrong units and retains size, original and preview after resume', async ({ page, browser }) => {
  test.setTimeout(300_000);
  await page.goto('/it/calculator/basic');
  const preview = await upload(page, 'metres.obj', obj);
  expect(preview.ok()).toBe(true);
  for (const side of dimensions(await preview.body())) expect(side).toBeCloseTo(0.01, 5);
  const small = await calculate(page);
  expect(small.item.status).toBe('REVIEW_REQUIRED');
  expect(small.item.errorCode).toBe('MODEL_TOO_SMALL');
  await expect(page.locator('app-upload-form app-button[type="submit"] button')).toBeEnabled();
  const rescaled = page.waitForResponse(response => response.url().endsWith('/api/model-previews'));
  await page.locator('.model-unit-field select').selectOption({ label: 'm' });
  const rescaledResponse = await rescaled;
  expect(rescaledResponse.ok()).toBe(true);
  for (const side of dimensions(await rescaledResponse.body())) expect(side).toBeCloseTo(10, 3);

  const result = await calculate(page);
  expect(result.sessionId).toBe(small.sessionId);
  expect(result.item.status).toBe('READY');
  expect(result.item.modelUnit).toBe('m');
  await expect(page.locator('app-quote-result')).toBeVisible();
  const base = `/api/quote-sessions/${result.sessionId}/line-items/${result.item.id}`;
  expect(await (await page.request.get(`${base}/content`)).body()).toEqual(obj);
  const storedPreview = await page.request.get(`${base}/stl-preview`);
  expect(storedPreview.ok()).toBe(true);
  for (const side of dimensions(await storedPreview.body())) expect(side).toBeCloseTo(10, 3);

  const context = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const resumed = await context.newPage();
    const errors: string[] = [];
    resumed.on('pageerror', error => errors.push(error.message));
    await resumed.goto(`/it/calculator/basic?session=${result.sessionId}`);
    await expect(resumed.locator('.file-card .file-name')).toHaveText('metres.obj');
    await expect(resumed.locator('.model-unit-field select option:checked')).toHaveText('m');
    await expect(resumed.locator('app-stl-viewer canvas')).toBeVisible();
    await expect(resumed.locator('app-quote-result')).toBeVisible();
    expect(errors).toEqual([]);
  } finally {
    await context.close();
  }
});

for (const extension of ['step', 'stp']) {
  test(`CALC-004: real ${extension.toUpperCase()} preview and quote preserve millimetres and original CAD`, async ({ page }) => {
    test.setTimeout(240_000);
    const original = await readFile('e2e/fixtures/cube.step');
    await page.goto('/it/calculator/basic');
    const preview = await upload(page, `cube.${extension}`, original);
    expect(preview.ok()).toBe(true);
    for (const side of dimensions(await preview.body())) expect(side).toBeCloseTo(10, 3);
    await expect(page.locator('app-stl-viewer canvas')).toBeVisible();
    await expect(page.locator('.model-unit-field')).toHaveCount(0);
    const { sessionId, item } = await calculate(page);
    expect(item.status).toBe('READY');
    const content = await page.request.get(`/api/quote-sessions/${sessionId}/line-items/${item.id}/content`);
    expect(content.ok()).toBe(true);
    expect(await content.body()).toEqual(original);
  });
}

test('CALC-005: invalid STEP remains available for review after preview and quote conversion failures', async ({ page }) => {
  test.setTimeout(90_000);
  await page.goto('/it/calculator/basic');
  const original = Buffer.from('invalid synthetic CAD');
  const preview = await upload(page, 'broken.step', original);
  expect(preview.status()).toBe(422);
  await expect(page.locator('.preview-status')).toBeVisible();
  const { sessionId, item } = await calculate(page);
  expect(item.status).toBe('REVIEW_REQUIRED');
  expect(item.errorCode).toBe('MODEL_CONVERSION_FAILED');
  await expect(page.locator('.file-card .file-name')).toHaveText('broken.step');
  const content = await page.request.get(`/api/quote-sessions/${sessionId}/line-items/${item.id}/content`);
  expect(await content.body()).toEqual(original);
});
