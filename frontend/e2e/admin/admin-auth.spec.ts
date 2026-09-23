import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './login';

const adminPages = [
  ['orders', 'Ordini'],
  ['filament-stock', 'Stock filamenti'],
  ['contact-requests', 'Richieste di contatto'],
  ['sessions', 'Sessioni quote'],
  ['cad-invoices', 'Fatture CAD'],
  ['qr', 'QR tracciati'],
  ['media', 'Media'],
  ['home-projects', 'Progetti home'],
  ['shop', 'Catalogo prodotti'],
  ['linkedin', 'Sincronizzazione LinkedIn'],
] as const;

test('ADMIN-001: anonymous direct navigation redirects to login', async ({ page }) => {
  await page.goto('/it/admin/qr');
  await expect(page).toHaveURL(/\/it\/admin\/login\/?$/);
  await expect(page.getByRole('heading', { name: 'Back-office' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'QR tracciati' })).toHaveCount(0);
});

test('ADMIN-002: UI login opens every admin route, survives reload, and logout removes access', async ({ page }) => {
  await loginAsAdmin(page);

  await page.goto('/it/admin');
  await expect(page).toHaveURL(/\/it\/admin\/orders\/?$/);

  for (const [route, heading] of adminPages) {
    await page.goto(`/it/admin/${route}`);
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  }

  await page.goto('/it/admin/home-media');
  await expect(page).toHaveURL(/\/it\/admin\/media\/?$/);
  await expect(page.getByRole('heading', { name: 'Media', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Media', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/it\/admin\/login\/?$/);
  await page.goto('/it/admin/orders');
  await expect(page).toHaveURL(/\/it\/admin\/login\/?$/);
});

test('ADMIN-003: an invalidated session is rejected on reload', async ({ page, context }) => {
  await loginAsAdmin(page);
  await context.clearCookies();
  await page.reload();
  await expect(page).toHaveURL(/\/it\/admin\/login\/?$/);
  await expect(page.getByRole('heading', { name: 'Ordini', exact: true })).toHaveCount(0);
});
