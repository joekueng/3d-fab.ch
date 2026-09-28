import { expect, test } from '@playwright/test';

test('CHECKOUT-001: a shop cart reaches checkout, validates the form and creates an order', async ({ page }) => {
  await page.goto('/it/shop');
  const firstProduct = page.locator('app-product-card').first();
  await expect(firstProduct).toBeVisible();
  await firstProduct.locator('.cart-btn').click();
  await expect(page.locator('.cart-line')).toHaveCount(1);
  await page.locator('.cart-card app-button').click();
  await expect(page).toHaveURL(/\/it\/checkout\?session=[^&]+/);

  const form = page.locator('.checkout-form-section form');
  await expect(form).toBeVisible();
  const submit = form.locator('app-button[type="submit"] button');
  await expect(submit).toBeDisabled();

  await form.locator('app-input[formcontrolname="email"] input').fill(`e2e-${Date.now()}@example.invalid`);
  await form.locator('app-input[formcontrolname="phone"] input').fill('+41 79 000 00 00');
  const billing = form.locator('[formgroupname="billingAddress"]');
  await billing.locator('app-input[formcontrolname="firstName"] input').fill('E2E');
  await billing.locator('app-input[formcontrolname="lastName"] input').fill('Customer');
  await billing.locator('app-input[formcontrolname="addressLine1"] input').fill('Teststrasse 1');
  await billing.locator('app-input[formcontrolname="zip"] input').fill('8000');
  await billing.locator('app-input[formcontrolname="city"] input').fill('Zürich');
  await form.locator('app-legal-consent input[type="checkbox"]').check();
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page).toHaveURL(/\/it\/order\/[^/?#]+/);
  await expect(page.locator('.order-id-title')).toBeVisible();
  await expect(page.locator('.order-item')).toHaveCount(1);
});
