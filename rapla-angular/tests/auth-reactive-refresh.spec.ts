import { test, expect } from '@playwright/test';

/**
 * Tier-7 proof of the PRD 072 reactive-401-refresh: when the short-lived access_token
 * cookie (Max-Age 1h) has expired but the refresh_token (21d, Path=/api/auth/refresh) is
 * still valid, the SPA's first /api/auth/me 401s, the interceptor refreshes, and the
 * replay succeeds — so the 401 in the console on a cold load is harmless-by-design, not a
 * cross-origin cookie bug. We simulate "1h later" by deleting just the access_token cookie.
 */
test('expired access_token → /me 401 → refresh 200 → /me 200 (reactive refresh)', async ({ page, context }) => {
  const seq: Array<{ method: string; path: string; status: number }> = [];
  page.on('response', (r) => {
    const p = new URL(r.url()).pathname;
    if (p === '/api/auth/me' || p === '/api/auth/refresh' || p === '/api/graphql') {
      seq.push({ method: r.request().method(), path: p, status: r.status() });
    }
  });

  // --- login admin/empty → B3 nag skip → authenticated SPA ---
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', '');
  await page.click('button#btn');
  await page.waitForURL(/\/(change-password|app\/)/);
  if (page.url().includes('/change-password')) await page.click('button.skip');
  await page.waitForURL(/\/app\//);
  await expect(page.locator('app-root')).toBeAttached();

  // reproduce the user's actual repro path: the GraphQL appointments view
  await page.goto('/app/appointments', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1000);

  // --- simulate the 1h expiry: the cookie's Max-Age == the JWT exp, so at expiry the
  // browser simply DROPS the access_token cookie (a malformed/forged value instead would
  // 401 the very /app navigation and isn't a realistic "expired"; a valid-signature-but-
  // expired JWT can't be forged without the server key). Deleting it is the faithful cold-start. ---
  const namesBefore = (await context.cookies()).map((c) => c.name);
  expect(namesBefore, 'access_token cookie present after login').toContain('access_token');
  await context.clearCookies({ name: 'access_token' });
  const namesAfter = (await context.cookies()).map((c) => c.name);
  expect(namesAfter, 'access_token cleared (= browser dropped it at Max-Age)').not.toContain('access_token');
  expect(namesAfter, 'refresh_token still present').toContain('refresh_token');

  // --- cold load with no access_token: capture the reactive sequence ---
  seq.length = 0;
  await page.reload({ waitUntil: 'domcontentloaded' });

  // Diagnostic: poll for any USER-VISIBLE hint (snackbar/alert/banner/toast) that the
  // recovered 401 might surface during the brief refresh window — that's the real bug.
  const hints: string[] = [];
  for (let i = 0; i < 20; i++) {
    const found: string[] = await page.evaluate(() => {
      const out: string[] = [];
      document
        .querySelectorAll(
          'mat-snack-bar-container,[role="alert"],.mat-mdc-snack-bar-container,[class*="snack"],[class*="error"],[class*="toast"],[class*="notif"],[class*="banner"]',
        )
        .forEach((el) => {
          const t = (el.textContent || '').trim();
          if (t) out.push(t.slice(0, 140));
        });
      return out;
    });
    for (const x of found) if (!hints.includes(x)) hints.push(x);
    await page.waitForTimeout(150);
  }
  console.log('[visible hints during recovery]', JSON.stringify(hints));

  console.log('[auth seq]', JSON.stringify(seq));
  const firstMe = seq.find((c) => c.path === '/api/auth/me');
  const refresh = seq.find((c) => c.path === '/api/auth/refresh');
  const okMe = seq.find((c) => c.path === '/api/auth/me' && c.status === 200);

  expect(firstMe?.status, 'first /api/auth/me 401s because the access_token expired').toBe(401);
  expect(refresh?.status, 'interceptor reactively refreshes → 200 (refresh_token still valid)').toBe(200);
  expect(okMe, 'replayed /api/auth/me succeeds with the fresh cookie').toBeTruthy();
});
