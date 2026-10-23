import { expect, test } from '@playwright/test';
import type {Page} from '@playwright/test';
import {
  AdminCadInvoice,
  AdminCreateCadInvoicePayload,
} from '../../src/app/features/admin/services/admin-operations.service';

async function openSimulatedAdmin(page: Page, path: string): Promise<void> {
  await page.goto(`/it/admin/login?redirect=${encodeURIComponent(path)}`);
  await expect(page.locator('.site-intro')).toHaveCount(0);
  await page.locator('input#admin-password').fill('synthetic-password');
  await page.getByRole('button', {name: 'Accedi', exact: true}).click();
}

test('UI-INVOICE-006: simulated checkout summary and images expand on desktop and mobile', async ({
                                                                                                    page,
                                                                                                  }, testInfo) => {
  const description =
    'Due riunioni con il cliente per definire i requisiti (1 h). Discussione interna per valutare le soluzioni di design e la loro realizzazione (0,5 h). Entrambe con 2 persone.';
  const imageName = 'prototipo.png';
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=',
    'base64',
  );
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/quote-sessions/summary-preview') {
      return route.fulfill({
        json: {
          session: {
            id: 'summary-preview',
            status: 'CAD_ACTIVE',
            invoiceName: 'Prototipo V1',
            serviceLines: [
              {
                description,
                billingType: 'HOURLY',
                quantity: 3,
                unitPriceChf: 35,
                lineTotalChf: 105,
              },
            ],
          },
          items: [],
          itemsTotalChf: 105,
          grandTotalChf: 105,
          setupCostChf: 0,
          shippingCostChf: 0,
          attachments: [
            {id: 'photo', originalFilename: imageName, image: true},
          ],
        },
      });
    }
    if (path.endsWith('/attachments/photo/preview')) {
      return route.fulfill({contentType: 'image/png', body: png});
    }
    if (path.endsWith('/resume')) return route.fulfill({json: null});
    return route.fulfill({json: []});
  });
  await page.goto('/it/checkout/cad?session=summary-preview');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  await expect(page.locator('.summary-services')).toContainText(description);
  const info = page.locator('.checkout-order-information');
  const formBounds = await page.locator('.checkout-form-section').boundingBox();
  const infoBounds = await info.boundingBox();
  expect(infoBounds?.width).toBe(formBounds?.width);
  expect(infoBounds?.x).toBe(formBounds?.x);
  const header = info.locator('.information-header');
  const headingBounds = await header.getByRole('heading').boundingBox();
  const actionBounds = await header.getByRole('button').boundingBox();
  expect(actionBounds!.x).toBeGreaterThan(
    headingBounds!.x + headingBounds!.width,
  );
  await page.screenshot({
    path: testInfo.outputPath('checkout-summary-desktop.png'),
    fullPage: true,
  });

  for (const mobile of [false, true]) {
    if (mobile) await page.setViewportSize({width: 390, height: 844});
    await page
      .getByRole('button', {name: 'Ingrandisci riepilogo', exact: true})
      .filter({visible: true})
      .first()
      .click();
    const summary = page.getByRole('dialog', {
      name: 'Riepilogo Ordine',
      exact: true,
    });
    await expect(summary).toBeVisible();
    await expect(summary).toContainText(description);
    await expect(summary.locator('app-price-breakdown')).toContainText(
      '105.00',
    );
    expect(
      await summary
        .locator('.item-name')
        .first()
        .evaluate((element) => getComputedStyle(element).wordBreak),
    ).toBe('normal');
    expect(
      await summary.evaluate(
        (element) => element.scrollWidth <= element.clientWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath(
        `checkout-summary-${mobile ? 'mobile' : 'expanded'}.png`,
      ),
      fullPage: false,
    });
    await summary
      .getByRole('button', {name: `Ingrandisci immagine: ${imageName}`})
      .click();
    const image = page.getByRole('dialog', {name: imageName, exact: true});
    await expect(image).toBeVisible();
    await expect(image.getByRole('img')).toBeVisible();
    await image
      .getByRole('button', {name: 'Dimensioni originali', exact: true})
      .click();
    await expect(
      image.getByRole('button', {name: 'Adatta allo schermo', exact: true}),
    ).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(image).toHaveCount(0);
    await expect(summary).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(summary).toHaveCount(0);
    await expect(page.locator('.summary-services')).toContainText(description);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
  await page
    .getByRole('button', {name: 'Ingrandisci immagine: prototipo.png'})
    .click();
  await expect(
    page.getByRole('dialog', {name: imageName, exact: true}),
  ).toBeVisible();
});

