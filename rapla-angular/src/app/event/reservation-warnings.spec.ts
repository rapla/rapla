import { describe, expect, it } from 'vitest';

import { isBlocked, saveGate, warningText, type ReservationWarning } from './reservation-warnings';

const w = (
  code: ReservationWarning['code'],
  severity: ReservationWarning['severity'],
  ...args: string[]
): ReservationWarning => ({ code, severity, args });

/**
 * PRD 105 Phase 3 — the SPA's share of the check flow: text and gating. The rules themselves are
 * server-side; a re-implementation here would be exactly the duplication PRD 023 Phase 10 removed.
 */
describe('reservation warnings (PRD 105)', () => {
  it('no findings → save without asking', () => {
    expect(saveGate([])).toBe('save');
  });

  it('confirmable findings → ask, never refuse', () => {
    expect(saveGate([w('CONFLICT', 'CONFIRMABLE'), w('NOT_IN_CALENDAR', 'CONFIRMABLE', 'X')])).toBe(
      'confirm',
    );
  });

  /** Severity comes from the server; the client must not soften a blocking finding into a prompt. */
  it('one blocking finding blocks, whatever else is in the list', () => {
    const findings = [w('CONFLICT', 'CONFIRMABLE'), w('NO_RESERVATION_NAME', 'BLOCKING')];
    expect(isBlocked(findings)).toBe(true);
    expect(saveGate(findings)).toBe('blocked');
  });

  it('renders German text with the server-supplied arguments', () => {
    expect(warningText(w('NO_ALLOCATABLES_SELECTED', 'CONFIRMABLE'))).toContain('keine Ressourcen');
    expect(warningText(w('REQUEST_PENDING', 'CONFIRMABLE', 'Raum A66'))).toContain('Raum A66');
    expect(warningText(w('NOT_IN_CALENDAR', 'CONFIRMABLE', 'Physik'))).toContain('Physik');
  });

  /** The server sends "" for a nameless draft — `??` would print „" and read like a bug. */
  it('an empty argument falls back to a readable phrase', () => {
    expect(warningText(w('NOT_IN_CALENDAR', 'CONFIRMABLE', ''))).toBe(
      'Die Veranstaltung erscheint nicht in der aktuellen Ansicht.',
    );
    expect(warningText(w('REQUEST_PENDING', 'CONFIRMABLE', ''))).toContain('Eine Ressource');
  });

  it('an unknown code degrades to the code itself instead of an empty dialog', () => {
    expect(warningText({ code: 'FUTURE_CODE' as never, severity: 'CONFIRMABLE', args: [] })).toBe(
      'FUTURE_CODE',
    );
  });
});
