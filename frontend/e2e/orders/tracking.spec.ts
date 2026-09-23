import { expect, test } from '@playwright/test';

function pendingOrderId(): string {
  const id = process.env['E2E_ORDER_PENDING_ID'];
  if (!id) throw new Error('Fullstack order tracking requires E2E_ORDER_PENDING_ID from the isolated seed');
  return id;
}

test('ORDER-001: a pending order can report payment once and retains its summary', async ({ page }) => {
  await page.goto(`/it/order/${encodeURIComponent(pendingOrderId())}`);
  await expect(page.locator('.order-id-title')).toBeVisible();
  await expect(page.locator('.order-item').first()).toBeVisible();
  await expect(page.locator('.payment-main')).toBeVisible();

  await page.locator('.payment-selection .ui-choice-card').first().click();
  const reportButton = page.locator('.payment-main .ui-actions app-button button');
  await expect(reportButton).toBeEnabled();
  await reportButton.click();
  await expect(page.locator('.status-reported-card')).toBeVisible();
  await expect(reportButton).toBeDisabled();
  await page.reload();
  await expect(page.locator('.status-reported-card')).toBeVisible();
  await expect(page.locator('.order-item').first()).toBeVisible();
});

test('ORDER-002: the short customer order URL displays the same tracking record', async ({ page }) => {
  await page.goto(`/it/co/${encodeURIComponent(pendingOrderId())}?source=e2e#details`);
  await expect(page).toHaveURL(/\/it\/(co|order)\//);
  await expect(page.locator('.order-id-title')).toBeVisible();
});
