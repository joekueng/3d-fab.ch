import { expect, test } from '@playwright/test';

test('SHOP-001: an active product can be added to the cart and survives reload', async ({ page }) => {
  await page.goto('/it/shop');
  const firstProduct = page.locator('app-product-card').first();
  await expect(firstProduct).toBeVisible();
  await firstProduct.locator('.cart-btn').click();

  const cartLine = page.locator('.cart-line');
  await expect(cartLine).toBeVisible();
  await expect(cartLine.locator('.qty-control span')).toHaveText('1');
  await cartLine.locator('.qty-control button').last().click();
  await expect(cartLine.locator('.qty-control span')).toHaveText('2');

  await page.reload();
  await expect(page.locator('.cart-line .qty-control span')).toHaveText('2');
  await page.locator('.cart-line .line-remove').click();
  await expect(page.locator('.cart-line')).toHaveCount(0);
});

test('SHOP-002: catalogue detail, category and legacy product paths resolve', async ({ page }) => {
  await page.goto('/it/shop');
  const productLink = page.locator('app-product-card .name a').first();
  await expect(productLink).toBeVisible();
  const productName = (await productLink.innerText()).trim();
  const detailPath = await productLink.getAttribute('href');
  expect(detailPath).toBeTruthy();
  await productLink.click();
  await expect(page.locator('.product-page h1')).toHaveText(productName);
  await expect(page.locator('.purchase-card .offer-price h3')).toContainText('CHF');

  const categoryPath = await page.locator('.breadcrumbs__item').last().getAttribute('href');
  expect(categoryPath).toBeTruthy();
  await page.goto(categoryPath!);
  await expect(page.locator('app-product-card').first()).toBeVisible();

  const slug = detailPath!.split('/').filter(Boolean).at(-1);
  expect(slug).toBeTruthy();
  await page.goto(`/it/shop/p/${slug}`);
  await expect(page.locator('.product-page h1')).toHaveText(productName);
});