test('UI-INVOICE-004: simulated editing and replacement preserve the shared link', async ({
                                                                                            page,
                                                                                          }, testInfo) => {
  const id = '66666666-6666-6666-6666-666666666666';
  let invoice: AdminCadInvoice | null = {
    sessionId: id,
    sessionStatus: 'CAD_ACTIVE',
    invoiceName: 'Link stabile',
    cadHours: 0,
    cadHourlyRateChf: 0,
    cadTotalChf: 0,
    printItemsTotalChf: 0,
    setupCostChf: 0,
    shippingCostChf: 0,
    grandTotalChf: 20,
    checkoutPath: `/checkout/cad?session=${id}`,
    createdAt: '2026-10-20T12:00:00Z',
    serviceLines: [
      {
        description: 'Design',
        billingType: 'FIXED',
        quantity: 1,
        unitPriceChf: 20,
      },
    ],
  };
  let deleteCalls = 0;
  let updates = 0;
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (path.endsWith('/auth/me') || path.endsWith('/auth/login'))
      return route.fulfill({json: {authenticated: true}});
    if (path.endsWith('/cad-invoices')) {
      expect(method).toBe('GET');
      return route.fulfill({ json: invoice ? [invoice] : [] });
    }
    if (path.endsWith(`/cad-invoices/${id}`) && method === 'PUT') {
      updates++;
      const body = route
        .request()
        .postDataJSON() as AdminCreateCadInvoicePayload;
      expect(body.sessionId).toBe(id);
      invoice = {
        ...body,
        sessionId: id,
        sessionStatus: 'CAD_ACTIVE',
        cadHours: 0,
        cadHourlyRateChf: 0,
        cadTotalChf: 0,
        printItemsTotalChf: 0,
        setupCostChf: 0,
        shippingCostChf: 0,
        grandTotalChf: 30,
        checkoutPath: `/checkout/cad?session=${id}`,
        createdAt: '2026-10-20T12:00:00Z',
      };
      return route.fulfill({ json: invoice });
    }
    if (path.endsWith('/delete')) {
      deleteCalls++;
      expect(route.request().postDataJSON()).toEqual(
        deleteCalls === 1
          ? {linkAction: 'NEW_INVOICE'}
          : {linkAction: 'REDIRECT', targetPath: '/contact'},
      );
      invoice = null;
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/items'))
      return route.fulfill({
        json: {
          sessionId: id,
          sessionStatus: 'CAD_ACTIVE',
          items: [],
          grandTotalChf: 30,
        },
      });
    return route.fulfill({ json: [] });
  });
  await openSimulatedAdmin(page, '/it/admin/cad-invoices');
  await page
    .getByRole('button', {name: 'Modifica prestazioni', exact: true})
    .click();
  const sessionField = page.locator('app-input[name="sessionId"] input');
  await expect(sessionField).toHaveAttribute('readonly', '');
  await page.getByLabel('Importo CHF', { exact: true }).fill('30');
  await page
    .getByRole('button', {name: 'Salva e prepara checkout', exact: true})
    .click();
  await expect(sessionField).toHaveValue(id);
  expect(updates).toBe(1);
  await expect(
    page.getByLabel('Descrizione e dettagli', {exact: true}),
  ).toHaveValue('Design');
  await page.getByRole('button', { name: 'Elimina', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(deleteCalls).toBe(0);
  await page
    .getByRole('dialog')
    .getByRole('button', {name: 'Annulla', exact: true})
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(deleteCalls).toBe(0);
  await page.getByRole('button', { name: 'Elimina', exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath('cad-delete-mobile.png'),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page
    .getByRole('dialog')
    .getByRole('button', {name: 'Conferma eliminazione', exact: true})
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(deleteCalls).toBe(1);
  await expect(sessionField).toHaveValue(id);
  await expect(
    page.getByLabel('Descrizione e dettagli', {exact: true}),
  ).toHaveValue('');
  await page
    .getByLabel('Descrizione e dettagli', {exact: true})
    .fill('Nuova prestazione');
  await page.getByLabel('Ore', { exact: true }).fill('1');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('30');
  await page
    .getByRole('button', {name: 'Salva e prepara checkout', exact: true})
    .click();
  await expect(page.locator('table.cad-invoices-table tbody tr')).toHaveCount(
    1,
  );
  await expect(
    page.getByLabel('Descrizione e dettagli', {exact: true}),
  ).toHaveValue('Nuova prestazione');
  expect(updates).toBe(2);
  await expect(sessionField).toHaveValue(id);
  await page.getByRole('button', { name: 'Elimina', exact: true }).click();
  await page
    .getByRole('dialog')
    .getByLabel('Gestione del vecchio link')
    .selectOption({ label: 'Reindirizza a una pagina del sito' });
  await page
    .getByRole('dialog')
    .getByLabel('Pagina di destinazione (percorso interno)')
    .fill('/contact');
  await page.screenshot({
    path: testInfo.outputPath('cad-delete-redirect-mobile.png'),
    fullPage: true,
  });
  await page
    .getByRole('dialog')
    .getByRole('button', {name: 'Conferma eliminazione', exact: true})
    .click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(sessionField).toHaveValue('');
  expect(deleteCalls).toBe(2);
});

test('UI-INVOICE-005: simulated deleted link redirects to a public page', async ({
                                                                                   page,
                                                                                 }) => {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/resume')) return route.fulfill({ json: null });
    if (path === '/api/quote-sessions/deleted-cad')
      return route.fulfill({json: {redirectPath: '/contact'}});
    return route.fulfill({ json: [] });
  });
  await page.goto('/it/checkout/cad?session=deleted-cad');
  await expect(page).toHaveURL(/\/it\/contact$/);
});

test('UI-INVOICE-001: simulated API supports custom rows, grouping and interim invoices', async ({
  page,
}, testInfo) => {
  const invoices: AdminCadInvoice[] = [];
  const failures: string[] = [];
  page.on('pageerror', (error) => failures.push(error.message));
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/auth/me') || url.pathname.endsWith('/auth/login')) {
      return route.fulfill({ json: { authenticated: true } });
    }
    if (url.pathname.endsWith('/cad-invoices')) {
      if (route.request().method() === 'POST') {
        const body = route
          .request()
          .postDataJSON() as AdminCreateCadInvoicePayload;
        expect(body.serviceLines).toHaveLength(2);
        expect(body.serviceLines?.[0].quantity).toBe(1.25);
        expect(body.serviceLines?.[1].billingType).toBe('FIXED');
        expect(body.serviceLines?.[1].quantity).toBe(1);
        expect(body.cadHours).toBeUndefined();
        const row: AdminCadInvoice = {
          ...body,
          sessionId: '11111111-1111-1111-1111-111111111111',
          sessionStatus: 'CAD_ACTIVE',
          cadHours: 0,
          cadHourlyRateChf: 0,
          cadTotalChf: 0,
          printItemsTotalChf: 0,
          setupCostChf: 0,
          shippingCostChf: 0,
          grandTotalChf: 150,
          checkoutPath: '/checkout/cad?session=test',
          createdAt: '2026-09-28T12:00:00Z',
        };
        invoices.push(row);
        return route.fulfill({ json: row });
      }
      return route.fulfill({ json: invoices });
    }
    if (url.pathname.endsWith('/items')) {
      return route.fulfill({ json: {
        sessionId: invoices[0].sessionId, sessionStatus: 'CAD_ACTIVE',
        items: [], grandTotalChf: 150,
      } });
    }
    if (url.pathname.endsWith('/cad-invoices/11111111-1111-1111-1111-111111111111')) {
      Object.assign(invoices[0], route.request().postDataJSON() as object);
      return route.fulfill({ json: invoices[0] });
    }
    return route.fulfill({ json: [] });
  });
  await openSimulatedAdmin(page, '/it/admin/cad-invoices');
  await expect(
    page.getByRole('heading', { name: 'Fatture e prestazioni' }),
  ).toBeVisible();
  await page
    .getByLabel('Cliente / azienda (riferimento interno)', { exact: true })
    .fill('Example company');
  await page
    .getByLabel('Nome fattura', { exact: true })
    .fill('Prima iterazione');
  await page
    .getByLabel('Progetto (opzionale)', { exact: true })
    .fill('Prototype A');
  await page
    .getByLabel('Descrizione e dettagli', { exact: true })
    .fill('Disegno personalizzato\nRevisione incastro');
  await page.getByLabel('Ore', { exact: true }).fill('1,25');
  await page.getByLabel('Tariffa CHF/h', { exact: true }).fill('80');
  await page
    .getByRole('button', { name: 'Aggiungi prestazione', exact: true })
    .click();
  await page
    .getByLabel('Descrizione e dettagli', { exact: true })
    .nth(1)
    .fill('Assemblaggio e finitura');
  await page
    .getByLabel('Modalità di addebito', { exact: true })
    .nth(1)
    .selectOption({ label: 'A corpo / una tantum' });
  await page.getByLabel('Importo CHF', { exact: true }).fill('50');
  await expect(page.getByLabel('Ore', { exact: true })).toHaveCount(1);
  await expect(page.locator('.site-intro')).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath('invoice-editor.png'),
    fullPage: true,
  });
  await page
    .getByRole('button', { name: 'Salva e prepara checkout', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Example company / Prototype A (1)' }),
  ).toBeVisible();
  await expect(
    page.getByRole('cell', { name: /Prima iterazione/ }),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Nuova fattura intermedia', exact: true })
    .click();
  await expect(
    page.getByLabel('Cliente / azienda (riferimento interno)', { exact: true }),
  ).toHaveValue('Example company');
  await expect(
    page.getByLabel('Progetto (opzionale)', { exact: true }),
  ).toHaveValue('Prototype A');
  await expect(page.getByLabel('Nome fattura', { exact: true })).toHaveValue(
    '',
  );
  await expect(
    page.getByLabel('Descrizione e dettagli', { exact: true }),
  ).toHaveCount(1);
  await expect(
    page.getByLabel('Descrizione e dettagli', { exact: true }),
  ).toHaveValue('');
  await page
    .getByRole('button', { name: 'Modifica prestazioni', exact: true })
    .click();
  await page
    .getByLabel('Nome fattura', { exact: true })
    .fill('Prima iterazione approvata');
  await page
    .getByRole('button', { name: 'Salva e prepara checkout', exact: true })
    .click();
  await expect(
    page.getByRole('cell', { name: /Prima iterazione approvata/ }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: testInfo.outputPath('invoice-mobile.png'),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(failures).toEqual([]);
});

test('UI-INVOICE-002: simulated service-only checkout and order show agreed details', async ({
  page,
}) => {
  const lines = [
    {
      description: 'Revisione del prototipo',
      billingType: 'HOURLY',
      quantity: 1.25,
      unitPriceChf: 85.5,
      lineTotalChf: 106.88,
    },
    {
      description: 'Finitura personalizzata',
      billingType: 'FIXED',
      quantity: 1,
      unitPriceChf: 50,
      lineTotalChf: 50,
    },
  ];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/resume')) return route.fulfill({ json: null });
    if (path === '/api/quote-sessions/test-services') {
      return route.fulfill({
        json: {
          session: {
            id: 'test-services',
            status: 'CAD_ACTIVE',
            invoiceName: 'Seconda iterazione',
            serviceLines: lines,
          },
          items: [],
          itemsTotalChf: 156.88,
          grandTotalChf: 156.88,
          cadTotalChf: 0,
          setupCostChf: 0,
          shippingCostChf: 0,
        },
      });
    }
    if (path === '/api/orders/test-services') {
      return route.fulfill({
        json: {
          id: 'test-services',
          status: 'PENDING_PAYMENT',
          paymentStatus: 'PENDING',
          invoiceName: 'Seconda iterazione',
          serviceLines: lines,
          items: [],
          subtotalChf: 156.88,
          totalChf: 156.88,
          shippingCostChf: 0,
          setupCostChf: 0,
          isCadOrder: true,
          cadFileCount: 2,
          cadFileDownloadAvailable: false,
        },
      });
    }
    if (path.endsWith('/events')) return route.fulfill({ status: 204 });
    return route.fulfill({ json: [] });
  });
  await page.goto('/it/checkout/cad?session=test-services');
  await expect(
    page.getByText('Seconda iterazione', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText('Revisione del prototipo', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText('Finitura personalizzata', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('.summary-services')).toContainText('106.88');
  await expect(page.locator('app-price-breakdown')).toContainText('156.88');
  await expect(page.locator('.summary-items .cad-summary-item')).toHaveCount(0);
  await expect(page.locator('.summary-services .cad-summary-item')).toHaveCount(
    2,
  );
  await expect(
    page.locator('.checkout-form-section > app-order-information'),
  ).toHaveCount(1);
  await page.goto('/it/order/test-services');
  await expect(
    page.getByText('Seconda iterazione', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('app-price-breakdown')).toContainText(
    'Revisione del prototipo',
  );
  await expect(page.locator('app-price-breakdown')).toContainText(
    'Finitura personalizzata',
  );
  await expect(
    page.getByRole('button', { name: 'Scarica file CAD', exact: true }),
  ).toBeDisabled();
});

test('UI-INVOICE-003: session id loads files inline and admin manages attachments', async ({
  page,
                                                                                           }, testInfo) => {
  const sessionId = '33333333-3333-3333-3333-333333333333';
  const imageId = '44444444-4444-4444-4444-444444444444';
  const pdfId = '55555555-5555-5555-5555-555555555555';
  const updateCalls: { persist: boolean }[] = [];
  let uploadCalls = 0;
  const pngBytes = Buffer.from([
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0,
  ]);
  const invoice: AdminCadInvoice = {
    sessionId,
    sessionStatus: 'CAD_ACTIVE',
    invoiceName: 'Correzione prezzo',
    clientName: 'Example company',
    collaborationName: 'Prototype A',
    cadHours: 0,
    cadHourlyRateChf: 0,
    cadTotalChf: 0,
    printItemsTotalChf: 20,
    setupCostChf: 0,
    shippingCostChf: 0,
    grandTotalChf: 20,
    checkoutPath: `/checkout/cad?session=${sessionId}`,
    createdAt: '2026-09-28T12:00:00Z',
  };
  const attachments = [
    {
      id: imageId,
      originalFilename: 'photo.png',
      mimeType: 'image/png',
      fileSizeBytes: 12,
      image: true,
      createdAt: '2026-09-28T12:00:00Z',
    },
    {
      id: pdfId,
      originalFilename: 'report.pdf',
      mimeType: 'application/pdf',
      fileSizeBytes: 42,
      image: false,
      createdAt: '2026-09-28T12:00:00Z',
    },
  ];

  const statsResponse = (persist: boolean) => ({
    sessionId,
    sessionStatus: 'CAD_ACTIVE',
    items: [
      {
        id: 'item-1',
        displayName: 'fixture.stl',
        quantity: 2,
        printTimeSeconds: persist ? 7200 : 3600,
        materialGrams: persist ? 200 : 100,
        unitPriceChf: persist ? 20 : 10,
        newUnitPriceChf: persist ? null : 20,
        status: 'READY',
        lineItemType: 'PRINT_FILE',
        editable: true,
      },
    ],
    printItemsTotalChf: 40,
    globalMachineCostChf: 0,
    cadTotalChf: 0,
    itemsTotalChf: 40,
    setupCostChf: 0,
    shippingCostChf: 0,
    grandTotalChf: 40,
  });

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();
    if (path.endsWith('/auth/me') || path.endsWith('/auth/login')) {
      return route.fulfill({ json: { authenticated: true } });
    }
    if (path.endsWith('/cad-invoices')) {
      return route.fulfill({ json: [invoice] });
    }
    if (path.endsWith('/preview') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'image/png',
        body: pngBytes,
      });
    }
    if (path.endsWith('/items/print-stats') && method === 'POST') {
      const body = route.request().postDataJSON() as {
        persist: boolean;
        items: unknown[];
      };
      updateCalls.push({ persist: body.persist });
      expect(body.items).toHaveLength(1);
      return route.fulfill({ json: statsResponse(body.persist) });
    }
    if (path.endsWith('/items') && method === 'GET') {
      return route.fulfill({ json: statsResponse(false) });
    }
    if (path.endsWith('/attachments') && method === 'POST') {
      uploadCalls += 1;
      return route.fulfill({ json: attachments });
    }
    if (path.includes('/attachments/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path.endsWith('/attachments') && method === 'GET') {
      return route.fulfill({ json: attachments });
    }
    return route.fulfill({ json: [] });
  });

  await openSimulatedAdmin(page, '/it/admin/cad-invoices');
  await expect(
    page.getByRole('heading', { name: 'Fatture e prestazioni' }),
  ).toBeVisible();

  const attachmentsSection = page.locator('.invoice-attachments');
  await expect(
    attachmentsSection.getByRole('heading', { name: 'Allegati fattura' }),
  ).toBeVisible();
  await expect(
    attachmentsSection.getByText('Salva la fattura per caricare gli allegati.'),
  ).toBeVisible();

  await page.getByLabel('ID sessione calcolatore (opzionale)').fill(sessionId);

  const sessionFiles = page.locator('.session-files');
  await expect(
    sessionFiles.getByRole('heading', { name: 'Correzione prezzi file' }),
  ).toBeVisible();
  const row = sessionFiles
    .locator('table.managed-items-table tbody tr')
    .first();
  await expect(row).toContainText('fixture.stl');
  await expect(
    attachmentsSection.locator('.attachment-thumb').first(),
  ).toBeVisible();
  await expect(attachmentsSection.getByText('report.pdf')).toBeVisible();
  await expect(
    attachmentsSection.getByText('Scaricabile dopo il pagamento'),
  ).toBeVisible();

  const sectionOrder = await page.evaluate(() => {
    const files = document.querySelector('.session-files');
    const attachments = document.querySelector('.invoice-attachments');
    const heading = Array.from(document.querySelectorAll('h3')).find(
      (candidate) => candidate.textContent?.trim() === 'Prestazioni',
    );
    if (!files || !attachments || !heading)
      return {files: false, attachments: false};
    return {
      files: Boolean(
        files.compareDocumentPosition(heading) &
        Node.DOCUMENT_POSITION_FOLLOWING,
      ),
      attachments: Boolean(
        attachments.compareDocumentPosition(heading) &
          Node.DOCUMENT_POSITION_PRECEDING,
      ),
    };
  });
  expect(sectionOrder.files).toBe(true);
  expect(sectionOrder.attachments).toBe(true);

  const saveButton = sessionFiles.getByRole('button', {
    name: 'Salva modifiche',
    exact: true,
  });
  await expect(saveButton).toBeDisabled();

  // Real keystrokes must stay in the same input while the price preview resets.
  for (const [index, value] of ['12', '34', '19,93'].entries()) {
    const input = row.locator('input').nth(index);
    await input.fill('');
    await input.focus();
    await page.keyboard.type(value);
    await expect(input).toHaveValue(value);
    await expect(input).toBeFocused();
  }

  await row.locator('input').nth(0).fill('2');
  await row.locator('input').nth(1).fill('0');
  await row.locator('input').nth(2).fill('200');
  await sessionFiles
    .getByRole('button', { name: 'Aggiorna prezzo', exact: true })
    .click();

  expect(updateCalls).toEqual([{ persist: false }]);
  await expect(row).toContainText('20.00');
  await expect(sessionFiles).toContainText('40.00');
  await expect(saveButton).toBeEnabled();

  await row.locator('input').nth(2).fill('150');
  await expect(saveButton).toBeDisabled();
  await sessionFiles
    .getByRole('button', { name: 'Aggiorna prezzo', exact: true })
    .click();
  await expect(saveButton).toBeEnabled();

  await saveButton.click();

  expect(updateCalls.at(-1)).toEqual({ persist: true });
  await expect(
    page.getByText(
      'Prezzi aggiornati. Il cliente deve ricaricare la pagina checkout.',
      { exact: true },
    ),
  ).toBeVisible();

  await expect(page.locator('#invoice-attachment-files')).toBeHidden();
  const addFiles = attachmentsSection.getByRole('button', {
    name: 'Aggiungi file',
    exact: true,
  });
  for (const width of [1280, 390]) {
    await page.setViewportSize({width, height: 844});
    await addFiles.scrollIntoViewIfNeeded();
    await expect(addFiles).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath(`invoice-attachments-${width}.png`),
    });
  }
  const choosing = page.waitForEvent('filechooser');
  await addFiles.click();
  const chooser = await choosing;
  await chooser.setFiles({
    name: 'model.stl',
    mimeType: 'model/stl',
    buffer: Buffer.from('solid test'),
  });
  expect(uploadCalls).toBe(1);
});

