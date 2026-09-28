import { readFile } from 'node:fs/promises';
import { expect, test, type Download } from '@playwright/test';
import { loginAsAdmin } from '../admin/login';

function fixture(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Fullstack paid-order journey requires ${name} from the isolated seed`);
  return value;
}

function chfCents(text: string): number {
  const amount = text.match(/[0-9'’]+(?:[.,][0-9]{2})?/);
  if (!amount) throw new Error(`No CHF amount in: ${text}`);
  return Math.round(Number(amount[0].replace(/['’]/g, '').replace(',', '.')) * 100);
}

async function bytes(download: Download): Promise<Buffer> {
  const path = await download.path();
  if (!path) throw new Error(`Playwright did not save ${download.suggestedFilename()}`);
  return readFile(path);
}

test('ORDER-003: admin payment confirmation unlocks customer CAD files and generates documents', async ({ page: customer, browser }) => {
  test.setTimeout(120_000);
  const orderId = fixture('E2E_ORDER_CAD_PENDING_ID');
  const expectedTotalCents = Math.round(Number(fixture('E2E_ORDER_CAD_TOTAL_CHF')) * 100);
  await customer.goto(`/it/order/${encodeURIComponent(orderId)}`);
  const adminContext = await browser.newContext({ baseURL: new URL(customer.url()).origin });
  try {
    await expect(customer.locator('.payment-main')).toBeVisible();
    await expect(customer.locator('.cad-download-panel')).toBeVisible();
    await expect(customer.locator('.cad-download-panel button')).toBeDisabled();
    expect(chfCents(await customer.locator('.payment-summary app-price-breakdown .price-total').innerText())).toBe(expectedTotalCents);

    const admin = await adminContext.newPage();
    await loginAsAdmin(admin);
    await admin.waitForLoadState('networkidle');
    const row = admin
      .locator('.orders-table tbody tr:not(.no-results)')
      .filter({ hasText: orderId.slice(0, 8) });
    await expect(row).toHaveCount(1);
    await Promise.all([
      admin.waitForResponse((response) =>
        response.url().endsWith(`/api/admin/orders/${orderId}`) &&
        response.request().method() === 'GET',
      ),
      row.click(),
    ]);
    await expect(admin.locator('.detail-panel .order-uuid code')).toHaveText(orderId);
    const statusEditor = admin.locator('.status-editor--primary');
    await statusEditor.locator('select').selectOption({ index: 1 });
    const updateButton = statusEditor.getByRole('button', { name: 'Aggiorna stato' });
    await expect(updateButton).toBeEnabled();
    const updateResponse = admin.waitForResponse((response) =>
      response.url().includes(`/api/admin/orders/${orderId}/status`) &&
      response.request().method() === 'POST',
    );
    await updateButton.click();
    expect((await updateResponse).ok()).toBe(true);
    await expect(admin.locator('.order-priority-panel__status .order-status-badge')).toContainText('Pagato');

    const paymentDocuments = admin.locator('details.order-disclosure').filter({ hasText: 'Pagamento e documenti' });
    await paymentDocuments.locator('summary').click();
    for (const [label, prefix] of [
      ['Scarica conferma + QR bill', 'conferma-'],
      ['Scarica fattura', 'fattura-'],
    ] as const) {
      const [download] = await Promise.all([
        admin.waitForEvent('download'),
        paymentDocuments.getByRole('button', { name: label }).click(),
      ]);
      expect(download.suggestedFilename()).toMatch(new RegExp(`^${prefix}.+\\.pdf$`));
      const pdf = await bytes(download);
      expect(pdf.length).toBeGreaterThan(1000);
      expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
    }

    await customer.reload();
    await expect(customer.getByRole('status').filter({ hasText: 'Pagato' }).first()).toBeVisible();
    await expect(customer.locator('.payment-main')).toHaveCount(0);
    await expect(customer.locator('.cad-download-panel button')).toBeEnabled();
    const [cadDownload] = await Promise.all([
      customer.waitForEvent('download'),
      customer.locator('.cad-download-panel button').click(),
    ]);
    expect(cadDownload.suggestedFilename()).toMatch(/\.zip$/);
    const zip = await bytes(cadDownload);
    expect(zip.length).toBeGreaterThan(100);
    expect(zip.subarray(0, 2).toString()).toBe('PK');
  } finally {
    await adminContext.close();
  }
});
