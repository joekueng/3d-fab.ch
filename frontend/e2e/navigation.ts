import { expect, Page } from '@playwright/test';

export async function openNavigation(page: Page): Promise<void> {
  const navbar = page.locator('header.navbar');
  await expect(navbar).toBeVisible();
  const toggle = navbar.locator('.mobile-toggle');
  if (await toggle.isVisible()) {
    await toggle.click();
    await expect(navbar.locator('nav')).toHaveClass(/\bopen\b/);
  }
  await expect(navbar.locator('nav')).toBeVisible();
}
