import { createHash } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

import { expect, test, type Page } from '@playwright/test';

const OUT = resolve(__dirname, '../../../docs/demo/ausleihe');
const TODAY = new Date('2026-09-14T10:00:00+02:00');

const SCENES = {
  resources: '01-resource-table-camera',
  availability: '02-availability-four-weeks',
  picker: '03-new-loan-template-picker',
  form: '04-loan-form',
  conflict: '05-conflict-dialog',
  myLoans: '06-my-loans',
};

function uuid5(name: string): string {
  const ns = Buffer.from('6f1c2c9e000040008000000000000118', 'hex');
  const h = createHash('sha1').update(Buffer.concat([ns, Buffer.from(name)])).digest();
  h[6] = (h[6] & 0x0f) | 0x50;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.subarray(0, 16).toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

const entityId = (prefix: string, key: string) => prefix + uuid5(`${prefix}:${key}`).slice(1);
const loanId = (key: string) => uuid5(`:${key}`);

const ITEMS: Record<string, string> = {
  C1: 'Cinema camera C1',
  C2: 'Cinema camera C2',
  C3: 'Mirrorless camera M1',
  C4: 'Mirrorless camera M2',
  C5: 'Camcorder H1',
  C6: 'Action camera G1',
  V1: 'Media van V1',
};

test.use({ viewport: { width: 1440, height: 900 }, locale: 'de-DE', timezoneId: 'Europe/Berlin' });
test.describe.configure({ mode: 'serial' });

test.beforeAll(() => mkdirSync(OUT, { recursive: true }));

async function login(page: Page, username: string): Promise<void> {
  await page.clock.install({ time: TODAY });
  await page.clock.resume();
  await page.goto('/app/');
  await page.fill('input[name=username]', username);
  await page.fill('input[name=password]', 'demo');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/app\//);
}

async function seedView(
  page: Page,
  username: string,
  state: { scope: { id: string; kind: string; label: string }[]; mode: 'table' | 'month'; from?: string },
): Promise<void> {
  const user = entityId('u', username);
  await page.evaluate(
    ({ user, state }) => {
      localStorage.setItem(
        `rapla.window::u=${user}`,
        JSON.stringify({ from: `${state.from ?? '2026-09-01'}T00:00:00`, to: '2026-10-01T00:00:00' }),
      );
      localStorage.setItem(`rapla.scope::u=${user}`, JSON.stringify(state.scope));
      localStorage.setItem(`rapla.renderMode::u=${user}`, JSON.stringify(state.mode));
      localStorage.setItem(`rapla.lastView::u=${user}`, 'Loans');
    },
    { user, state },
  );
  await page.goto('/app/views/Loans');
}

const itemScope = Object.entries(ITEMS).map(([k, label]) => ({ id: entityId('r', `item-${k}`), kind: 'resource', label }));
const shot = (page: Page, scene: string) => page.screenshot({ path: `${OUT}/${scene}.png`, animations: 'disabled' });

test('01 resource search filtered to cameras', async ({ page }) => {
  await login(page, 'desk');
  await seedView(page, 'desk', { scope: [], mode: 'table' });
  await page.getByPlaceholder(/Suchen/).fill('camera');
  await expect(page.getByRole('button', { name: 'Belegung' }).first()).toBeVisible({ timeout: 15000 });
  await shot(page, SCENES.resources);
});

test('02 month grid of the loan items', async ({ page }) => {
  await login(page, 'desk');
  await seedView(page, 'desk', { scope: itemScope, mode: 'month' });
  await expect(page.getByText('11 Loans').first()).toBeVisible({ timeout: 15000 });
  await shot(page, SCENES.availability);
});

test('03 new loan from the template picker', async ({ page }) => {
  await login(page, 'desk');
  await seedView(page, 'desk', { scope: itemScope, mode: 'month' });
  await expect(page.getByText('11 Loans').first()).toBeVisible({ timeout: 15000 });
  await page.getByRole('button', { name: /Neu/ }).click();
  await expect(page.getByText('Weekend camera kit').last()).toBeVisible();
  await shot(page, SCENES.picker);
  await page.keyboard.press('Escape');
});

test('05 conflict dialog on saving a double-booked loan', async ({ page }) => {
  await login(page, 'desk');
  await seedView(page, 'desk', { scope: itemScope, mode: 'table' });
  await page.goto(`/app/event/${loanId('l13')}`);
  await expect(page.getByText(/belegt an/).first()).toBeVisible({ timeout: 15000 });
  await page.getByText('klicken zum Bearbeiten').click();
  await page.getByRole('textbox', { name: 'Titel' }).fill('Student film project (reshoot)');
  await page.getByRole('button', { name: 'Speichern' }).click();
  const warnings = page.getByRole('dialog').last();
  await expect(warnings).toBeVisible();
  await expect(warnings.getByText('Cinema camera C2')).toBeVisible();
  await shot(page, SCENES.conflict);
  await warnings.getByRole('button', { name: /Abbrechen|Zurück/ }).click();
});

test('04 generated loan form', async ({ page }) => {
  await login(page, 'desk');
  await page.goto(`/api/documents/LoanForm?reservationId=${loanId('l08')}`);
  await expect(page.getByRole('heading', { name: 'Loan form' })).toBeVisible();
  await shot(page, SCENES.form);
});

test("06 the borrower's own loans", async ({ page }) => {
  await login(page, 'alice');
  await seedView(page, 'alice', {
    scope: [{ id: entityId('u', 'alice'), kind: 'user', label: 'Alice Baker' }],
    mode: 'table',
  });
  await expect(page.getByText('Field trip Hamburg')).toBeVisible();
  await shot(page, SCENES.myLoans);
});
