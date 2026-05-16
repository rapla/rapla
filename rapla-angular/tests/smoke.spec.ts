import { test, expect } from '@playwright/test';

// Tier-7 smoke test. Requires the rapla dev server running (AGENTS.md §8) —
// the SPA is served by Spring Boot at /app/. Run with `npm run e2e`.
test.describe('SPA smoke', () => {
  test('the Angular app mounts at /app/', async ({ page }) => {
    await page.goto('/app/');
    await expect(page.locator('app-root')).toBeAttached();
  });
});
