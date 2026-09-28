import { expect, test } from '@playwright/test';

test('a visitor opens the home page and navigates to the shop', async ({ page }) => {
  await test.step('open the Italian home page', async () => {
    const response = await page.goto('/it');

    expect(response?.status()).toBe(200);
    await expect(page.locator('main h1').first()).toBeVisible();
  });

  await test.step('click the shop button and verify the shop page', async () => {
    await page.getByRole('button', { name: 'Vai allo shop' }).click();
    await expect(page).toHaveURL(/\/it\/shop\/?$/);
    await expect(page.locator('main h1').first()).toBeVisible();
  });
});
