import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './login';

test('ADMIN-FILAMENT-001: create, edit, cancel deletion, and delete a test variant', async ({ page }) => {
  if (process.env['E2E_DISPOSABLE_STACK'] !== '1') {
    throw new Error('Filament mutation requires E2E_DISPOSABLE_STACK=1 from scripts/e2e/run.sh');
  }

  await loginAsAdmin(page);
  await page.goto('/it/admin/filament-stock');
  await expect(page.getByRole('heading', { name: 'Stock filamenti' })).toBeVisible();

  const creator = page.locator('section.subpanel').filter({ has: page.getByRole('heading', { name: 'Nuova variante' }) });
  const add = creator.getByRole('button', { name: 'Aggiungi variante' });
  await expect(add).toBeEnabled(); // The isolated baseline must include a material.

  const name = `E2E variant ${randomUUID().slice(0, 12)}`;
  await creator.getByRole('textbox', { name: 'Nome variante' }).fill(name);
  await creator.getByRole('textbox', { name: 'Colore', exact: true }).fill('Blu');
  await creator.getByRole('textbox', { name: 'Hex colore' }).fill('#1234AB');
  await creator.getByRole('spinbutton', { name: 'Costo CHF/kg' }).fill('25.50');
  await creator.getByRole('spinbutton', { name: 'Stock spool' }).fill('2');
  await creator.getByRole('checkbox', { name: 'Attiva' }).uncheck();
  await add.click();
  await expect(page.getByText('Variante aggiunta.')).toBeVisible();

  let row = page.locator('.variant-row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await page.reload();
  row = page.locator('.variant-row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await row.locator('.expand-toggle').click();
  await expect(row.getByRole('checkbox', { name: 'Attiva' })).not.toBeChecked();
  await expect(row.getByRole('spinbutton', { name: 'Stock spool' })).toHaveValue('2');

  await row.getByRole('spinbutton', { name: 'Stock spool' }).fill('2.5');
  await row.getByRole('button', { name: 'Salva variante' }).click();
  await expect(page.getByText('Variante aggiornata.')).toBeVisible();
  await page.reload();
  row = page.locator('.variant-row').filter({ hasText: name });
  await row.locator('.expand-toggle').click();
  await expect(row.getByRole('spinbutton', { name: 'Stock spool' })).toHaveValue('2.5');

  await row.getByRole('button', { name: 'Elimina' }).click();
  const dialog = page.getByRole('dialog', { name: 'Sei sicuro?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Annulla' }).first().click();
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Elimina' }).click();
  await page.getByRole('dialog', { name: 'Sei sicuro?' }).getByRole('button', { name: 'Conferma elimina' }).click();
  await expect(page.getByText('Variante eliminata.')).toBeVisible();
  await expect(page.locator('.variant-row').filter({ hasText: name })).toHaveCount(0);
  await page.reload();
  await expect(page.locator('.variant-row').filter({ hasText: name })).toHaveCount(0);
});
