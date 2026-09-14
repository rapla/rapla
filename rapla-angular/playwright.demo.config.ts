import { defineConfig, devices } from '@playwright/test';

// PRD 118 demo screenshot scripts (tests/demo/). Run explicitly with
// `RAPLA_BASE_URL=http://localhost:8061 npm run demo:shots -- hochschule` against a
// server started with the matching docs/demo/<usecase>/demo-<usecase>.xml and
// -Duser.language=de. Excluded from `npm run e2e` via testIgnore in playwright.config.ts.
export default defineConfig({
  testDir: './tests/demo',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    channel: 'chrome',
    baseURL: process.env.RAPLA_BASE_URL ?? 'http://localhost:8061',
    viewport: { width: 1440, height: 900 },
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
  },
});
