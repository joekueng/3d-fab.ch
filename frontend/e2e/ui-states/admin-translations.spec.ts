import {expect, Page, test} from '@playwright/test';

async function login(page: Page, area: string) {
  await page.goto(
    `/it/admin/login?redirect=${encodeURIComponent(`/it/admin/${area}`)}`,
  );
  // Wait for the server-rendered form to hydrate before entering its password.
  await expect(page.locator('app-admin-login app-input')).not.toHaveAttribute(
    'ngh',
    /.*/,
  );
  await page.locator('input#admin-password').fill('synthetic-password');
  await page.getByRole('button', {name: 'Accedi', exact: true}).click();
  await expect(page.locator('.site-intro')).toHaveCount(0);
}

test('UI-ADMIN-TRANSLATION-001: simulated bulk color translation saves all languages from the header', async ({
                                                                                                                page,
                                                                                                              }, testInfo) => {
  let translated = false;
  let fail = false;
  let release: () => void = () => {
  };
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  const variant = {
    id: 1,
    materialTypeId: 1,
    materialCode: 'PLA',
    variantDisplayName: 'PLA Black',
    colorName: 'Black',
    colorLabelIt: 'Nero opaco',
    colorLabelEn: 'Old English',
    colorLabelDe: 'Alt',
    colorLabelFr: 'Ancien',
    colorHex: '#111111',
    finishType: 'MATTE',
    costChfPerKg: 20,
    stockSpools: 2,
    spoolNetKg: 1,
    isActive: true,
  };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/login') || path.endsWith('/auth/me'))
      return route.fulfill({json: {authenticated: true}});
    if (path.endsWith('/translate-colors')) {
      expect(route.request().method()).toBe('POST');
      if (fail)
        return route.fulfill({
          status: 503,
          json: {message: 'Synthetic translation unavailable'},
        });
      await pending;
      translated = true;
      return route.fulfill({
        json: {translatedColors: 1, filamentVariants: 1, shopVariants: 3},
      });
    }
    if (path.endsWith('/filaments/materials'))
      return route.fulfill({json: [{id: 1, materialCode: 'PLA'}]});
    if (path.endsWith('/filaments/variants'))
      return route.fulfill({
        json: [
          {
            ...variant,
            ...(translated
              ? {
                colorLabelEn: 'Matte black',
                colorLabelDe: 'Mattschwarz',
                colorLabelFr: 'Noir mat',
              }
              : {}),
          },
        ],
      });
    return route.fulfill({json: []});
  });
  await login(page, 'filament-stock');
  const header = page.locator('header.section-header');
  const button = header.getByRole('button', {name: 'Traduci', exact: true});
  await expect(button).toBeVisible();
  await expect(
    header.getByRole('button', {name: 'Aggiorna', exact: true}),
  ).toBeVisible();
  await expect(
    page.getByRole('button', {name: 'Traduci', exact: true}),
  ).toHaveCount(1);
  const row = page.locator('.variant-row');
  await row.locator('.expand-toggle').click();
  await row.getByLabel('Costo CHF/kg', {exact: true}).fill('27');
  await button.click();
  await expect(
    header.getByRole('button', {name: 'Traduco…', exact: true}),
  ).toBeDisabled();
  release();
  await expect(row.getByLabel('Label EN', {exact: true})).toHaveValue(
    'Matte black',
  );
  await expect(row.getByLabel('Label DE', {exact: true})).toHaveValue(
    'Mattschwarz',
  );
  await expect(row.getByLabel('Label FR', {exact: true})).toHaveValue(
    'Noir mat',
  );
  await expect(row.getByLabel('Label IT', {exact: true})).toHaveValue(
    'Nero opaco',
  );
  await expect(row.getByLabel('Costo CHF/kg', {exact: true})).toHaveValue(
    '27',
  );
  await expect(page.locator('.ui-banner--success')).toContainText('salvate');
  await page.setViewportSize({width: 390, height: 844});
  await header.scrollIntoViewIfNeeded();
  await page.screenshot({
    path: testInfo.outputPath('color-translations-mobile.png'),
  });
  expect(
    await page.locator('body').evaluate((element) => element.scrollWidth),
  ).toBeLessThanOrEqual(390);
  fail = true;
  await button.click();
  await expect(page.locator('.ui-banner--error')).toContainText(
    'Synthetic translation unavailable',
  );
  await expect(button).toBeEnabled();
  await expect(row.getByLabel('Label EN', {exact: true})).toHaveValue(
    'Matte black',
  );
});

