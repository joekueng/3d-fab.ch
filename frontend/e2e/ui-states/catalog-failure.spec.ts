import { expect, test } from '@playwright/test';

test('UI-001: simulated catalogue outage shows feedback and recovers on navigation', async ({ page }) => {
  await page.goto('/it');
  await page.route('**/api/shop/products**', async (route) => {
    await route.fulfill({ status: 503, contentType: 'application/json', body: '{"error":"simulated outage"}' });
  });

  await page.locator('header.navbar nav a[href="/it/shop"]').click();
  await expect(page.locator('.catalog-state-error')).toBeVisible();
  await expect(page.locator('app-product-card')).toHaveCount(0);

  await page.unroute('**/api/shop/products**');
  await page.goto('/it');
  await page.locator('header.navbar nav a[href="/it/shop"]').click();
  await expect(page.locator('app-product-card').first()).toBeVisible();
  await expect(page.locator('.catalog-state-error')).toHaveCount(0);
});
