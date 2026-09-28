import { expect, type Page } from '@playwright/test';

export async function loginAsAdmin(page: Page): Promise<void> {
  const password = process.env['E2E_ADMIN_PASSWORD'];
  if (!password) {
    throw new Error('E2E_ADMIN_PASSWORD is required for fullstack admin tests');
  }

  await page.goto('/it/admin/login');
  await page.waitForLoadState('networkidle');
  const passwordInput = page.locator('input#admin-password');
  await passwordInput.fill(password);
  await expect(passwordInput).toHaveValue(password);
  const loginButton = page.getByRole('button', { name: 'Accedi' });
  await expect(loginButton).toBeEnabled();
  await loginButton.click();
  await expect(page).toHaveURL(/\/it\/admin\/orders\/?$/);
  await expect(page.getByRole('heading', { name: 'Ordini', exact: true })).toBeVisible();
}
