import { describe, expect, it } from 'vitest';

import { CHIP_TEXT_COLOR, chipColor, chipName, chipTime, isMovableRow } from './block-style';

describe('block-style (PRD 100 Phase 1)', () => {
  it('chipColor returns the §12-gated color string', () => {
    expect(chipColor({ color: '#ff0000' })).toBe('#ff0000');
  });

  it('chipColor: null / empty / non-string → null (neutral chip)', () => {
    expect(chipColor({ color: null })).toBeNull();
    expect(chipColor({ color: '' })).toBeNull();
    expect(chipColor({ color: 42 })).toBeNull();
    expect(chipColor({})).toBeNull();
  });

  it('chipTime prefers the server-formatted times field', () => {
    expect(chipTime({ times: '10:00 - 11:30', start: '2026-06-13T10:00:00' })).toBe(
      '10:00 - 11:30',
    );
  });

  it('chipTime falls back to the start HH:mm', () => {
    expect(chipTime({ start: '2026-06-13T10:15:00' })).toBe('10:15');
    expect(chipTime({})).toBe('');
  });

  it('chipName coerces the name field', () => {
    expect(chipName({ name: 'Physik' })).toBe('Physik');
    expect(chipName({})).toBe('');
  });

  it('chip text is ALWAYS black (Swing SwingRaplaBlock.FOREGROUND_COLOR parity, D1)', () => {
    expect(CHIP_TEXT_COLOR).toBe('#000');
  });
});

describe('isMovableRow — drag gate (PRD 095 D6, fail-closed)', () => {
  const movable = {
    reservation: { id: 'e1', canModify: true, appointmentCount: 1 },
    appointment: { id: 'a1', repeating: null },
  };

  it('movable: canModify + single appointment + non-repeating', () => {
    expect(isMovableRow(movable)).toBe(true);
  });

  it('not movable: repeating appointment', () => {
    expect(
      isMovableRow({ ...movable, appointment: { id: 'a1', repeating: { type: 'WEEKLY' } } }),
    ).toBe(false);
  });

  it('not movable: multi-appointment reservation (moveReservations shifts ALL)', () => {
    expect(
      isMovableRow({ ...movable, reservation: { id: 'e1', canModify: true, appointmentCount: 2 } }),
    ).toBe(false);
  });

  it('not movable: no modify permission', () => {
    expect(
      isMovableRow({ ...movable, reservation: { id: 'e1', canModify: false, appointmentCount: 1 } }),
    ).toBe(false);
  });

  it('fail-closed: custom views omitting the hidden fields yield not-movable', () => {
    expect(isMovableRow({})).toBe(false);
    expect(isMovableRow({ reservation: { id: 'e1', canModify: true } })).toBe(false);
    expect(isMovableRow({ ...movable, appointment: undefined })).toBe(false);
    // appointment selected WITHOUT the repeating field → unknown → not movable
    expect(isMovableRow({ ...movable, appointment: { id: 'a1' } })).toBe(false);
  });
});
