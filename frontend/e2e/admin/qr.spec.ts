import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './login';

test('ADMIN-QR-001: QR editor validates fields and persists a disabled test link', async ({ page }) => {
  if (process.env['E2E_DISPOSABLE_STACK'] !== '1') {
    throw new Error('QR mutation requires E2E_DISPOSABLE_STACK=1 from scripts/e2e/run.sh');
  }
  await loginAsAdmin(page);
  await page.goto('/it/admin/qr');
  await expect(page.getByRole('heading', { name: 'QR tracciati' })).toBeVisible();

  await page.getByRole('button', { name: 'Nuovo QR' }).click();
  await page.getByRole('button', { name: 'Salva', exact: true }).click();
  await expect(page.getByText('Nome, slug e target path sono obbligatori.')).toBeVisible();

  const slug = `e2e-${randomUUID().slice(0, 12)}`;
  const name = `E2E QR ${slug}`;
  await page.getByRole('textbox', { name: 'Nome', exact: true }).fill(name);
  await page.getByRole('textbox', { name: 'Slug' }).fill(slug);
  await page.getByRole('textbox', { name: 'Target path' }).fill('/contact');
  await page.getByRole('checkbox', { name: /QR attivo/ }).uncheck();
  const createResponse = page.waitForResponse((response) =>
    response.url().endsWith('/api/admin/qr-links') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Salva', exact: true }).click();
  expect((await createResponse).ok()).toBe(true);
  await expect(page.locator('.qr-list').getByRole('button', { name: new RegExp(slug) })).toContainText('Disattivo');

  await page.reload();
  await page.getByText('Gestione QR', { exact: true }).click();
  const saved = page.locator('.qr-list').getByRole('button', { name: new RegExp(slug) });
  await expect(saved).toContainText(name);
  await expect(saved).toContainText('Disattivo');
  await saved.click();
  await expect(page.getByRole('textbox', { name: 'Target path' })).toHaveValue('/contact');
  await expect(page.getByRole('checkbox', { name: /QR attivo/ })).not.toBeChecked();

  await page.getByRole('textbox', { name: 'Note interne' }).fill('Created by isolated E2E run');
  const updateResponse = page.waitForResponse((response) =>
    response.url().includes('/api/admin/qr-links/') && response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: 'Salva', exact: true }).click();
  expect((await updateResponse).ok()).toBe(true);
  await page.reload();
  await page.getByText('Gestione QR', { exact: true }).click();
  await page.locator('.qr-list').getByRole('button', { name: new RegExp(slug) }).click();
  await expect(page.getByRole('textbox', { name: 'Note interne' })).toHaveValue('Created by isolated E2E run');
});
