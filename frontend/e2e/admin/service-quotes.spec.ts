import { readFile } from 'node:fs/promises';
import { expect, test, type Page } from '@playwright/test';
import { loginAsAdmin } from './login';
import type { AdminServiceQuote } from '../../src/app/features/service-quotes/service-quotes.models';

// Private no-store bodies can disappear from Chromium's debugging protocol.
// Forward the real request exactly once and retain its bytes before delivering them.
async function captureJson<T>(page: Page, path: RegExp, action: () => Promise<unknown>): Promise<T> {
  let value: T | undefined;
  await page.route(path, async route => {
    const response = await route.fetch(); const body = await response.body();
    if (response.ok()) value = JSON.parse(body.toString()) as T;
    await route.fulfill({ response, body });
  });
  try {
    const observed = page.waitForResponse(response => path.test(new URL(response.url()).pathname) && response.request().method() === 'POST');
    await action(); expect((await observed).ok()).toBe(true);
    if (!value) throw new Error('Successful response body was not retained');
    return value;
  } finally { await page.unroute(path); }
}

test.use({ actionTimeout: 15_000 });

for (const mode of ['hourly-percent', 'lump-chf'] as const) {
  test(`QUOTE-001: ${mode} quote is accepted, emailed as PDF and linked to the final order`, async ({ page, browser, request }, testInfo) => {
    test.setTimeout(180_000);
    await loginAsAdmin(page);
    const origin = new URL(page.url()).origin;
    const email = `quote-${crypto.randomUUID()}@example.test`;
    const title = `E2E ${mode} ${crypto.randomUUID()}`;
    await page.goto('/it/admin/service-quotes');
    await page.getByLabel('Titolo del preventivo *', { exact: true }).fill(title);
    await page.getByLabel('Descrizione unica del lavoro *', { exact: true }).fill('Synthetic CAD design for one prototype.');
    await page.getByLabel('Cliente / azienda (riferimento interno)', { exact: true }).fill('E2E commercial customer');
    await expect(page.locator('[formcontrolname=recipientEmail]')).toHaveCount(0);
    if (mode === 'hourly-percent') {
      await page.getByLabel('Ore stimate', { exact: true }).fill('10');
      await page.getByLabel('Tariffa oraria CHF', { exact: true }).fill('80');
      await page.getByLabel('Variazione massima', { exact: true }).fill('15');
    } else {
      await page.getByLabel('Tipo di stima', { exact: true }).selectOption({ label: 'Stima a corpo' });
      await page.getByLabel('Importo stimato', { exact: true }).fill('500');
      await page.getByLabel('Margine espresso in', { exact: true }).selectOption({ label: 'Importo CHF' });
      await page.getByLabel('Variazione massima', { exact: true }).fill('100');
    }
    const saved = page.waitForResponse(r => new URL(r.url()).pathname === '/api/admin/service-quotes' && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Salva bozza', exact: true }).click();
    expect((await saved).status()).toBe(200);
    const quote = await captureJson<AdminServiceQuote>(page, /\/api\/admin\/service-quotes\/[0-9a-f-]+\/publish$/, () => page.getByRole('button', { name: 'Pubblica preventivo', exact: true }).click());
    const revision = quote.revisions[0];
    expect(revision.offer.maximumAmountChf).toBe(mode === 'hourly-percent' ? 920 : 600);
    await page.getByText('Invia il link via email (facoltativo)', { exact: true }).click();
    await page.locator('.invitation input').fill(`invitation-${crypto.randomUUID()}@example.test`);
    const invited = page.waitForResponse(r => r.url().endsWith(`/revisions/${revision.offer.revisionId}/email`));
    await page.getByRole('button', { name: 'Invia preventivo', exact: true }).click();
    expect((await invited).ok()).toBe(true);
    const customerContext = await browser.newContext({ baseURL: origin, viewport: { width: 390, height: 844 } });
    try {
      const customer = await customerContext.newPage();
      await customer.goto(new URL(revision.publicUrl).pathname);
      await expect(customer.getByRole('heading', { name: title, exact: true })).toBeVisible();
      await expect(customer.getByRole('button', { name: 'Scarica PDF', exact: true })).toHaveCount(0);
      const form = customer.locator('app-service-quote-acceptance form');
      const field = (name: string) => form.locator(`app-input[formcontrolname="${name}"] input`);
      await field('email').fill(email);
      if (mode === 'lump-chf') {
        await form.locator('app-select[formcontrolname="customerType"] select').selectOption({ label: 'Azienda' });
        await field('companyName').fill('Example AG'); await field('contactPerson').fill('Ada Example');
      } else {
        await field('firstName').fill('Ada'); await field('lastName').fill('Example');
      }
      await field('phone').fill('+41 79 000 00 00');
      await field('addressLine1').fill('Teststrasse 1'); await field('zip').fill('8000'); await field('city').fill('Zürich');
      await form.locator('app-checkbox[formcontrolname="acceptQuote"] input').check();
      await form.locator('app-legal-consent input').check();
      await expect.poll(() => customer.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await customer.screenshot({ path: testInfo.outputPath(`${mode}-acceptance-mobile.png`), fullPage: true });
      const confirmation = await captureJson<{ acceptanceId: string }>(customer, /\/api\/service-quotes\/[0-9a-f-]+\/revisions\/[0-9a-f-]+\/accept$/, () => form.getByRole('button', { name: 'Accetta preventivo', exact: true }).click());
      await expect(customer.getByRole('heading', { name: 'Preventivo accettato', exact: true })).toBeVisible();
      const downloadButton = customer.getByRole('button', { name: 'Scarica PDF', exact: true });
      await expect(downloadButton).toBeVisible({ timeout: 30_000 });
      const pdfDownload = customer.waitForEvent('download'); await downloadButton.click();
      const downloaded = await pdfDownload; const path = await downloaded.path(); expect(path).toBeTruthy();
      const pdf = await readFile(path!); expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
      const mailbox = process.env['E2E_MAIL_URL'] ?? `http://127.0.0.1:${process.env['E2E_MAIL_PORT']}`;
      interface Message { ID: string; To: { Address: string }[]; Attachments: number }
      let receiptId = '';
      await expect.poll(async () => {
        const response = await request.get(`${mailbox}/api/v1/messages`);
        const messages = await response.json() as { messages: Message[] };
        receiptId = messages.messages.find(message => message.To.some(to => to.Address === email) && message.Attachments > 0)?.ID ?? '';
        return Boolean(receiptId);
      }, { timeout: 30_000 }).toBe(true);
      const receipt = await request.get(`${mailbox}/api/v1/message/${receiptId}`);
      const detail = await receipt.json() as { Attachments: { FileName: string; PartID: string }[] };
      expect(detail.Attachments).toHaveLength(1);
      expect(detail.Attachments[0].FileName).toContain(quote.id);
      const attachment = await request.get(`${mailbox}/api/v1/message/${receiptId}/part/${detail.Attachments[0].PartID}`);
      expect(await attachment.body()).toEqual(pdf);
      expect((await customer.request.get(`/api/service-quote-acceptances/${confirmation.acceptanceId}/pdf`)).status()).toBe(403);
      await customer.reload(); await expect(customer.getByText('Questo preventivo è già stato accettato.', { exact: false })).toBeVisible();
      await expect(customer.getByRole('button', { name: 'Scarica PDF', exact: true })).toHaveCount(0);
      await page.getByRole('button', { name: 'Aggiorna', exact: true }).click();
      await expect(page.locator('.revision').getByText(email, { exact: false })).toBeVisible();
      const adminDownload = page.waitForEvent('download'); await page.getByRole('button', { name: 'Scarica PDF', exact: true }).click();
      const adminPath = await (await adminDownload).path(); expect(await readFile(adminPath!)).toEqual(pdf);
      const overflow = await page.locator('app-admin-service-quotes').evaluate(element =>
        Array.from(element.querySelectorAll<HTMLElement>('.section-card, .quote-admin-layout, .quote-editor, .quote-list, .revision'))
          .filter(panel => panel.scrollWidth > panel.clientWidth + 2).map(panel => ({ className: panel.className, width: panel.clientWidth, content: panel.scrollWidth })),
      );
      expect(overflow).toEqual([]);
      await page.screenshot({ path: testInfo.outputPath(`${mode}-back-office.png`), fullPage: true });
      await page.getByRole('button', { name: 'Crea lavoro da preventivo', exact: true }).click();
      await expect(page.getByLabel('UUID preventivo', { exact: true })).toHaveValue(quote.id);
      await expect(page.getByLabel('Nome fattura', { exact: true })).toHaveValue(title);
      const job = await captureJson<{ sessionId: string; checkoutPath: string; serviceQuoteReference: { quoteId: string; revisionId: string } }>(page, /\/api\/admin\/cad-invoices$/, () => page.getByRole('button', { name: 'Salva e prepara checkout', exact: true }).click());
      expect(job.serviceQuoteReference).toMatchObject({ quoteId: quote.id, revisionId: revision.offer.revisionId });
      expect(job.checkoutPath).toMatch(/#prefill=[a-f0-9]{64}$/);
      const plainSession = await customer.request.get(`/api/quote-sessions/${job.sessionId}`);
      const publicSession = await plainSession.text();
      expect(publicSession).not.toContain(email);
      expect(publicSession).not.toContain('cadCheckoutCustomerToken');
      expect((await customer.request.get(`/api/quote-sessions/${job.sessionId}/checkout-customer`)).status()).toBe(403);
      await customer.goto('/it' + job.checkoutPath);
      const checkout = customer.locator('.checkout-form-section form'); await expect(checkout).toBeVisible();
      await expect(checkout.locator('app-input[formcontrolname="email"] input')).toHaveValue(email);
      await expect(checkout.locator('app-input[formcontrolname="phone"] input')).toHaveValue('+41 79 000 00 00');
      const billing = checkout.locator('[formgroupname="billingAddress"]');
      const identity = mode === 'lump-chf' ? { companyName: 'Example AG', referencePerson: 'Ada Example' } : { firstName: 'Ada', lastName: 'Example' };
      for (const [name, value] of Object.entries({ ...identity, addressLine1: 'Teststrasse 1', zip: '8000', city: 'Zürich', countryCode: 'CH' })) {
        await expect(billing.locator(`app-input[formcontrolname="${name}"] input`)).toHaveValue(value!);
      }
      await expect(checkout.locator('app-legal-consent input')).not.toBeChecked();
      await customer.screenshot({ path: testInfo.outputPath(`${mode}-checkout-prefilled.png`), fullPage: true });
      // Order data remains editable without rewriting the accepted quote.
      await billing.locator('app-input[formcontrolname="addressLine1"] input').fill('Updated billing address 2');
      await checkout.locator('app-legal-consent input').check();
      const order = await captureJson<{ id: string; serviceQuoteReference: { quoteId: string } }>(customer, /\/api\/orders\/from-quote\/[0-9a-f-]+$/, () => checkout.locator('app-button[type="submit"] button').click());
      expect(order.serviceQuoteReference.quoteId).toBe(quote.id);
      const prefillToken = new URLSearchParams(job.checkoutPath.split('#')[1]).get('prefill')!;
      expect((await customer.request.get(`/api/quote-sessions/${job.sessionId}/checkout-customer`, { headers: { 'X-Cad-Checkout-Token': prefillToken } })).status()).toBe(403);
      await page.goto(`/it/admin/service-quotes?quote=${quote.id}`);
      await expect(page.locator('.revision').getByText('Teststrasse 1, 8000 Zürich, CH', { exact: true })).toBeVisible();
      await expect(page.locator('.revision').getByText('Updated billing address 2', { exact: false })).toHaveCount(0);
      await expect(customer).toHaveURL(/\/it\/order\//);
      await expect(customer.getByText(quote.id, { exact: false })).toBeVisible();
      const invoiceDocument = await customer.request.get(`/api/orders/${order.id}/confirmation`);
      expect(invoiceDocument.ok()).toBe(true); expect((await invoiceDocument.body()).subarray(0, 5).toString()).toBe('%PDF-');
    } finally { await customerContext.close(); }
  });
}

test('QUOTE-002: anonymous access and cross-origin admin mutations are rejected', async ({ page, browser }) => {
  const anonymous = await browser.newContext({ baseURL: process.env['E2E_BASE_URL'] });
  try {
    expect((await anonymous.request.get('/api/admin/service-quotes')).status()).toBe(401);
    expect((await anonymous.request.post('/api/admin/service-quotes', { headers: { Origin: new URL(process.env['E2E_BASE_URL']!).origin }, data: {} })).status()).toBe(401);
    await loginAsAdmin(page);
    expect((await page.request.post('/api/admin/service-quotes', { headers: { Origin: 'https://example.invalid' }, data: {} })).status()).toBe(403);
  } finally { await anonymous.close(); }
});
