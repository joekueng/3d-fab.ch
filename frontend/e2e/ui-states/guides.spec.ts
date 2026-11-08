import { expect, test } from '@playwright/test';
import { mediaUsage, options } from '../../scripts/fixtures/guides-data.mjs';

// All catalogue/photo/price responses here are explicitly synthetic doubles.
test('UI-GUIDES-001: synthetic catalogue filters, compares two variants and opens a keyboard gallery', async ({
  page,
}) => {
  await page.goto('/it/pricing');
  await page.route('**/api/calculator/options', (route) =>
    route.fulfill({ json: options }),
  );
  await page.route('**/api/public/media/usages?**', (route) => {
    const url = new URL(route.request().url());
    return route.fulfill({
      json: mediaUsage(
        url.searchParams.get('usageType')!,
        url.searchParams.get('usageKey')!,
        url.searchParams.get('lang')!,
      ),
    });
  });
  await page.route('**/__fixtures/sample-*.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#ddd"/><text x="30" y="200">Synthetic test sample</text></svg>',
    }),
  );
  await page.locator('footer a[href="/it/colors-finishes"]').click();
  const catalogue = page.locator('.variants');
  await expect(catalogue.locator('article')).toHaveCount(3);
  const black = catalogue.getByRole('article', {
    name: 'PLA Nero campione',
    exact: true,
  });
  const white = catalogue.getByRole('article', {
    name: 'PLA Bianco campione',
    exact: true,
  });
  const blue = catalogue.getByRole('article', {
    name: 'PETG Blu campione',
    exact: true,
  });
  await expect(white.getByText('Al momento esaurito')).toBeVisible();
  await expect(white.getByText('Foto non ancora disponibile')).toBeVisible();
  await expect(black.locator('app-image-preview')).toHaveCount(3);
  // Primary is sortOrder=1 in the fixture, so it must be shown before sortOrder=0.
  await expect(black.locator('app-image-preview img').first()).toHaveAttribute(
    'src',
    /sample-1.svg/,
  );
  const trigger = black.getByRole('button', {
    name: /Campione sintetico per test 2/,
  });
  await trigger.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('dialog').locator(':focus')).toHaveCount(1);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await black.getByRole('checkbox').check();
  await white.getByRole('checkbox').check();
  await expect(page.locator('.comparison article')).toHaveCount(2);
  await expect(blue.getByRole('checkbox')).toBeDisabled();
  await page
    .getByRole('combobox', { name: 'Materiale', exact: true })
    .selectOption({ label: 'PETG' });
  await expect(catalogue.locator('article')).toHaveCount(1);
  await expect(page.locator('.comparison article')).toHaveCount(2);
  await page
    .getByRole('combobox', { name: 'Finitura', exact: true })
    .selectOption({ label: 'Traslucida' });
  await expect(catalogue.locator('article')).toHaveCount(1);
  await page
    .getByRole('combobox', { name: 'Materiale', exact: true })
    .selectOption({ label: 'PLA' });
  await expect(catalogue.locator('article')).toHaveCount(2); // material change resets finish.
  await page
    .getByRole('combobox', { name: 'Finitura', exact: true })
    .selectOption({ label: 'Opachi' });
  await expect(catalogue.locator('article')).toHaveCount(1);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.comparison article')).toHaveCount(2);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await expect(page.locator('.site-intro')).toHaveCount(0);
  await page.screenshot({
    path: 'test-results/guides-colors-mobile.png',
    fullPage: true,
  });
});