test('UI-INVOICE-007: simulated checkout makes quantity and color choices clear on narrow screens', async ({
                                                                                                             page,
                                                                                                           }, testInfo) => {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/options'))
      return route.fulfill({
        json: {
          materials: [
            {
              code: 'PLA',
              variants: [
                {
                  id: 1,
                  colorName: 'Dark Brown',
                  hexColor: '#654321',
                  colorLabelIt: 'Marrone scuro',
                },
                {
                  id: 2,
                  colorName: 'Red',
                  hexColor: '#ff0000',
                  colorLabelIt: 'Rosso',
                },
              ],
            },
          ],
        },
      });
    if (path === '/api/quote-sessions/editable-summary')
      return route.fulfill({
        json: {
          session: {
            id: 'editable-summary',
            status: 'CAD_ACTIVE',
            materialCode: 'PLA',
          },
          items: [
            {
              id: 'part',
              originalFilename: 'RASPBERRY_PI_5_.stl',
              quantity: 2,
              materialCode: 'PLA',
              colorCode: 'Dark Brown',
              filamentVariantId: 1,
              printTimeSeconds: 7200,
              materialGrams: 29,
              unitPriceChf: 4.69,
              status: 'READY',
            },
          ],
          itemsTotalChf: 9.38,
          shippingCostChf: 4,
          grandTotalChf: 13.38,
        },
      });
    if (path.endsWith('/resume')) return route.fulfill({json: null});
    if (path.endsWith('/stl-preview')) return route.fulfill({status: 404});
    return route.fulfill({json: []});
  });
  await page.goto('/it/checkout/cad?session=editable-summary');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  for (const width of [1280, 390, 320]) {
    await page.setViewportSize({width, height: 844});
    const editor = page.locator('.cad-item-editor');
    await expect(editor.locator('.qty-group')).toContainText('Quantità');
    await expect(editor.locator('.color-group')).toContainText(/colore/i);
    await expect(editor.getByRole('spinbutton')).toHaveValue('2');
    await expect(
      editor.getByRole('button', {name: /Marrone scuro|Dark Brown/}),
    ).toBeEnabled();
    await expect(page.locator('.summary-item .item-price')).toContainText(
      '9.38',
    );
    const qtyBounds = await editor.locator('.qty-group').boundingBox();
    const colorBounds = await editor.locator('.color-group').boundingBox();
    expect(colorBounds!.y).toBeGreaterThan(qtyBounds!.y + qtyBounds!.height);
    const quantityInput = await editor.getByRole('spinbutton').boundingBox();
    expect(quantityInput!.height).toBe(32);
    expect(quantityInput!.width).toBeLessThanOrEqual(64);
    expect((await editor.boundingBox())!.height).toBeLessThan(140);
    expect(
      await editor
        .locator('.trigger')
        .evaluate((element) => getComputedStyle(element).borderTopWidth),
    ).toBe('0px');
    const wordLines = await editor
      .locator('.trigger-copy strong')
      .evaluate((element) => {
        const text = element.firstChild!;
        const range = document.createRange();
        range.setStart(text, 0);
        range.setEnd(text, 'Marrone'.length);
        return range.getClientRects().length;
      });
    expect(wordLines).toBe(1);

    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await editor.scrollIntoViewIfNeeded();
    await page.screenshot({
      path: testInfo.outputPath(`checkout-controls-${width}.png`),
    });
  }
});