test('UI-ADMIN-TRANSLATION-002: simulated category and product image translation fills the localized editors', async ({
                                                                                                                        page,
                                                                                                                      }, testInfo) => {
  const product = {
    id: 'product',
    categoryId: 'category',
    categoryName: 'Accessori',
    slug: 'supporto',
    name: 'Supporto',
    nameIt: 'Supporto',
    nameEn: 'Stand',
    nameDe: 'Halter',
    nameFr: 'Support',
    variants: [],
    images: [],
    mediaUsages: [],
    isActive: true,
    isFeatured: false,
    sortOrder: 0,
  };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/login') || path.endsWith('/auth/me'))
      return route.fulfill({json: {authenticated: true}});
    if (path.endsWith('/translations/localized-text')) {
      const payload = route.request().postDataJSON();
      expect(payload.sourceLanguage).toBe('it');
      if (payload.fields.name) {
        expect(payload.fields.description.required).toBe(true);
        return route.fulfill({
          json: {
            sourceLanguage: 'it',
            targetLanguages: ['en', 'de', 'fr'],
            fields: {
              name: {en: 'Accessories', de: 'Zubehör', fr: 'Accessoires'},
              description: {
                en: 'Printed accessories',
                de: 'Gedrucktes Zubehör',
                fr: 'Accessoires imprimés',
              },
              seoTitle: {
                en: '3D accessories',
                de: '3D-Zubehör',
                fr: 'Accessoires 3D',
              },
            },
          },
        });
      }
      expect(payload.fields.altText.values.it).toBe('Supporto nero');
      return route.fulfill({
        json: {
          sourceLanguage: 'it',
          targetLanguages: ['en', 'de', 'fr'],
          fields: {
            title: {
              en: 'Stand image',
              de: 'Bild des Halters',
              fr: 'Image du support',
            },
            altText: {
              en: 'Black stand',
              de: 'Schwarzer Halter',
              fr: 'Support noir',
            },
          },
        },
      });
    }
    if (path.endsWith('/shop/products/product'))
      return route.fulfill({json: product});
    if (path.endsWith('/shop/products'))
      return route.fulfill({json: [product]});
    return route.fulfill({json: []});
  });
  await login(page, 'shop');
  await page
    .getByRole('button', {name: 'Gestisci categorie', exact: true})
    .click();
  const category = page.locator('.category-editor');
  await category
    .getByRole('textbox', {name: 'Nome categoria IT', exact: true})
    .fill('Accessori');
  await category
    .getByRole('textbox', {name: 'Descrizione IT', exact: true})
    .fill('Accessori stampati');
  await category
    .getByRole('textbox', {name: 'SEO title IT', exact: true})
    .fill('Accessori 3D');
  await category.getByRole('button', {name: 'Traduci', exact: true}).click();
  await expect(category.getByRole('status')).toContainText('inserite');
  await category
    .locator('app-admin-language-toolbar')
    .first()
    .getByRole('button', {name: /^EN/})
    .click();
  await expect(
    category.getByRole('textbox', {name: 'Nome categoria EN', exact: true}),
  ).toHaveValue('Accessories');
  await expect(
    category.getByRole('textbox', {name: 'Descrizione EN', exact: true}),
  ).toHaveValue('Printed accessories');
  await expect(
    category.getByRole('textbox', {name: 'SEO title EN', exact: true}),
  ).toHaveValue('3D accessories');

  await page
    .locator('.shop-products-table tbody tr')
    .filter({hasText: 'Supporto'})
    .click();
  const image = page
    .locator('section.ui-subpanel--soft')
    .filter({has: page.locator('[name="productImageTitle"]')});
  await image.locator('[name="productImageTitle"]').fill('Immagine supporto');
  await image.locator('[name="productImageAltText"]').fill('Supporto nero');
  await image.getByRole('button', {name: 'Traduci', exact: true}).click();
  await expect(image.getByRole('status')).toContainText('inserite');
  await image
    .locator('app-admin-language-toolbar')
    .getByRole('button', {name: /^DE/})
    .click();
  await expect(image.locator('[name="productImageTitle"]')).toHaveValue(
    'Bild des Halters',
  );
  await expect(image.locator('[name="productImageAltText"]')).toHaveValue(
    'Schwarzer Halter',
  );
  await page.setViewportSize({width: 390, height: 844});
  await image.locator('app-admin-translate-action').scrollIntoViewIfNeeded();
  await page.screenshot({
    path: testInfo.outputPath('shop-translations-mobile.png'),
  });
});
