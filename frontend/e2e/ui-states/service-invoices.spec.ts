import { expect, test } from '@playwright/test';
import { AdminCadInvoice, AdminCreateCadInvoicePayload } from '../../src/app/features/admin/services/admin-operations.service';

test('UI-INVOICE-001: simulated API supports custom rows, grouping and interim invoices', async ({ page }, testInfo) => {
  const invoices: AdminCadInvoice[] = [];
  const failures: string[] = [];
  page.on('pageerror', error => failures.push(error.message));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/auth/me')) {
      return route.fulfill({ json: { authenticated: true } });
    }
    if (url.pathname.endsWith('/cad-invoices')) {
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as AdminCreateCadInvoicePayload;
        expect(body.serviceLines).toHaveLength(2);
        expect(body.serviceLines?.[0].quantity).toBe(1.25);
        expect(body.serviceLines?.[1].billingType).toBe('FIXED');
        expect(body.serviceLines?.[1].quantity).toBe(1);
        expect(body.cadHours).toBeUndefined();
        const row: AdminCadInvoice = {
          ...body, sessionId: '11111111-1111-1111-1111-111111111111', sessionStatus: 'CAD_ACTIVE',
          cadHours: 0, cadHourlyRateChf: 0, cadTotalChf: 0,
          printItemsTotalChf: 0, setupCostChf: 0, shippingCostChf: 0, grandTotalChf: 150,
          checkoutPath: '/checkout/cad?session=test', createdAt: '2026-09-28T12:00:00Z',
        };
        invoices.push(row);
        return route.fulfill({ json: row });
      }
      return route.fulfill({ json: invoices });
    }
    if (url.pathname.endsWith('/metadata')) {
      Object.assign(invoices[0], route.request().postDataJSON() as object);
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/it/admin/cad-invoices');
  await expect(page.getByRole('heading', { name: 'Fatture e prestazioni' })).toBeVisible();
  await page.getByLabel('Cliente / azienda (riferimento interno)', { exact: true }).fill('Example company');
  await page.getByLabel('Nome fattura', { exact: true }).fill('Prima iterazione');
  await page.getByLabel('Progetto (opzionale)', { exact: true }).fill('Prototype A');
  await page.getByLabel('Descrizione e dettagli', { exact: true }).fill('Disegno personalizzato\nRevisione incastro');
  await page.getByLabel('Ore', { exact: true }).fill('1,25');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('80');
  await page.getByRole('button', { name: 'Aggiungi prestazione', exact: true }).click();
  await page.getByLabel('Descrizione e dettagli', { exact: true }).nth(1).fill('Assemblaggio e finitura');
  await page.getByLabel('Modalità di addebito', { exact: true }).nth(1).selectOption({ label: 'A corpo / una tantum' });
  await page.getByLabel('Importo CHF', { exact: true }).fill('50');
  await expect(page.getByLabel('Ore', { exact: true })).toHaveCount(1);
  await page.screenshot({ path: testInfo.outputPath('invoice-editor.png'), fullPage: true });
  await page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Example company / Prototype A (1)' })).toBeVisible();
  await expect(page.getByRole('cell', { name: /Prima iterazione/ })).toBeVisible();
  await page.getByRole('button', { name: 'Nuova fattura intermedia', exact: true }).click();
  await expect(page.getByLabel('Cliente / azienda (riferimento interno)', { exact: true })).toHaveValue('Example company');
  await expect(page.getByLabel('Progetto (opzionale)', { exact: true })).toHaveValue('Prototype A');
  await expect(page.getByLabel('Nome fattura', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('Descrizione e dettagli', { exact: true })).toHaveCount(1);
  await expect(page.getByLabel('Descrizione e dettagli', { exact: true })).toHaveValue('');
  await page.getByRole('button', { name: 'Modifica nome e gruppo', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Nome fattura', { exact: true }).fill('Prima iterazione approvata');
  await page.getByRole('dialog').getByRole('button', { name: 'Salva', exact: true }).click();
  await expect(page.getByRole('cell', { name: /Prima iterazione approvata/ })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: testInfo.outputPath('invoice-mobile.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(failures).toEqual([]);
});
