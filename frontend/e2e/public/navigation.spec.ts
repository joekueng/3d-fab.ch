import { expect, test } from '@playwright/test';

const locales = ['it', 'en', 'de', 'fr'] as const;

for (const locale of locales) {
  test(`PUBLIC-001: ${locale} navigation, language switch, and legal links`, async ({ page }) => {
    await page.goto(`/${locale}/contact`);
    await expect(page.locator('header.navbar nav a[href="/' + locale + '/contact"]')).toBeVisible();

    const privacy = page.locator('footer a[href="/' + locale + '/privacy"]');
    await privacy.click();
    await expect(page).toHaveURL(new RegExp(`/${locale}/privacy/?$`));
    await expect(page.locator('.legal-page h1')).toBeVisible();

    await page.goto(`/${locale}/privacy?source=e2e#privacy`);
    await page.waitForLoadState('networkidle');

    const switcher = page.locator('header select.lang-switch');
    await switcher.selectOption(locale === 'it' ? 'en' : 'it');
    const nextLocale = locale === 'it' ? 'en' : 'it';
    await expect(page).toHaveURL(new RegExp(`/${nextLocale}/privacy\\?source=e2e#privacy$`));
    await expect(page.locator('.legal-page h1')).toBeVisible();
    await page.reload();
    await expect(page).toHaveURL(new RegExp(`/${nextLocale}/privacy\\?source=e2e#privacy$`));
  });
}

test('PUBLIC-002: static public pages fit a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  for (const route of ['/it', '/de/about', '/fr/materials', '/en/contact']) {
    await page.goto(route);
    await expect(page.locator('h1').first()).toBeVisible();
    const overflow = await page.evaluate(() =>
      document.documentElement.scrollWidth > window.innerWidth + 2,
    );
    expect(overflow, `${route} has horizontal overflow`).toBe(false);
  }
});

test('PUBLIC-003: contact form validates required fields without submitting', async ({ page }) => {
  await page.goto('/en/contact');
  const form = page.locator('app-contact-form form');
  await expect(form).toBeVisible();
  const submit = form.locator('button[type="submit"]');
  await expect(submit).toBeDisabled();

  await form.locator('app-input[formcontrolname="email"] input').fill('invalid');
  await form.locator('app-input[formcontrolname="name"] input').fill('Browser Test');
  await expect(submit).toBeDisabled();
  await form.locator('app-input[formcontrolname="email"] input').fill('browser@example.test');
  await expect(submit).toBeDisabled();

  const privacy = form.locator('a[href="/en/privacy"]').first();
  await expect(privacy).toBeVisible();
  await expect(privacy).toHaveAttribute('target', '_blank');
});
