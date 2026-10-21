import { expect, test } from '@playwright/test';
import { loginAsAdmin } from './login';

test('ADMIN-CAD-002: a saved calculator link follows the redirect after its CAD invoice is deleted', async ({ page, browser }) => {
  test.setTimeout(60_000);
  await loginAsAdmin(page);
  const origin = new URL(page.url()).origin;
  const session = await page.request.post('/api/quote-sessions', { data: {} });
  expect(session.ok()).toBe(true);
  const { id } = await session.json() as { id: string };
  await page.goto('/it/admin/cad-invoices');
  const name = `Saved calculator link ${crypto.randomUUID()}`;
  await page.getByLabel('ID sessione calcolatore (opzionale)').fill(id);
  await page.getByLabel('Nome fattura', { exact: true }).fill(name);
  await page.getByLabel('Descrizione e dettagli', { exact: true }).fill('Synthetic service');
  await page.getByLabel('Ore', { exact: true }).fill('1');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('30');
  const created = page.waitForResponse(response =>
    response.url().endsWith('/api/admin/cad-invoices') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click();
  expect((await created).ok()).toBe(true);
  await page.locator('table.cad-invoices-table tbody tr').filter({ hasText: name })
    .getByRole('button', { name: 'Elimina', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Gestione del vecchio link').selectOption({ label: 'Reindirizza a una pagina del sito' });
  await dialog.getByLabel('Pagina di destinazione (percorso interno)').fill('/contact');
  const removed = page.waitForResponse(response => response.url().endsWith(`/api/admin/cad-invoices/${id}/delete`));
  await dialog.getByRole('button', { name: 'Conferma eliminazione', exact: true }).click();
  expect((await removed).status()).toBe(204);
  const customerContext = await browser.newContext({ baseURL: origin });
  try {
    const customer = await customerContext.newPage();
    await customer.goto(`/it/checkout/cad?session=${id}`);
    await expect(customer).toHaveURL(/\/it\/contact$/);
    // Calculator links can have been shared before the same session became an invoice.
    await customer.goto(`/it/calculator/basic?session=${id}`);
    await expect(customer).toHaveURL(/\/it\/contact$/);
  } finally {
    await customerContext.close();
  }
});

test('ADMIN-CAD-001: real invoice edits, protected deletion, replacement and public redirect preserve the link', async ({ page, browser }) => {
  test.setTimeout(120_000);
  await loginAsAdmin(page);
  await page.goto('/it/admin/cad-invoices');
  const name = `E2E lifecycle ${crypto.randomUUID()}`;
  await page.getByLabel('Nome fattura', { exact: true }).fill(name);
  await page.getByLabel('Descrizione e dettagli', { exact: true }).fill('Synthetic design service');
  await page.getByLabel('Ore', { exact: true }).fill('1');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('30');
  const created = page.waitForResponse(response =>
    response.url().endsWith('/api/admin/cad-invoices') && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click();
  const createdResponse = await created;
  expect(createdResponse.ok()).toBe(true);
  const sessionField = page.getByLabel('ID sessione calcolatore (opzionale)');
  await expect(sessionField).toHaveValue(/^[0-9a-f-]{36}$/);
  const sessionId = await sessionField.inputValue();
  await expect(sessionField).toHaveValue(sessionId);
  await expect(sessionField).toHaveAttribute('readonly', '');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('40');
  const updated = page.waitForResponse(response =>
    response.url().endsWith(`/api/admin/cad-invoices/${sessionId}`) && response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click();
  expect((await updated).ok()).toBe(true);
  await expect(page.getByLabel('Descrizione e dettagli', { exact: true })).toHaveValue('Synthetic design service');

  const customerContext = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const customer = await customerContext.newPage();
    const deletePath = `/api/admin/cad-invoices/${sessionId}/delete`;
    const anonymous = await customer.request.post(deletePath, {
      headers: { Origin: new URL(page.url()).origin }, data: { linkAction: 'NEW_INVOICE' },
    });
    expect(anonymous.status()).toBe(401);
    const crossOrigin = await page.request.post(deletePath, {
      headers: { Origin: 'https://example.invalid' }, data: { linkAction: 'NEW_INVOICE' },
    });
    expect(crossOrigin.status()).toBe(403);
    await customer.goto(`/it/checkout/cad?session=${sessionId}`);
    await expect(customer.getByText(name, { exact: true })).toBeVisible();
    await expect(customer.locator('app-price-breakdown')).toContainText('40.00');

    const row = page.locator('table.cad-invoices-table tbody tr').filter({ hasText: name });
    await row.getByRole('button', { name: 'Elimina', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Annulla', exact: true }).click();
    expect((await customer.request.get(`/api/quote-sessions/${sessionId}`)).status()).toBe(200);
    await row.getByRole('button', { name: 'Elimina', exact: true }).click();
    const deleted = page.waitForResponse(response => response.url().endsWith(deletePath));
    await page.getByRole('dialog').getByRole('button', { name: 'Conferma eliminazione', exact: true }).click();
    expect((await deleted).status()).toBe(204);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect((await customer.request.get(`/api/quote-sessions/${sessionId}`)).status()).toBe(410);
    await expect(sessionField).toHaveValue(sessionId);
    await expect(page.getByLabel('Descrizione e dettagli', { exact: true })).toHaveValue('');

    await page.getByLabel('Nome fattura', { exact: true }).fill(name);
    await page.getByLabel('Descrizione e dettagli', { exact: true }).fill('Replacement service');
    await page.getByLabel('Ore', { exact: true }).fill('1');
    await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('25');
    const replacement = page.waitForResponse(response =>
      response.url().endsWith(`/api/admin/cad-invoices/${sessionId}`) && response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click();
    expect((await replacement).ok()).toBe(true);
    await customer.reload();
    await expect(customer.getByText('Replacement service', { exact: true })).toBeVisible();
    await expect(customer.locator('app-price-breakdown')).toContainText('25.00');
    await expect(customer.getByText('Synthetic design service', { exact: true })).toHaveCount(0);

    await row.getByRole('button', { name: 'Elimina', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Gestione del vecchio link').selectOption({ label: 'Reindirizza a una pagina del sito' });
    await dialog.getByLabel('Pagina di destinazione (percorso interno)').fill('/contact');
    const redirected = page.waitForResponse(response => response.url().endsWith(deletePath));
    await dialog.getByRole('button', { name: 'Conferma eliminazione', exact: true }).click();
    expect((await redirected).status()).toBe(204);
    await customer.goto(`/fr/checkout/cad?session=${sessionId}`);
    await expect(customer).toHaveURL(/\/fr\/contact$/);
    const publicData = await customer.request.get(`/api/quote-sessions/${sessionId}`);
    expect(await publicData.json()).toEqual({ redirectPath: '/contact' });
  } finally {
    await customerContext.close();
  }
});
