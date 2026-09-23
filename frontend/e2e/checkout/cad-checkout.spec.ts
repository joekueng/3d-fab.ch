import { expect, test } from '@playwright/test';

test('CAD-001: a seeded CAD quote opens its checkout and updates quantity', async ({ page }) => {
  const sessionId = process.env['E2E_CAD_SESSION_ID'];
  if (!sessionId) throw new Error('Fullstack CAD checkout requires E2E_CAD_SESSION_ID from the isolated seed');

  await page.goto(`/it/checkout/cad?session=${encodeURIComponent(sessionId)}`);
  await expect(page.locator('.checkout-form-section form')).toBeVisible();
  await expect(page.locator('.cad-summary-item')).toBeVisible();
  const cadItem = page.locator('.summary-item').filter({ has: page.locator('app-print-item-controls.cad-item-controls') }).first();
  await expect(cadItem).toBeVisible();
  const quantity = cadItem.locator('input.qty-input');
  const originalQuantity = Number(await quantity.inputValue());
  const originalTotal = await cadItem.locator('.item-total-price').innerText();
  await quantity.fill(String(originalQuantity + 1));
  await quantity.blur();
  await expect(quantity).toHaveValue(String(originalQuantity + 1));
  await expect(cadItem.locator('.item-total-price')).not.toHaveText(originalTotal);
  await page.reload();
  await expect(cadItem.locator('input.qty-input')).toHaveValue(String(originalQuantity + 1));
});
