import { readFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { test, type Page } from '@playwright/test';

// PRD 118 U3 — Seminarhaus scenes. Server: docs/demo/seminarhaus/demo-seminarhaus.xml,
// -Duser.language=de. Output: docs/demo/seminarhaus/NN-<scene>.png (locale de).

const DEMO_DIR = join(process.cwd(), '..', 'docs', 'demo', 'seminarhaus');
const XML = readFileSync(join(DEMO_DIR, 'demo-seminarhaus.xml'), 'utf-8');
const TODAY = new Date('2026-09-21T10:00:00+02:00');
const WEEK = { from: '2026-09-21T00:00:00', to: '2026-09-28T00:00:00' };
const SEPTEMBER = { from: '2026-09-01T00:00:00', to: '2026-10-01T00:00:00' };
const PROGRAMME = { from: '2026-09-01T00:00:00', to: '2027-03-01T00:00:00' };
const SEMINAR_ROOMS = ['Garden room', 'Library', 'Hall', 'Studio'];
const GUEST_ROOMS = ['North wing', 'South wing', 'Cottage'];
const TRAINERS = ['Lena', 'Marco', 'Alice', 'Tom'];

function userId(username: string): string {
  return XML.match(new RegExp(`<rapla:user id="([^"]+)"[^>]*username="${username}"`))![1];
}

function resourceId(name: string): string {
  const resource = XML.match(
    new RegExp(
      `<rapla:resource id="([^"]+)"[^>]*>\\s*<dynatt:\\w+>\\s*<dynatt:name>${name}</dynatt:name>`,
    ),
  );
  if (resource) return resource[1];
  return XML.match(
    new RegExp(
      `<rapla:person id="([^"]+)"[^>]*>\\s*<dynatt:trainer>\\s*<dynatt:surname>[^<]*</dynatt:surname>\\s*<dynatt:firstname>${name}</dynatt:firstname>`,
    ),
  )![1];
}

function reservationId(name: string): string {
  return XML.match(
    new RegExp(
      `<rapla:reservation id="([^"]+)"[^>]*>\\s*<dynatt:seminar>\\s*<dynatt:name>${name}</dynatt:name>`,
    ),
  )![1];
}

interface Seed {
  resources?: string[];
  mode?: 'week' | 'month' | 'table' | 'grouped';
  window?: { from: string; to: string };
}

async function open(page: Page, user: string, path: string, seed: Seed = {}): Promise<void> {
  const suffix = `::u=${userId(user)}`;
  const scope = (seed.resources ?? []).map((name) => ({
    id: resourceId(name),
    kind: 'resource',
    label: name,
  }));
  await page.clock.setFixedTime(TODAY);
  await page.addInitScript(
    ([suffix, scope, mode, window]) => {
      localStorage.setItem(`rapla.scope${suffix}`, JSON.stringify(scope));
      if (mode) localStorage.setItem(`rapla.renderMode${suffix}`, JSON.stringify(mode));
      if (window) localStorage.setItem(`rapla.window${suffix}`, JSON.stringify(window));
    },
    [suffix, scope, seed.mode, seed.window] as const,
  );
  await page.goto('/login');
  await page.locator('#u').fill(user);
  await page.locator('#p').fill('demo');
  await page.locator('#btn').click();
  await page.waitForURL(/\/(change-password|app\/)/);
  if (page.url().includes('/change-password')) await page.click('button.skip');
  await page.goto(`/app/${path}`);
  await page.waitForLoadState('networkidle');
}

async function shot(page: Page, file: string): Promise<void> {
  mkdirSync(DEMO_DIR, { recursive: true });
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(DEMO_DIR, file) });
}

const VIEW = 'views/rapla_appointments';

test.describe('demo seminarhaus', () => {
  test('01 month grid coloured by event kind', async ({ page }) => {
    await open(page, 'manager', VIEW, {
      resources: SEMINAR_ROOMS,
      mode: 'month',
      window: SEPTEMBER,
    });
    await shot(page, '01-month-grid.png');
  });

  test('02 seminar with its category', async ({ page }) => {
    await open(page, 'manager', `event/${reservationId('Yoga weekend retreat')}`);
    await shot(page, '02-event-category.png');
  });

  test('03 kitchen sees catering only', async ({ page }) => {
    await open(page, 'kitchen', VIEW, { resources: ['Dining hall'], mode: 'week', window: WEEK });
    await shot(page, '03-kitchen-view.png');
  });

  test('04 declarative view: arrivals this week', async ({ page }) => {
    await open(page, 'manager', 'views/ArrivalsThisWeek', {
      resources: GUEST_ROOMS,
      mode: 'grouped',
      window: WEEK,
    });
    await shot(page, '04-arrivals-view.png');
  });

  test("05 trainer's own calendar", async ({ page }) => {
    await open(page, 'lena', VIEW, { resources: ['Lena'], mode: 'month', window: SEPTEMBER });
    await shot(page, '05-trainer-calendar.png');
  });

  test('06 programme grouped by month', async ({ page }) => {
    await open(page, 'manager', 'views/Programme', {
      resources: TRAINERS,
      mode: 'grouped',
      window: PROGRAMME,
    });
    await shot(page, '06-programme.png');
  });
});
