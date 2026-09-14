import { readFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { test, type Page } from '@playwright/test';

// PRD 118 U1 — Hochschule scenes. Server: docs/demo/hochschule/demo-hochschule.xml,
// -Duser.language=de. Output: docs/demo/hochschule/NN-<scene>.png (locale de).

const DEMO_DIR = join(process.cwd(), '..', 'docs', 'demo', 'hochschule');
const XML = readFileSync(join(DEMO_DIR, 'demo-hochschule.xml'), 'utf-8');
const TODAY = new Date('2026-09-16T10:00:00+02:00');
const WEEK = { from: '2026-09-14T00:00:00', to: '2026-09-21T00:00:00' };
const OCTOBER = { from: '2026-10-01T00:00:00', to: '2026-11-01T00:00:00' };

function userId(username: string): string {
  return XML.match(new RegExp(`<rapla:user id="([^"]+)"[^>]*username="${username}"`))![1];
}

function resourceId(name: string): string {
  return XML.match(
    new RegExp(`<rapla:resource id="([^"]+)"[^>]*>\\s*<dynatt:\\w+>\\s*<dynatt:name>${name}</dynatt:name>`),
  )![1];
}

function reservationId(name: string, allocatedName?: string): string {
  const re = new RegExp(
    `<rapla:reservation id="([^"]+)"[^>]*>(?:\\s*<rapla:annotation[^<]*</rapla:annotation>)?\\s*<dynatt:\\w+>\\s*<dynatt:name>${name}</dynatt:name>[\\s\\S]*?</rapla:reservation>`,
    'g',
  );
  const allocated = allocatedName ? resourceId(allocatedName) : undefined;
  for (const m of XML.matchAll(re)) {
    if (!allocated || m[0].includes(`idref="${allocated}"`)) return m[1];
  }
  throw new Error(`reservation ${name} not found`);
}

interface Seed {
  resources?: string[];
  mode?: 'week' | 'month';
  window?: { from: string; to: string };
}

async function open(page: Page, user: string, path: string, seed: Seed = {}): Promise<void> {
  const suffix = `::u=${userId(user)}`;
  const scope = (seed.resources ?? []).map((name) => ({ id: resourceId(name), kind: 'resource', label: name }));
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
  await page.locator('#p').fill(user);
  await page.locator('#btn').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
  await page.goto(`/app/${path}`);
  await page.waitForLoadState('networkidle');
}

async function shot(page: Page, file: string): Promise<void> {
  mkdirSync(DEMO_DIR, { recursive: true });
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(DEMO_DIR, file) });
}

const VIEW = 'views/rapla_appointments';

test.describe('demo hochschule', () => {
  test('01 week view of a course', async ({ page }) => {
    await open(page, 'planner', VIEW, { resources: ['CS-25'], mode: 'week', window: WEEK });
    await shot(page, '01-week-course.png');
  });

  test('02 room with a double booking', async ({ page }) => {
    // ponytail: the event sheet shows no availability pills for a persisted event (residue, § U1);
    // until that is fixed the scene shows the double booking as overlapping blocks in the room's week.
    await open(page, 'planner', VIEW, { resources: ['Seminar room B202'], mode: 'week', window: WEEK });
    await shot(page, '02-week-room-conflict.png');
  });

  test('03 repeating lecture with exceptions', async ({ page }) => {
    await open(page, 'planner', `event/${reservationId('Software Engineering', 'CS-25')}`);
    await page.locator('.sec-title', { hasText: 'Termine' }).click();
    await page.locator('button[title="Wiederholung"]').first().click();
    await page.waitForTimeout(1500);
    await shot(page, '03-event-repeating.png');
  });

  test('04 template picker', async ({ page }) => {
    await open(page, 'planner', VIEW, { resources: ['CS-25'], mode: 'week', window: WEEK });
    await page.locator('button.new-event').click();
    await page.waitForTimeout(500);
    await shot(page, '04-template-picker.png');
  });

  test('05 month view', async ({ page }) => {
    await open(page, 'planner', VIEW, { resources: ['CS-25'], mode: 'month', window: OCTOBER });
    await shot(page, '05-month.png');
  });

  test('06 student read-only on a phone', async ({ page }) => {
    await page.setViewportSize({ width: 400, height: 860 });
    await open(page, 'student', `event/${reservationId('Software Engineering', 'CS-25')}`);
    await shot(page, '06-student-readonly.png');
  });

  test('07 published calendar (nearest SPA state; export dialog is Swing)', async ({ page }) => {
    await open(page, 'planner', VIEW, { resources: ['Lecture hall A001'], mode: 'week', window: WEEK });
    await shot(page, '07-ical-export.png');
  });

  test('09 equipment loans', async ({ page }) => {
    const equipment = [
      'Mobile beamer 1', 'Mobile beamer 2', 'Mobile beamer 3', 'Camera 1', 'Camera 2', 'Laptop trolley',
      'Bicycle 1', 'Bicycle 2', 'Bicycle 3', 'Bicycle 4', 'Bicycle 5', 'Bicycle 6',
    ];
    await open(page, 'planner', VIEW, { resources: equipment, mode: 'week', window: WEEK });
    await shot(page, '09-equipment-loans.png');
  });

  test('10 pending room request', async ({ page }) => {
    await open(page, 'planner', `event/${reservationId('Software Engineering: exam Q&amp;A')}`);
    await shot(page, '10-requests.png');
  });
});