test('UI-INVOICE-008: simulated mixed-format checkout reuses stored model previews in compact summaries', async ({
                                                                                                                   page,
                                                                                                                 }, testInfo) => {
  const formats = ['stl', 'obj', '3mf', 'step', 'STP'];
  const previews = new Set<string>();
  const errors: string[] = [];
  let editedQuantity = 1;
  let editedVariant = 1;
  const edits: { quantity: number; filamentVariantId: number }[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const stl =
    'solid fixture\nfacet normal 0 0 1\nouter loop\nvertex 0 0 0\nvertex 20 0 0\nvertex 0 20 20\nendloop\nendfacet\nendsolid fixture';
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/options'))
      return route.fulfill({
        json: {
          materials: [
            {
              code: 'PLA',
              variants: [
                {
                  id: 1,
                  colorName: 'Orange',
                  colorLabelIt: 'Arancione',
                  hexColor: '#ffa500',
                },
                {
                  id: 2,
                  colorName: 'Red',
                  colorLabelIt: 'Rosso',
                  hexColor: '#ff0000',
                },
              ],
            },
          ],
        },
      });
    if (
      path === '/api/quote-sessions/compact-summary' ||
      path.startsWith('/api/quote-sessions/compact-summary/cad-items/')
    ) {
      if (route.request().method() === 'PATCH') {
        const edit = route.request().postDataJSON() as {
          quantity: number;
          filamentVariantId: number;
        };
        edits.push(edit);
        editedQuantity = edit.quantity;
        editedVariant = edit.filamentVariantId;
      }
      return route.fulfill({
        json: {
          session: {
            id: 'compact-summary',
            status: 'CAD_ACTIVE',
            materialCode: 'PLA',
          },
          items: formats.map((format, index) => ({
            id: `model-${index}`,
            originalFilename: `sample.${format}`,
            quantity: index === 0 ? editedQuantity : 1,
            materialCode: 'PLA',
            colorCode: index === 0 && editedVariant === 2 ? 'Red' : 'Orange',
            filamentVariantId: index === 0 ? editedVariant : 1,
            printTimeSeconds: 5400,
            materialGrams: 20,
            unitPriceChf: 10,
            status: 'READY',
          })),
          itemsTotalChf: 40 + editedQuantity * 10,
          shippingCostChf: 4,
          grandTotalChf: 44 + editedQuantity * 10,
        },
      });
    }
    if (path.endsWith('/stl-preview')) {
      previews.add(path.split('/').at(-2)!);
      return route.fulfill({contentType: 'model/stl', body: stl});
    }
    if (path.endsWith('/resume')) return route.fulfill({json: null});
    return route.fulfill({json: []});
  });
  await page.goto('/it/checkout/cad?session=compact-summary');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  const cards = page.locator('.print-summary-item');
  await expect(cards).toHaveCount(5);
  await expect(cards.locator('canvas')).toHaveCount(5);
  expect(previews.size).toBe(5);
  await expect(cards.first().locator('.item-specs')).toContainText('PLA');
  await expect(cards.first().locator('.item-specs')).toContainText('1.5h');
  await expect(cards.first().locator('.item-specs')).toContainText('20g');
  const title = await cards.first().locator('.item-name').boundingBox();
  const price = await cards.first().locator('.item-price').boundingBox();
  expect(price!.x).toBeGreaterThan(title!.x);
  expect(Math.abs(title!.y - price!.y)).toBeLessThan(2);
  expect(
    (await cards.first().locator('.cad-item-editor').boundingBox())!.height,
  ).toBeLessThan(210);
  const compactPreview = await cards
    .first()
    .locator('.item-preview')
    .boundingBox();
  const compactEditor = await cards
    .first()
    .locator('.cad-item-editor')
    .boundingBox();
  expect(Math.abs(compactPreview!.height - compactEditor!.height)).toBeLessThan(
    2,
  );
  expect(compactPreview!.width).toBeGreaterThan(150);
  const previewCanvas = await cards.first().locator('canvas').boundingBox();
  expect(Math.abs(previewCanvas!.height - compactPreview!.height)).toBeLessThan(
    4,
  );
  expect(Math.abs(compactPreview!.y - compactEditor!.y)).toBeLessThan(2);
  expect((await cards.first().boundingBox())!.height).toBeLessThan(260);
  const heading = await page
    .locator('.checkout-summary-header h3')
    .boundingBox();
  const expand = await page
    .locator('.checkout-summary-header')
    .getByRole('button', {
      name: 'Ingrandisci riepilogo',
      exact: true,
    })
    .boundingBox();
  expect(expand!.x).toBeGreaterThan(heading!.x);
  expect(
    Math.abs(
      heading!.y + heading!.height / 2 - (expand!.y + expand!.height / 2),
    ),
  ).toBeLessThan(2);
  await cards.first().scrollIntoViewIfNeeded();
  await page.screenshot({
    path: testInfo.outputPath('checkout-normal-compact.png'),
  });
  for (const width of [1280, 390]) {
    await page.setViewportSize({width, height: 844});
    const list = page.locator('.summary-items');
    const colorTrigger = cards.first().locator('.trigger');
    await colorTrigger.scrollIntoViewIfNeeded();
    await list.evaluate((element) => {
      element.scrollTop = 64;
    });
    const originalCanvas = await cards
      .first()
      .locator('canvas')
      .elementHandle();
    const originalCard = await cards.first().elementHandle();
    const listPosition = await list.evaluate((element) => element.scrollTop);
    const pagePosition = await page.evaluate(() => window.scrollY);
    expect(listPosition).toBeGreaterThan(0);
    for (const color of ['Rosso', 'Arancione']) {
      await colorTrigger.click();
      await page
        .locator('.color-popup')
        .getByRole('button', {name: color, exact: true})
        .click();
      await expect(colorTrigger).toHaveAccessibleName(color);
      expect(
        await originalCanvas!.evaluate((element) => element.isConnected),
      ).toBe(true);
      expect(
        await originalCard!.evaluate((element) => element.isConnected),
      ).toBe(true);
      expect(await list.evaluate((element) => element.scrollTop)).toBeCloseTo(
        listPosition,
        0,
      );
      expect(await page.evaluate(() => window.scrollY)).toBeCloseTo(
        pagePosition,
        0,
      );
    }
    await originalCanvas!.dispose();
    await originalCard!.dispose();
  }
  for (const width of [1280, 390]) {
    await page.setViewportSize({width, height: 844});
    await page
      .getByRole('button', {name: 'Ingrandisci riepilogo', exact: true})
      .filter({visible: true})
      .first()
      .click();
    const dialog = page.getByRole('dialog', {
      name: 'Riepilogo Ordine',
      exact: true,
    });
    await expect(dialog.locator('.print-summary-item canvas')).toHaveCount(5);
    await expect(dialog.locator('.loading-overlay')).toHaveCount(0);
    const thumbnail = await dialog
      .locator('.item-preview')
      .first()
      .boundingBox();
    expect(Math.abs(thumbnail!.width - thumbnail!.height)).toBeLessThan(4);
    expect(thumbnail!.width).toBeGreaterThanOrEqual(120);
    const controls = dialog.locator('.cad-item-editor').first();
    await expect(controls.getByRole('spinbutton')).toBeVisible();
    const quantityBounds = await controls.locator('.qty-group').boundingBox();
    const colorBounds = await controls.locator('.color-group').boundingBox();
    expect(colorBounds!.y).toBeGreaterThan(
      quantityBounds!.y + quantityBounds!.height,
    );
    expect(Math.abs(colorBounds!.x - quantityBounds!.x)).toBeLessThan(2);
    expect(
      (await controls.getByRole('spinbutton').boundingBox())!.height,
    ).toBeGreaterThanOrEqual(40);
    const viewerBorder = await dialog
      .locator('.viewer-container')
      .first()
      .evaluate((el) => getComputedStyle(el).borderTopWidth);
    expect(viewerBorder).toBe('0px');
    const first = await dialog
      .locator('.print-summary-item')
      .nth(0)
      .boundingBox();
    const second = await dialog
      .locator('.print-summary-item')
      .nth(1)
      .boundingBox();
    if (width > 700) {
      expect(second!.x).toBeGreaterThan(first!.x);
      expect(Math.abs(first!.y - second!.y)).toBeLessThan(2);
    } else {
      expect(second!.y).toBeGreaterThan(first!.y);
      expect(
        await dialog.evaluate(
          (element) => element.scrollWidth <= element.clientWidth,
        ),
      ).toBe(true);
    }
    await page.screenshot({
      path: testInfo.outputPath(`checkout-expanded-${width}.png`),
    });
    const popupCanvas = await dialog.locator('canvas').first().elementHandle();
    const nextQuantity = width > 700 ? 3 : 4;
    await controls.getByRole('spinbutton').fill(String(nextQuantity));
    await controls.getByRole('spinbutton').press('Tab');
    await expect(controls.getByRole('spinbutton')).toHaveValue(
      String(nextQuantity),
    );
    await expect(dialog.locator('.item-total-price').first()).toContainText(
      `${nextQuantity * 10}.00`,
    );
    await controls.locator('.trigger').click();
    await expect(dialog.locator('.color-popup')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('.color-popup')).toHaveCount(0);
    await controls.locator('.trigger').click();
    const nextColor = width > 700 ? 'Rosso' : 'Arancione';
    await dialog
      .locator('.color-popup')
      .getByRole('button', {name: nextColor, exact: true})
      .click();
    await expect(controls.locator('.trigger')).toHaveAccessibleName(nextColor);
    await expect(dialog.locator('.color-popup')).toHaveCount(0);
    expect(await popupCanvas!.evaluate((element) => element.isConnected)).toBe(
      true,
    );
    await popupCanvas!.dispose();
    await dialog
      .locator('.print-summary-item')
      .nth(3)
      .getByRole('button', {name: 'Apri 3D', exact: true})
      .click();
    const enlarged = page.getByRole('dialog', {
      name: 'sample.step',
      exact: true,
    });
    await expect(enlarged.locator('canvas')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(enlarged).toHaveCount(0);
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
  }
  expect(edits).toEqual([
    {quantity: 1, filamentVariantId: 2},
    {quantity: 1, filamentVariantId: 1},
    {quantity: 1, filamentVariantId: 2},
    {quantity: 1, filamentVariantId: 1},
    {quantity: 3, filamentVariantId: 1},
    {quantity: 3, filamentVariantId: 2},
    {quantity: 4, filamentVariantId: 2},
    {quantity: 4, filamentVariantId: 1},
  ]);
  await expect(
    page.locator('.cad-item-editor').first().getByRole('spinbutton'),
  ).toHaveValue('4');
  expect(errors).toEqual([]);
});


