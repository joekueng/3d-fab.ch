import { expect, test } from '@playwright/test';

test('RENDER-001: root negotiates language and preserves query parameters', async ({ request }) => {
  const preferred = await request.get('/?source=e2e', {
    headers: { 'Accept-Language': 'fr-CH,fr;q=0.9,it;q=0.8', 'User-Agent': 'Playwright E2E' },
    maxRedirects: 0,
  });
  expect(preferred.status()).toBe(302);
  expect(preferred.headers()['location']).toBe('/fr?source=e2e');

  const bot = await request.get('/?source=e2e', {
    headers: { 'Accept-Language': 'de', 'User-Agent': 'Googlebot' },
    maxRedirects: 0,
  });
  expect(bot.status()).toBe(308);
  expect(bot.headers()['location']).toBe('/it?source=e2e');
});

test('RENDER-002: canonical redirects preserve query strings', async ({ request }) => {
  const cases: ReadonlyArray<readonly [string, string]> = [
    ['/calculator?session=test', '/it/calculator/basic?session=test'],
    ['/en/calculator?session=test', '/en/calculator/basic?session=test'],
    ['/it/shop/example/item?campaign=e2e', '/it/shop/p/item?campaign=e2e'],
    ['/zz/about?campaign=e2e', '/it/about?campaign=e2e'],
    ['/de-CH/about?campaign=e2e', '/de/about?campaign=e2e'],
    ['/fr-CH/calculator?campaign=e2e', '/fr/calculator/basic?campaign=e2e'],
    ['/fr/about/?campaign=e2e', '/fr/about?campaign=e2e'],
  ];
  for (const [source, target] of cases) {
    const response = await request.get(source, { maxRedirects: 0 });
    expect(response.status(), source).toBe(308);
    expect(response.headers()['location'], source).toBe(target);
  }
});

test('RENDER-003: localized pages render useful SSR metadata', async ({ request }) => {
  for (const locale of ['it', 'en', 'de', 'fr']) {
    const response = await request.get(`/${locale}/about`);
    expect(response.status()).toBe(200);
    const html = await response.text();
    expect(html).toMatch(/<h1\b[^>]*>[^<]+<\/h1>/i);
    expect(html).toMatch(/<title>[^<]+<\/title>/i);
    expect(html).toContain(`/${locale}/about`);
  }
});

test('RENDER-004: missing catalogue resources return HTTP 404', async ({ request }) => {
  const missing = 'e2e-missing-product-00000000';
  const product = await request.get(`/en/shop/p/${missing}`);
  expect(product.status()).toBe(404);
  expect(product.headers()['cache-control']).toContain('no-store');

  const category = await request.get(`/en/shop/${missing}`);
  expect(category.status()).toBe(404);
  expect(category.headers()['cache-control']).toContain('no-store');
});

test('RENDER-005: unknown application routes return localized 404 pages', async ({ request, page }) => {
  for (const locale of ['it', 'en', 'de', 'fr']) {
    const response = await request.get(`/${locale}/e2e-unknown-route`, { maxRedirects: 0 });
    expect(response.status()).toBe(404);
    expect(response.headers()['location']).toBeUndefined();
    expect(response.headers()['cache-control']).toContain('no-store');
    const html = await response.text();
    const head = html.slice(0, html.indexOf('</head>'));
    expect(html).toContain(`<html lang="${locale}-CH"`);
    expect(head).toContain('name="robots" content="noindex, follow"');
    expect(head).not.toMatch(/<link[^>]+rel="canonical"/);
    expect(head).not.toMatch(/<link[^>]+hreflang=/);
  }

  await page.goto('/de/e2e-unknown-route');
  await expect(page.getByRole('heading', { name: 'Seite nicht gefunden' })).toBeVisible();
  await expect(page.locator('app-not-found .not-found-home')).toHaveAttribute('href', '/de');
  await page.locator('header select.lang-switch').selectOption('fr');
  await expect(page).toHaveURL(/\/fr\/e2e-unknown-route$/);
  await expect(page.getByRole('heading', { name: 'Page introuvable' })).toBeVisible();
  await expect(page.locator('app-not-found .not-found-home')).toHaveAttribute('href', '/fr');
  await expect(page.locator('head link[rel="canonical"]')).toHaveCount(0);
  await page.locator('footer a[href="/fr/privacy"]').click();
  await expect(page.locator('.legal-page h1')).toBeVisible();
  await expect(page.locator('head link[rel="canonical"]')).toHaveAttribute('href', /\/fr\/privacy$/);
  await expect(page.locator('head meta[name="robots"]')).toHaveAttribute('content', 'index, follow');
});

test('RENDER-006: calculator diagnostic page is excluded from indexing', async ({ request }) => {
  const response = await request.get('/en/calculator/animation-test');
  expect(response.status()).toBe(200);
  const html = await response.text();
  expect(html).toMatch(/<meta[^>]+name="robots"[^>]+content="noindex, nofollow"/i);
});
