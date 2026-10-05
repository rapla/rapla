import { describe, expect, it } from 'vitest';

import {
  CHIP_TEXT_COLOR,
  chipColor,
  chipName,
  chipTime,
  isAnonymousRow,
  isDraggableRow,
  isMovableRow,
  paleRows,
} from './block-style';
import type { FilterEntry } from '../state/filter-store';

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
      isMovableRow({
        ...movable,
        reservation: { id: 'e1', canModify: false, appointmentCount: 1 },
      }),
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

describe('paleRows — PRD 128 D1 focus', () => {
  const block = (app: string, start: string, end: string) => ({
    appointment: { id: app },
    start: `2026-10-05T${start}:00`,
    end: `2026-10-05T${end}:00`,
  });
  const a1Hit = block('a1', '10:00', '12:00');
  const a1Miss = block('a1', '14:00', '15:00');
  const a2 = block('a2', '11:00', '13:00');
  const other = block('a9', '11:00', '12:00');
  const rows = [a1Hit, a1Miss, a2, other];
  const conflict: FilterEntry = { id: 'CONFLICT;r1;a1;a2', kind: 'conflict', label: 'K' };

  it('without a focus chip nothing is pale', () => {
    expect(paleRows(rows, [{ id: 'r1', kind: 'resource', label: 'R' }]).size).toBe(0);
  });

  it('a conflict keeps only the overlapping blocks of both sides normal (Swing overlapsBlock)', () => {
    expect([...paleRows(rows, [conflict])]).toEqual([a1Miss, other]);
  });

  it('an anonymous block of an unreadable other side counts by its flat appointmentId (D6, Phase 1b)', () => {
    const masked = {
      appointment: null,
      appointmentId: 'a2',
      start: '2026-10-05T11:00:00',
      end: '2026-10-05T13:00:00',
    };
    expect([...paleRows([a1Hit, a1Miss, masked, other], [conflict])]).toEqual([a1Miss, other]);
  });

  it('a request keeps the blocks of its reservation normal (reservation id from the chip id)', () => {
    const own = { ...block('a5', '09:00', '10:00'), reservation: { id: 'v1' } };
    const anonymous = { ...block('a6', '09:00', '10:00'), reservation: null, reservationId: 'v1' };
    const request: FilterEntry = { id: 'REQUEST;r1;v1', kind: 'request', label: 'A' };
    expect([...paleRows([own, anonymous, ...rows], [request])]).toEqual(rows);
  });
});

describe('anonymous blocks — PRD 128 Phase 1b', () => {
  const anonymous = {
    name: 'nicht sichtbar',
    reservation: null,
    reservationId: 'v1',
    appointment: null,
    appointmentId: 'a1',
  };

  it('are recognised by their explicit null reservation, and are neither movable nor draggable', () => {
    expect(isAnonymousRow(anonymous)).toBe(true);
    expect(isAnonymousRow({ name: 'X', reservation: { id: 'v1' } })).toBe(false);
    expect(isAnonymousRow({ name: 'X' })).toBe(false);
    expect(isMovableRow(anonymous)).toBe(false);
    expect(isDraggableRow(anonymous)).toBe(false);
  });
});
