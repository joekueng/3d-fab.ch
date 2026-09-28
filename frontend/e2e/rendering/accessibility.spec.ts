import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

for (const route of ['/en', '/en/contact', '/en/shop', '/de/privacy']) {
  test(`RENDER-007: automated accessibility scan for ${route}`, async ({ page }) => {
    const response = await page.goto(route);
    expect(response?.status()).toBe(200);
    await expect(page.locator('h1').first()).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    await test.info().attach('axe-violations.json', {
      body: JSON.stringify(results.violations, null, 2),
      contentType: 'application/json',
    });

    const critical = results.violations.filter((violation) => violation.impact === 'critical');
    expect(critical, `Critical accessibility violations on ${route}`).toEqual([]);
  });
}
