import {expect, test} from '@playwright/test';

// Synthetic geometry only; these scenarios never contact a real admin API.
const stl = `solid fixture
facet normal 0 0 1
outer loop
vertex 0 0 0
vertex 20 0 0
vertex 0 20 20
endloop
endfacet
endsolid fixture`;

for (const area of ['orders', 'contact-requests', 'sessions']) {
  test(`UI-ADMIN-PREVIEW-001: simulated ${area} model preview renders and reports unavailable files`, async ({
                                                                                                               page,
                                                                                                             }, testInfo) => {
    let fail = false;
    const now = '2026-10-22T12:00:00Z';
    const item = {
      id: 'model',
      itemType: 'PRINT_FILE',
      originalFilename: 'fixture.stl',
      quantity: 1,
      materialCode: 'PLA',
      colorCode: 'Yellow',
      printTimeSeconds: 3600,
      materialGrams: 20,
      unitPriceChf: 10,
      lineTotalChf: 10,
      status: 'READY',
    };
    const order = {
      id: 'order',
      orderNumber: 'TEST',
      status: 'PAID',
      paymentStatus: 'RECEIVED',
      customerEmail: 'synthetic@example.com',
      totalChf: 10,
      createdAt: now,
      items: [item],
      emailLogs: [],
    };
    const request = {
      id: 'request',
      requestType: 'CUSTOM',
      customerType: 'PRIVATE',
      status: 'NEW',
      email: 'synthetic@example.com',
      createdAt: now,
      updatedAt: now,
      message: 'Synthetic request',
      attachments: [
        {id: 'model', originalFilename: 'fixture.stl', createdAt: now},
      ],
      emailLogs: [],
    };
    const session = {
      id: 'session',
      status: 'ACTIVE',
      materialCode: 'PLA',
      createdAt: now,
      expiresAt: now,
    };
    await page.route('**/api/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/auth/login') || path.endsWith('/auth/me'))
        return route.fulfill({json: {authenticated: true}});
      if (path.endsWith('/information'))
        return route.fulfill({json: {id: 'information', entries: []}});
      if (path.endsWith('/file') || path.endsWith('/stl-preview'))
        return route.fulfill(
          fail
            ? {status: 404, json: {message: 'Synthetic missing file'}}
            : {contentType: 'model/stl', body: stl},
        );
      if (path === '/api/admin/orders') return route.fulfill({json: [order]});
      if (path === '/api/admin/orders/order')
        return route.fulfill({json: order});
      if (path === '/api/admin/contact-requests')
        return route.fulfill({json: [request]});
      if (path === '/api/admin/contact-requests/request')
        return route.fulfill({json: request});
      if (path === '/api/admin/sessions')
        return route.fulfill({json: [session]});
      if (path === '/api/quote-sessions/session')
        return route.fulfill({
          json: {
            session,
            items: [item],
            itemsTotalChf: 10,
            shippingCostChf: 4,
            grandTotalChf: 14,
          },
        });
      if (path.endsWith('/statistics')) return route.fulfill({json: null});
      return route.fulfill({json: []});
    });
    await page.goto(
      `/it/admin/login?redirect=${encodeURIComponent(`/it/admin/${area}`)}`,
    );
    await expect(page.locator('.site-intro')).toHaveCount(0);
    await page.locator('input#admin-password').fill('synthetic-password');
    await page.getByRole('button', {name: 'Accedi', exact: true}).click();
    if (area === 'sessions')
      await page.getByRole('button', {name: 'Vedi', exact: true}).click();
    const trigger = page.getByRole('button', {
      name: 'Anteprima 3D',
      exact: true,
    });
    await trigger.click();
    const modal = page.getByRole('dialog', {
      name: 'Anteprima 3D del modello',
      exact: true,
    });
    await expect(modal.locator('canvas')).toBeVisible();
    await expect(modal.locator('.loading-overlay')).toHaveCount(0);
    await expect(modal.locator('.dims-overlay')).toContainText('20');
    await page.setViewportSize({width: 390, height: 844});
    await page.screenshot({
      path: testInfo.outputPath(`${area}-preview-mobile.png`),
    });
    await page.keyboard.press('Escape');
    await expect(modal).toHaveCount(0);
    if (area === 'orders') await page.setViewportSize({width: 1280, height: 844});
    fail = true;
    await trigger.click();
    await expect(modal.locator('.ui-banner--error')).toBeVisible();
    await expect(modal.locator('.preview-loading')).toHaveCount(0);
  });
}
