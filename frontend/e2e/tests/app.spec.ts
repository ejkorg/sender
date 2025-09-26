import { test, expect } from '@playwright/test';

test('app index shows welcome', async ({ page }) => {
  await page.goto('/');
  // The built app sets <title>Frontend</title> so assert that
  await expect(page).toHaveTitle(/Frontend/);
});
