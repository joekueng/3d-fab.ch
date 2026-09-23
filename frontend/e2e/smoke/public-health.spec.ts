import { expect, test } from '@playwright/test';
import { openNavigation } from '../navigation';

const locales = ['it', 'en', 'de', 'fr'] as const;

test('SMOKE-001: home navigation and static assets are available', async ({ page, request }) => {
  const response = await page.goto('/it');
  expect(response?.status()).toBe(200);
  await expect(page.locator('main h1').first()).toBeVisible();
  await expect(page.locator('footer a[href="/it/privacy"]')).toBeVisible();

  const logo = await request.get('/assets/images/SVG/logo-giallo-spesso.svg');
  expect(logo.status()).toBe(200);
  expect(logo.headers()['content-type']).toContain('image/svg+xml');

  await openNavigation(page);
  await expect(page.locator('header.navbar nav a[href="/it/shop"]')).toBeVisible();
  await page.locator('header.navbar nav a[href="/it/shop"]').click();
  await expect(page).toHaveURL(/\/it\/shop\/?$/);
  await expect(page.locator('header.navbar nav')).not.toHaveClass(/\bopen\b/);
  await expect(page.locator('.shop-hero h1')).toBeVisible();
});

for (const locale of locales) {
  test(`SMOKE-002: ${locale} public pages render translated content`, async ({ page }) => {
    for (const route of ['', '/about', '/materials', '/contact', '/privacy', '/terms']) {
      const response = await page.goto(`/${locale}${route}`);
      expect(response?.status(), `/${locale}${route}`).toBe(200);
      const heading = page.locator('h1').first();
      await expect(heading, `/${locale}${route}`).toBeVisible();
      await expect(heading).not.toHaveText(/^(HOME|ABOUT|MATERIALS|CONTACT|LEGAL)\./);
    }
  });
}

test('SMOKE-003: catalogue and admin login entry points load', async ({ page }) => {
  const shop = await page.goto('/it/shop');
  expect(shop?.status()).toBe(200);
  await expect(page.locator('.shop-hero h1')).toBeVisible();
  await expect(page.locator('.catalog-content')).toBeVisible();

  const login = await page.goto('/it/admin/login');
  expect(login?.status()).toBe(200);
  await expect(page.locator('input[type="password"]')).toBeVisible();
});
