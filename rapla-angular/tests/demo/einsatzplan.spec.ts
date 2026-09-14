import { test, expect, type Page } from '@playwright/test';
import { resolve } from 'node:path';

// PRD 118 U4 — hospital ward shift roster. Server must serve
// docs/demo/einsatzplan/demo-einsatzplan.xml with -Duser.language=de.
// Dialogs opened by drags are cancelled, so the data stays unchanged.
const OUT = resolve(process.cwd(), '../docs/demo/einsatzplan');
const TEAMS_A_B = ['Alex Turner', 'Jordan Baker', 'Morgan Fischer', 'Casey Novak', 'Riley Brooks', 'Taylor Weber'];
const WARDS = ['Ward 3 – Internal medicine', 'Ward 5 – Surgery'];

test.use({ viewport: { width: 1440, height: 900 }, locale: 'de-DE', timezoneId: 'Europe/Berlin' });
test.describe.configure({ mode: 'serial' });

async function login(page: Page, user: string) {
  // Fixed demo "now" (Mon of week 38) so the today marker and now-line don't move with the real date.
  await page.clock.setFixedTime(new Date('2026-09-14T10:00:00+02:00'));
  await page.goto('/login');
  await page.fill('#u', user);
  await page.fill('#p', 'demo');
  await Promise.all([page.waitForURL('**/app/**'), page.click('#btn')]);
  await expect(page.locator('input.obsearch')).toBeVisible();
  // Recents are stored server-side per user; clear them so earlier runs don't show up.
  const clear = page.locator('.recents-hdr .clr');
  if (await clear.waitFor({ timeout: 3000 }).then(() => true, () => false)) {
    await clear.click();
    await expect(clear).toBeHidden();
  }
}

async function addChips(page: Page, names: string[]) {
  const search = page.locator('input.obsearch');
  for (const name of names) {
    await search.fill(name);
    await page.locator('.omnibox .row', { hasText: name }).locator('button.act[data-action="filter-add"]').click();
    // Recents are ordered by a coarse server timestamp; space the adds so the list order is stable.
    await page.waitForTimeout(1100);
  }
  await search.fill('');
  await page.keyboard.press('Escape');
}

async function showWindow(page: Page, from: string, to: string, mode: 'Woche' | 'Tag' | 'Tabelle') {
  await page.getByRole('button', { name: 'Tabelle', exact: true }).click();
  for (const [label, value] of [['Bis', to], ['Von', from], ['Bis', to]]) {
    const box = page.getByRole('textbox', { name: label });
    await box.fill(value);
    await box.press('Tab');
    await page.waitForTimeout(300);
  }
  await page.getByRole('button', { name: mode, exact: true }).click();
  if (mode !== 'Tabelle') {
    await page.getByRole('combobox', { name: 'Zeitraster' }).selectOption('1h');
    await expect(page.locator('.nav .range')).toHaveText(mode === 'Woche' ? `${from} … ${to}` : new RegExp(`^${from}`));
  }
  await expect(page.locator('.result-info')).toHaveText(/\d+ Termine/);
  await page.waitForTimeout(800);
}

async function drag(page: Page, chip: ReturnType<Page['locator']>, dx: number, dy: number) {
  const box = (await chip.boundingBox())!;
  const x = box.x + box.width / 2;
  const y = box.y + 12;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx / 2, y + dy / 2, { steps: 5 });
  await page.mouse.move(x + dx, y + dy, { steps: 5 });
  await page.mouse.up();
}

async function dayWidth(page: Page) {
  const a = (await page.getByText('16', { exact: true }).boundingBox())!;
  const b = (await page.getByText('17', { exact: true }).boundingBox())!;
  return b.x - a.x;
}

// The training block is 90 minutes long (08:00–09:30).
async function hourHeight(chip: ReturnType<Page['locator']>) {
  return (await chip.boundingBox())!.height / 1.5;
}

async function shot(page: Page, name: string, parkMouse = true) {
  if (parkMouse) await page.mouse.move(1435, 895);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}/${name}.png`, animations: 'disabled', caret: 'hide' });
}

async function personWeek(page: Page) {
  await login(page, 'planner');
  await addChips(page, TEAMS_A_B);
  await showWindow(page, '2026-09-14', '2026-09-21', 'Woche');
}

test('01 week by person', async ({ page }) => {
  await personWeek(page);
  await shot(page, '01-week-by-person');
});

test('02 week by ward', async ({ page }) => {
  await login(page, 'planner');
  await addChips(page, WARDS);
  await showWindow(page, '2026-09-14', '2026-09-21', 'Woche');
  await shot(page, '02-week-by-ward');
});

test('03 drag-move review', async ({ page }) => {
  await personWeek(page);
  const chip = page.locator('.chip', { hasText: 'Team A – Morning' }).first();
  await drag(page, chip, await dayWidth(page), 0);
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.waitForTimeout(400);
  await shot(page, '03-drag-move-review', false);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
});

test('04 conflict', async ({ page }) => {
  await personWeek(page);
  const chip = page.locator('.chip', { hasText: 'Hygiene training' });
  // The drop snaps the start DOWN to the raster, so drag a bit past one hour (08:00 → 09:00).
  await drag(page, chip, 0, (await hourHeight(chip)) * 1.25);
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('Vor dem Speichern prüfen');
  await page.waitForTimeout(400);
  await shot(page, '04-conflict', false);
  await dialog.getByRole('button', { name: 'Abbrechen' }).click();
});

test('05 table', async ({ page }) => {
  await login(page, 'planner');
  await addChips(page, TEAMS_A_B);
  await showWindow(page, '2026-09-14', '2026-09-21', 'Tabelle');
  await shot(page, '05-table-export');
});

test('06 station screen', async ({ page }) => {
  await login(page, 'wardscreen');
  await addChips(page, [WARDS[0]]);
  await showWindow(page, '2026-09-16', '2026-09-17', 'Tag');
  await shot(page, '06-station-screen');
});