test('UI-INVOICE-009: simulated removal keeps the checkout and groups responsive table actions', async ({
  page,
}, testInfo) => {
  const id = '99999999-9999-9999-9999-999999999999';
  let removed = false;
  const serviceLines = [
    {
      description: 'Progettazione conservata',
      billingType: 'FIXED',
      quantity: 1,
      unitPriceChf: 50,
    },
  ];
  const invoice = () => ({
    sessionId: id,
    sessionStatus: 'CAD_ACTIVE',
    invoiceName: 'Prototipo con un nome molto lungo',
    serviceLines,
    cadHours: 0,
    cadHourlyRateChf: 0,
    cadTotalChf: 0,
    printItemsTotalChf: removed ? 0 : 20,
    setupCostChf: removed ? 0 : 5,
    shippingCostChf: removed ? 0 : 2,
    grandTotalChf: removed ? 50 : 77,
    checkoutPath: `/checkout/cad?session=${id}`,
  });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/me') || path.endsWith('/auth/login'))
      return route.fulfill({ json: { authenticated: true } });
    if (path.endsWith('/cad-invoices'))
      return route.fulfill({ json: [invoice()] });
    if (
      path.endsWith(`/cad-invoices/${id}`) &&
      route.request().method() === 'PUT'
    ) {
      const body = route.request().postDataJSON();
      expect(body.removePrintItems).toBe(true);
      expect(body.sessionId).toBe(id);
      expect(body.serviceLines).toEqual(serviceLines);
      removed = true;
      return route.fulfill({ json: invoice() });
    }
    if (path.endsWith('/items'))
      return route.fulfill({
        json: {
          sessionId: id,
          sessionStatus: 'CAD_ACTIVE',
          itemsTotalChf: removed ? 50 : 77,
          grandTotalChf: removed ? 50 : 77,
          items: removed
            ? []
            : [
                {
                  id: 'print',
                  displayName: 'RASPBERRY_PI_5_long_model_filename.stl',
                  quantity: 9,
                  printTimeSeconds: 7020,
                  materialGrams: 29.11,
                  unitPriceChf: 4.18,
                  editable: true,
                },
              ],
          printItemsTotalChf: removed ? 0 : 20,
        },
      });
    return route.fulfill({ json: [] });
  });
  await openSimulatedAdmin(page, '/it/admin/cad-invoices');
  const table = page.locator('.cad-invoices-table');
  await expect(
    table.getByRole('button', { name: 'Modifica prestazioni', exact: true }),
  ).toHaveCount(1);
  await expect(
    table.getByRole('button', { name: 'Gestisci file', exact: true }),
  ).toHaveCount(0);
  await table
    .getByRole('button', { name: 'Modifica prestazioni', exact: true })
    .click();
  await expect(page.getByLabel('ID fattura / checkout')).toHaveValue(id);
  await expect(page.locator('.managed-items-table')).toBeVisible();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath(`invoice-editor-${width}.png`),
      fullPage: true,
    });
    await table.screenshot({
      path: testInfo.outputPath(`invoice-table-${width}.png`),
    });
    const cells = table.locator('tbody tr').first().locator('td');
    expect(
      await cells.last().evaluate((el) => getComputedStyle(el).display),
    ).not.toBe('flex');
  }
  await page
    .getByRole('button', { name: 'Rimuovi stampe dalla fattura', exact: true })
    .click();
  await expect(page.locator('.managed-items-table')).toHaveCount(0);
  await page
    .getByRole('button', { name: 'Mantieni le stampe', exact: true })
    .click();
  await expect(page.locator('.managed-items-table')).toBeVisible();
  await page
    .getByRole('button', { name: 'Rimuovi stampe dalla fattura', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Salva e prepara checkout', exact: true })
    .click();
  await expect(
    page.getByLabel('Descrizione e dettagli', { exact: true }),
  ).toHaveValue('Progettazione conservata');
  await expect(page.getByLabel('ID fattura / checkout')).toHaveValue(id);
  await expect(page.locator('.managed-items-table')).toHaveCount(0);
  await expect(table.locator('tbody tr')).toContainText('50.00');
  expect(removed).toBe(true);
});
