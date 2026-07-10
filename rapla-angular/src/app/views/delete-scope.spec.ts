import { describe, expect, it } from 'vitest';

import { applyDeleteScope, deleteScopeOptions, type BlockRef } from './delete-scope';
import type { EventDraft } from '../event/event-draft';

function draft(partial: Partial<EventDraft>): EventDraft {
  return {
    id: 'e1',
    persisted: true,
    typeKey: 'event',
    values: { name: 'Physik' },
    appointments: [],
    allocations: [],
    lastChanged: '2026-07-01T10:00:00',
    ...partial,
  };
}

const WEEKLY = {
  type: 'WEEKLY' as const,
  interval: 1,
  end: null,
  count: null,
  weekdays: [1],
  exceptions: [],
};

const single = {
  id: 'a1',
  start: '2026-07-08T08:00:00',
  end: '2026-07-08T16:00:00',
  allDay: false,
  repeating: null,
};
const repeating = {
  id: 'a2',
  start: '2026-07-09T10:00:00',
  end: '2026-07-09T11:00:00',
  allDay: false,
  repeating: WEEKLY,
};

const block = (
  appointmentId: string | null,
  start: string | null = '2026-07-16T10:00:00',
): BlockRef => ({
  appointmentId,
  start,
  isException: false,
});

describe('deleteScopeOptions (Swing showDialog predicates)', () => {
  it('single non-repeating appointment in a 1-appointment event → only whole event', () => {
    const d = draft({ appointments: [single] });
    const opts = deleteScopeOptions(d, block('a1'));
    expect(opts.map((o) => o.scope)).toEqual(['event']);
  });

  it('repeating appointment in a 1-appointment event → event + single (no serie)', () => {
    const d = draft({ appointments: [repeating] });
    const opts = deleteScopeOptions(d, block('a2'));
    expect(opts.map((o) => o.scope)).toEqual(['event', 'single']);
  });

  it('repeating appointment in a multi-appointment event → event + serie + single', () => {
    const d = draft({ appointments: [single, repeating] });
    const opts = deleteScopeOptions(d, block('a2'));
    expect(opts.map((o) => o.scope)).toEqual(['event', 'serie', 'single']);
  });

  it('non-repeating appointment in a multi-appointment event → event + single', () => {
    const d = draft({ appointments: [single, repeating] });
    const opts = deleteScopeOptions(d, block('a1'));
    expect(opts.map((o) => o.scope)).toEqual(['event', 'single']);
  });

  it('no block info (reservations-root view) → only whole event', () => {
    const d = draft({ appointments: [single, repeating] });
    const opts = deleteScopeOptions(d, { appointmentId: null, start: null, isException: false });
    expect(opts.map((o) => o.scope)).toEqual(['event']);
  });

  it('single-option label carries the block date', () => {
    const d = draft({ appointments: [single, repeating] });
    const opts = deleteScopeOptions(d, block('a2', '2026-07-16T10:00:00'));
    expect(opts.find((o) => o.scope === 'single')?.label).toContain('16.07.2026');
  });
});

describe('applyDeleteScope (Swing data-model effects)', () => {
  it('event → delete the whole reservation', () => {
    const d = draft({ appointments: [single] });
    expect(applyDeleteScope(d, 'event', block('a1'))).toEqual({ kind: 'deleteEvent' });
  });

  it('serie → removes the appointment from the draft', () => {
    const d = draft({ appointments: [single, repeating] });
    const action = applyDeleteScope(d, 'serie', block('a2'));
    if (action.kind !== 'update') throw new Error('expected update');
    expect(action.draft.appointments.map((a) => a.id)).toEqual(['a1']);
  });

  it('serie on the LAST appointment → whole-event delete (last-appointment cascade)', () => {
    const d = draft({ appointments: [repeating] });
    expect(applyDeleteScope(d, 'serie', block('a2'))).toEqual({ kind: 'deleteEvent' });
  });

  it('single on a repeating appointment → adds a DAY-TRUNCATED exception', () => {
    const d = draft({ appointments: [single, repeating] });
    const action = applyDeleteScope(d, 'single', block('a2', '2026-07-16T10:00:00'));
    if (action.kind !== 'update') throw new Error('expected update');
    const a2 = action.draft.appointments.find((a) => a.id === 'a2');
    expect(a2?.repeating?.exceptions).toContain('2026-07-16T00:00:00');
  });

  it('single on a non-repeating appointment in a multi-appointment event → removes the appointment', () => {
    const d = draft({ appointments: [single, repeating] });
    const action = applyDeleteScope(d, 'single', block('a1'));
    if (action.kind !== 'update') throw new Error('expected update');
    expect(action.draft.appointments.map((a) => a.id)).toEqual(['a2']);
  });

  it('removing an appointment strips it from restriction maps; empty restriction drops the allocation', () => {
    const d = draft({
      appointments: [single, repeating],
      allocations: [
        { allocatableId: 'r1', allocatableName: 'C452', appointmentIds: ['a1', 'a2'] },
        { allocatableId: 'r2', allocatableName: 'Kamera', appointmentIds: ['a1'] },
        { allocatableId: 'r3', allocatableName: 'Beamer', appointmentIds: null },
      ],
    });
    const action = applyDeleteScope(d, 'single', block('a1'));
    if (action.kind !== 'update') throw new Error('expected update');
    expect(action.draft.allocations).toEqual([
      { allocatableId: 'r1', allocatableName: 'C452', appointmentIds: ['a2'] },
      { allocatableId: 'r3', allocatableName: 'Beamer', appointmentIds: null },
    ]);
  });

  it('does not mutate the input draft (deep copy)', () => {
    const d = draft({ appointments: [single, repeating] });
    applyDeleteScope(d, 'single', block('a2'));
    expect(d.appointments.find((a) => a.id === 'a2')?.repeating?.exceptions).toEqual([]);
  });

  it('exception dates are not duplicated', () => {
    const withException = {
      ...repeating,
      repeating: { ...WEEKLY, exceptions: ['2026-07-16T00:00:00'] },
    };
    const d = draft({ appointments: [single, withException] });
    const action = applyDeleteScope(d, 'single', block('a2', '2026-07-16T10:00:00'));
    if (action.kind !== 'update') throw new Error('expected update');
    const a2 = action.draft.appointments.find((a) => a.id === 'a2');
    expect(a2?.repeating?.exceptions).toEqual(['2026-07-16T00:00:00']);
  });
});
