import { expect, test } from '@playwright/test';

function requiredFixture(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Fullstack price assertion requires ${name} from the isolated seed`);
  return value;
}

function chfCents(text: string): number {
  const amount = text.match(/[0-9'’]+(?:[.,][0-9]{2})?/);
  if (!amount) throw new Error(`No CHF amount in: ${text}`);
  return Math.round(Number(amount[0].replace(/['’]/g, '').replace(',', '.')) * 100);
}

test('SHOP-003: the seeded default variant and cart have independently specified CHF totals', async ({ page }) => {
  const unitCents = Math.round(Number(requiredFixture('E2E_SHOP_UNIT_PRICE_CHF')) * 100);
  const totalCents = Math.round(Number(requiredFixture('E2E_SHOP_TWO_ITEM_TOTAL_CHF')) * 100);
  expect(unitCents).toBeGreaterThan(0);
  expect(totalCents).toBeGreaterThan(unitCents * 2 - 1);

  await page.goto('/it/shop');
  const productLink = page.locator('app-product-card .name a').first();
  await expect(productLink).toBeVisible();
  await productLink.click();
  await expect(page.locator('.product-page h1')).toBeVisible();
  expect(chfCents(await page.locator('.offer-price h3').innerText())).toBe(unitCents);
  await page.locator('.quantity-card .qty-control button').last().click();
  await expect(page.locator('.quantity-card .qty-control span')).toHaveText('2');
  await page.getByRole('button', { name: 'Aggiungi al Carrello' }).click();
  await page.getByRole('button', { name: 'Vai al checkout' }).click();
  await expect(page).toHaveURL(/\/it\/checkout\?session=/);
  await expect(page.locator('.summary-item').first()).toBeVisible();
  expect(chfCents(await page.locator('.summary-item .item-total-price').first().innerText())).toBe(unitCents * 2);
  expect(chfCents(await page.locator('app-price-breakdown .price-total').innerText())).toBe(totalCents);
});
