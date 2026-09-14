import { test, expect } from '@playwright/test';

/**
 * Tier-7 CSP enforce-readiness gate (PRD 071 Phase 2 Step 3 / Phase 4).
 *
 * The SPA + /login ship the Spring CSP **report-only** while the app is still
 * component-sparse (PRD 071): every directive except script-src (which the autoCsp
 * `<meta>` already enforces) is only *reported*, not blocked. This test is the
 * repeatable measurement that answers "would flipping to enforce break anything?":
 * it drives the authenticated SPA, walks the CDK overlays that exist, and asserts the
 * browser fired **zero report-only `securitypolicyviolation` events**. As components
 * land (forms, datepickers, charts, third-party widgets, external img/font/connect
 * origins), a new violation turns this red → "not enforce-ready yet / fix the component".
 * When it stays green across the matured component set, the report-only → enforce flip
 * is a measured decision, not a guess.
 *
 * Requires the rapla dev server running (AGENTS.md §8) with the dev-default admin
 * (empty password) so the report-only header is served on the real /app document.
 * Not yet wired into CI (PRD 034 Phase 4).
 *
 * The 2026-06-21 finding this codifies: Material/CDK positions overlays via the CSSOM
 * accessors (`el.style.x = …`), which CSP's style-src does NOT govern — so the dialog +
 * mat-select produce no violations. The **control canary** below is load-bearing: a
 * deliberate `setAttribute('style')` MUST fire, else the report-only header or the
 * listener is missing and the whole gate would be measuring blind (passing for the
 * wrong reason).
 */
test.describe('CSP enforce-readiness', () => {
  test('authenticated SPA walk produces no report-only CSP violations', async ({ page }) => {
    // Capture violations from the very first byte, re-installed on every full navigation.
    await page.addInitScript(() => {
      (window as any).__csp = [];
      document.addEventListener('securitypolicyviolation', (e: SecurityPolicyViolationEvent) => {
        (window as any).__csp.push({
          directive: e.violatedDirective,
          blockedURI: e.blockedURI,
          disposition: e.disposition,
          sample: (e.sample || '').slice(0, 80),
        });
      });
    });

    // --- login: dev-default admin / empty password ---
    // Go straight to the static /login page. Navigating to /app/ unauthenticated triggers
    // the SPA's redirect storm (auth guard → /oauth2/authorize, plus an external-IdP tab),
    // which flakes goto with ERR_ABORTED/ERR_NETWORK_CHANGED. After login the SPA loads
    // authenticated and stable; the addInitScript listener still captures its load-time
    // violations (the runtime inline <style> we're gating on).
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.fill('input[name="username"]', 'admin');
    await page.fill('input[name="password"]', '');
    await page.click('button#btn');

    // B3 nag: an empty-password account is redirected to /change-password — skip it.
    await page.waitForURL(/\/(change-password|app\/)/);
    if (page.url().includes('/change-password')) {
      await page.click('button.skip');
    }
    await page.waitForURL(/\/app\//);
    await expect(page.locator('app-root')).toBeAttached();

    // --- walk the CDK overlays that exist today (extend as the SPA grows) ---
    // "Switch to user" mat-dialog + its "Target user" mat-select.
    const switchUser = page.getByRole('button', { name: 'admin' });
    if (await switchUser.count()) {
      await switchUser.first().click();
      const targetUser = page.getByRole('combobox', { name: 'Target user' });
      if (await targetUser.count()) {
        await targetUser.click();
        await page.keyboard.press('Escape'); // close the select overlay
      }
      await page.keyboard.press('Escape'); // close the dialog
    }

    // Report-only violations produced by the real walk (BEFORE the canary). The SPA policy
    // is the "soft shell" — connect-src/object-src/base-uri/frame-*/form-action (style-src,
    // img-src, font-src were dropped as no-code-execution noise). enforce-readiness = the SPA
    // never violates any of those during a real session.
    const walkViolations: Array<{ directive: string; blockedURI: string }> = await page.evaluate(() =>
      (window as any).__csp.filter((v: any) => v.disposition === 'report'),
    );

    // --- control canary: a directive STILL in the policy (object-src 'none') MUST violate ---
    // Proves the report-only header + the listener are actually live; without it a "0
    // violations" result could just mean we were measuring nothing. (Uses object-src, not
    // style-src — style-src is no longer in the policy.)
    const before = await page.evaluate(() => (window as any).__csp.length);
    await page.evaluate(() => {
      const o = document.createElement('object');
      o.data = 'data:application/x-test,';
      document.body.appendChild(o);
    });
    await page.waitForTimeout(300);
    const after = await page.evaluate(() => (window as any).__csp.length);

    expect(
      after,
      'control canary (object-src) did NOT fire — the report-only CSP header or the securitypolicyviolation listener is missing; this gate would be measuring blind',
    ).toBeGreaterThan(before);

    expect(
      walkViolations,
      `SPA produced report-only CSP violations — flipping the soft-shell policy to enforce would BREAK these:\n${JSON.stringify(walkViolations, null, 2)}`,
    ).toEqual([]);
  });
});