test('UI-GUIDES-002: simulated options and photo failures show fallbacks and retry recovers', async ({
  page,
}) => {
  await page.goto('/it/pricing');
  let fail = true;
  await page.route('**/api/calculator/options', (route) =>
    fail
      ? route.fulfill({ status: 503, json: {} })
      : route.fulfill({ json: options }),
  );
  await page.route('**/api/public/media/usages?**', (route) =>
    route.fulfill({ status: 503, json: {} }),
  );
  await page.locator('footer a[href="/it/colors-finishes"]').click();
  await expect(page.getByRole('alert')).toContainText(
    'Il catalogo non è disponibile',
  );
  fail = false;
  await page.getByRole('button', { name: 'Riprova', exact: true }).click();
  await expect(page.locator('.variants article')).toHaveCount(3);
  await expect(
    page.locator('.variants').getByText('Foto non ancora disponibile'),
  ).toHaveCount(3);
  await page
    .getByRole('combobox', { name: 'Materiale', exact: true })
    .selectOption({ label: 'PLA' });
  await page
    .getByRole('combobox', { name: 'Finitura', exact: true })
    .selectOption({ label: 'Opachi' });
  await expect(page.locator('.variants article')).toHaveCount(1);
  await page.locator('footer a[href="/it/pricing"]').click();
  await page.unroute('**/api/public/media/usages?**');
  await page.route('**/api/public/media/usages?**', (route) => {
    const url = new URL(route.request().url());
    return route.fulfill({
      json: mediaUsage(
        url.searchParams.get('usageType')!,
        url.searchParams.get('usageKey')!,
        'it',
      ).map((item) => ({
        ...item,
        card: item.card
          ? {
              ...item.card,
              jpegUrl:
                item.card.jpegUrl?.replace('.svg', '-broken.svg') ?? null,
            }
          : null,
        hero: item.hero
          ? {
              ...item.hero,
              jpegUrl:
                item.hero.jpegUrl?.replace('.svg', '-broken.svg') ?? null,
            }
          : null,
      })),
    });
  });
  await page.route('**/__fixtures/sample-*.svg', (route) =>
    route.fulfill({ status: 404, body: '' }),
  );
  await page.locator('footer a[href="/it/colors-finishes"]').click();
  const brokenPhotoCard = page
    .locator('.variants')
    .getByRole('article', { name: 'PLA Nero campione', exact: true });
  await brokenPhotoCard.scrollIntoViewIfNeeded();
  await expect(
    brokenPhotoCard.getByText('Foto non ancora disponibile'),
  ).toBeVisible();
  await expect(brokenPhotoCard.locator('app-image-preview')).toHaveCount(0);
  await page.locator('footer a[href="/it/pricing"]').click();
  await page.route('**/api/calculator/options', (route) =>
    route.fulfill({ json: { ...options, materials: [] } }),
  );
  await page.locator('footer a[href="/it/colors-finishes"]').click();
  await expect(page.locator('.variants article')).toHaveCount(0);
  await expect(
    page.getByText('Nessuna variante per questi filtri.', { exact: false }),
  ).toBeVisible();
});

test('UI-GUIDES-003: synthetic pricing data renders all locales and consultation links preserve language', async ({
  page,
}) => {
  const titles = {
    it: 'Il prezzo del tuo progetto',
    en: 'The price of your project',
    de: 'Der Preis für dein Projekt',
    fr: 'Le prix de ton projet',
  };
  for (const [lang, title] of Object.entries(titles)) {
    await page.goto(`/${lang}/colors-finishes`);
    await page.route('**/api/public/media/usages?**', (route) => {
      const url = new URL(route.request().url());
      return route.fulfill({
        json: mediaUsage(
          url.searchParams.get('usageType')!,
          url.searchParams.get('usageKey')!,
          url.searchParams.get('lang')!,
        ),
      });
    });
    await page.locator(`footer a[href="/${lang}/pricing"]`).click();
    await expect(
      page.getByRole('heading', { name: title, exact: true }),
    ).toBeVisible();
    await expect(page.locator('#cad-title')).toBeVisible();
    await expect(page.locator('#projects-title')).toHaveCount(0);
    await expect(page.locator('.cad-guidance a')).toHaveAttribute(
      'href',
      `/${lang}/contact?requestType=consult`,
    );
    await expect(page.locator('time')).toHaveCount(3);
    await expect(
      page
        .locator('a[href="/' + lang + '/contact?requestType=consult"]')
        .first(),
    ).toBeVisible();
    await expect(page.locator('.site-intro')).toHaveCount(0);
    if (lang === 'it') {
      await page.setViewportSize({ width: 1440, height: 1000 });
      await page.screenshot({
        path: 'test-results/guides-pricing-desktop.png',
        fullPage: true,
      });
    }
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
  await page
    .locator('a[href="/fr/contact?requestType=consult"]')
    .first()
    .click();
  await expect(page.locator('#contact-request-type')).toHaveValue('consult');
});
