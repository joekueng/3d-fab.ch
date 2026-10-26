import { expect, test, type Page } from '@playwright/test';

const guidePaths = ['materials', 'colors-finishes', 'pricing'];

async function openGuideNavigation(page: Page): Promise<void> {
  const header = page.locator('header.navbar');
  const hamburger = header.locator('.mobile-toggle');
  if (await hamburger.isVisible()) {
    await hamburger.click();
    await expect(hamburger).toHaveAttribute('aria-expanded', 'true');
  } else {
    await header.locator('.guides-trigger').click();
  }
  await expect(header.locator('.guides-trigger')).toHaveAttribute(
    'aria-expanded',
    'true',
  );
}

test('UI-NAV-GUIDES-001: grouped header and footer links keep all four locales without document reloads', async ({
  page,
}) => {
  for (const lang of ['it', 'en', 'de', 'fr']) {
    await page.goto(`/${lang}/contact`);
    await expect(page.locator('.site-intro')).toHaveCount(0);
    const timeOrigin = await page.evaluate(() => performance.timeOrigin);
    const footerGroup = page.locator(
      'footer nav[aria-labelledby="footer-print-guides-title"]',
    );
    await expect(footerGroup.locator('a')).toHaveCount(3);
    expect(
      await footerGroup
        .locator('a')
        .evaluateAll((links) => links.map((link) => link.getAttribute('href'))),
    ).toEqual(guidePaths.map((path) => `/${lang}/${path}`));
    for (const path of guidePaths) {
      await openGuideNavigation(page);
      const links = page.locator('#print-guide-navigation a');
      await expect(links).toHaveCount(3);
      await page
        .locator(`#print-guide-navigation a[href="/${lang}/${path}"]`)
        .click();
      await expect(page).toHaveURL(new RegExp(`/${lang}/${path}/?$`));
      await expect(page.locator('#print-guide-navigation')).toBeHidden();
      await expect(page.locator('header.navbar .nav-guides')).toHaveClass(
        /\bactive\b/,
      );
      await expect(
        footerGroup.locator(`a[href="/${lang}/${path}"]`),
      ).toHaveAttribute('aria-current', 'page');
      expect(await page.evaluate(() => performance.timeOrigin)).toBe(
        timeOrigin,
      );
    }
    await footerGroup.locator(`a[href="/${lang}/materials"]`).click();
    await expect(page).toHaveURL(new RegExp(`/${lang}/materials/?$`));
    expect(await page.evaluate(() => performance.timeOrigin)).toBe(timeOrigin);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
});

test('UI-NAV-GUIDES-002: keyboard, Escape and outside clicks close the guide disclosure', async ({
  page,
}, testInfo) => {
  await page.goto('/it/contact');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  const hamburger = page.locator('header.navbar .mobile-toggle');
  const mobile = await hamburger.isVisible();
  if (mobile) {
    await hamburger.focus();
    await page.keyboard.press('Enter');
    await expect(hamburger).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#print-guide-navigation')).toBeVisible();
    await page.locator('header.navbar .guides-trigger').click();
  }
  const trigger = page.locator('header.navbar .guides-trigger');
  await trigger.focus();
  await page.keyboard.press('Enter');
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  await page.keyboard.press('Tab');
  await expect(
    page.locator('#print-guide-navigation a[href="/it/materials"]'),
  ).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(
    page.locator('#print-guide-navigation a[href="/it/colors-finishes"]'),
  ).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  await expect(trigger).toHaveAttribute('aria-expanded', 'false');
  await page.keyboard.press('Enter');
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab'); // About, outside the disclosure.
  await expect(trigger).toHaveAttribute('aria-expanded', 'false');
  await trigger.click();
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  await page.screenshot({
    path: `test-results/navigation-guides-${testInfo.project.name}.png`,
  });
  // The heading is covered by the open dropdown/drawer; click exposed page space.
  const viewport = page.viewportSize()!;
  await page.mouse.click(4, viewport.height - 4);
  await expect(page.locator('#print-guide-navigation')).toBeHidden();
  if (mobile) {
    await expect(hamburger).toHaveAttribute('aria-expanded', 'false');
    await hamburger.click();
    await page.keyboard.press('Escape'); // Closes guides first.
    await page.keyboard.press('Escape'); // Then closes the hamburger.
    await expect(hamburger).toHaveAttribute('aria-expanded', 'false');
    await expect(hamburger).toBeFocused();
  }
});

test('UI-NAV-GUIDES-003: mobile menu fits 320px and closing the cart keeps guide choices usable', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 640 });
  await page.goto('/de/contact');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  await page.locator('header.navbar .cart-trigger').click();
  await expect(page.locator('.cart-panel')).toBeVisible();
  await openGuideNavigation(page);
  await expect(page.locator('.cart-panel')).toHaveCount(0);
  await expect(
    page.locator('#print-guide-navigation a[href="/de/colors-finishes"]'),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page
    .locator('#print-guide-navigation a[href="/de/colors-finishes"]')
    .click();
  await expect(page).toHaveURL(/\/de\/colors-finishes$/);
  await expect(page.locator('header.navbar .mobile-toggle')).toHaveAttribute(
    'aria-expanded',
    'false',
  );
});

test('UI-NAV-GUIDES-004: mouse hover opens the flyout and touch retains tap controls', async ({
  page,
}) => {
  await page.goto('/it/contact');
  await expect(page.locator('.site-intro')).toHaveCount(0);
  const hamburger = page.locator('header.navbar .mobile-toggle');
  const trigger = page.locator('header.navbar .guides-trigger');
  const flyout = page.locator('#print-guide-navigation');
  if (await hamburger.isVisible()) {
    await hamburger.click();
    await expect(flyout).toBeVisible();
    await trigger.click();
    await expect(flyout).toBeHidden();
    await trigger.click();
    await expect(flyout).toBeVisible();
    return;
  }
  await trigger.hover();
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  const box = (await trigger.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height + 4);
  await expect(flyout).toBeVisible(); // Crossing the gap must not dismiss the menu.
  await page
    .locator('#print-guide-navigation a[href="/it/colors-finishes"]')
    .hover();
  await expect(flyout).toBeVisible();
  await page.locator('header.navbar nav > a[href="/it/about"]').hover();
  await expect(flyout).toBeHidden();
  await trigger.hover();
  await trigger.click();
  await expect(flyout).toBeVisible(); // Clicking an already hover-opened trigger keeps it usable.
  await page.keyboard.press('Escape');
  await expect(flyout).toBeHidden();
  await expect(trigger).toBeFocused();
});
