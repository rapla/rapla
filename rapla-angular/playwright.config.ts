import { defineConfig, devices } from '@playwright/test';

// Tier-7 browser e2e (AGENTS.md §10). The rapla dev server must already be
// running (AGENTS.md §8) — no webServer auto-start here. The SPA is served by
// Spring Boot at /app/; baseURL points at the full stack on :8051.
//
// channel: 'chrome' uses the system-installed Google Chrome. Playwright's
// bundled Chromium has no build for Ubuntu 26.04 (see PRD 033 /
// docs/development.md § "Playwright MCP — install"), so do NOT rely on
// `npx playwright install`.
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL: process.env.RAPLA_BASE_URL ?? 'http://localhost:8051',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    },
  ],
});
