import { describe, expect, it } from 'vitest';
import {
  fromReservation,
  generateAppointmentId,
  generateEventId,
  isDirty,
  newDraft,
  newScopedDraft,
  rangeScopedDraft,
  scopeAllocations,
  snapshot,
  toReservationInput,
  withEnd,
  withStart,
  type ReservationWire,
} from './event-draft';

const WIRE: ReservationWire = {
  id: 'e1111111-1111-4111-8111-111111111111',
  lastChanged: '2026-07-06T14:12:00',
  classification: {
    typeKey: 'event',
    name: 'Klausur Physik II',
    description: 'unbekanntes, nicht gerendertes Attribut',
    belongsto: 'c-department-1',
  },
  appointments: [
    {
      id: 'a1111111-1111-4111-8111-111111111111',
      start: '2026-07-07T12:00:00',
      end: '2026-07-07T13:45:00',
      allDay: false,
      repeating: {
        type: 'WEEKLY',
        interval: 1,
        end: '2026-09-30',
        count: null,
        weekdays: [1],
        exceptions: ['2026-07-15'],
      },
    },
  ],
  allocations: [
    { allocatable: { id: 'r-room-1', name: 'Raum A66' }, appointmentIds: null },
    { allocatable: { id: 'r-beamer-4', name: 'Beamer 04' }, appointmentIds: ['a1111111-1111-4111-8111-111111111111'] },
  ],
};

describe('EventDraft (PRD 091 Phase 2.2)', () => {
  it('mints rapla-typed ids (D3)', () => {
    expect(generateEventId()).toMatch(/^e[0-9a-f-]{35}$/);
    expect(generateAppointmentId()).toMatch(/^a[0-9a-f-]{35}$/);
    expect(generateEventId()).not.toBe(generateEventId());
  });

  it('newDraft carries one default appointment with ids from the start', () => {
    const d = newDraft('event', new Date('2026-07-06T10:20:00'));
    expect(d.persisted).toBe(false);
    expect(d.id).toMatch(/^e/);
    expect(d.appointments).toHaveLength(1);
    expect(d.appointments[0].id).toMatch(/^a/);
    expect(d.appointments[0].start).toBe('2026-07-06T11:00:00');
    expect(d.appointments[0].end).toBe('2026-07-06T12:00:00');
  });

  it('round-trip invariant (2.0b): load → no edit → input echoes EVERYTHING', () => {
    const draft = fromReservation(WIRE);
    const input = toReservationInput(draft);
    expect(input.typeKey).toBe('event');
    expect(input.classification).toEqual({
      event: {
        name: 'Klausur Physik II',
        description: 'unbekanntes, nicht gerendertes Attribut',
        belongsto: 'c-department-1',
      },
    });
    expect(input.appointments).toEqual([
      {
        id: 'a1111111-1111-4111-8111-111111111111',
        start: '2026-07-07T12:00:00',
        end: '2026-07-07T13:45:00',
        allDay: false,
        repeating: {
          type: 'WEEKLY',
          interval: 1,
          end: '2026-09-30',
          count: null,
          weekdays: [1],
          exceptions: ['2026-07-15'],
        },
      },
    ]);
    expect(input.allocations).toEqual([
      { allocatableId: 'r-room-1' },
      { allocatableId: 'r-beamer-4', appointmentIds: ['a1111111-1111-4111-8111-111111111111'] },
    ]);
  });

  it('strips GraphQL meta keys but keeps unknown attributes', () => {
    const draft = fromReservation({
      ...WIRE,
      classification: { typeKey: 'event', __typename: 'eventClassification', custom: 42 },
    } as unknown as ReservationWire);
    expect(draft.values).toEqual({ custom: 42 });
  });

  it('restriction null = all appointments (omitted in input)', () => {
    const draft = fromReservation(WIRE);
    expect(draft.allocations[0].appointmentIds).toBeNull();
    expect('appointmentIds' in toReservationInput(draft).allocations[0]).toBe(false);
  });

  it('dirty tracking: unchanged draft is clean, any edit flips it', () => {
    const draft = fromReservation(WIRE);
    const baseline = snapshot(draft);
    expect(isDirty(draft, baseline)).toBe(false);
    draft.values['name'] = 'Klausur Physik III';
    expect(isDirty(draft, baseline)).toBe(true);
  });

  it('mutating the input does not leak back into the draft (defensive copies)', () => {
    const draft = fromReservation(WIRE);
    const input = toReservationInput(draft);
    (input.appointments[0].repeating!.exceptions as string[]).push('2026-08-01');
    input.allocations[1].appointmentIds!.push('a-x');
    expect(draft.appointments[0].repeating!.exceptions).toEqual(['2026-07-15']);
    expect(draft.allocations[1].appointmentIds).toEqual(['a1111111-1111-4111-8111-111111111111']);
  });
});

describe('four-field coupling (withStart / withEnd)', () => {
  const appointment = { start: '2026-07-07T10:00:00', end: '2026-07-07T11:30:00' };

  it('withStart shifts the end so the duration stays (time edit)', () => {
    expect(withStart(appointment, '2026-07-07T14:00:00')).toEqual({
      start: '2026-07-07T14:00:00',
      end: '2026-07-07T15:30:00',
    });
  });

  it('withStart shifts across days (date edit) and over month ends', () => {
    expect(withStart(appointment, '2026-07-31T23:00:00')).toEqual({
      start: '2026-07-31T23:00:00',
      end: '2026-08-01T00:30:00',
    });
  });

  it('withStart keeps a multi-day duration', () => {
    const loan = { start: '2026-07-07T09:00:00', end: '2026-07-09T17:00:00' };
    expect(withStart(loan, '2026-07-14T09:00:00')).toEqual({
      start: '2026-07-14T09:00:00',
      end: '2026-07-16T17:00:00',
    });
  });

  it('withEnd after the start just changes the duration', () => {
    expect(withEnd(appointment, '2026-07-08T09:15:00')).toEqual({
      start: '2026-07-07T10:00:00',
      end: '2026-07-08T09:15:00',
    });
  });

  it('withEnd at or before the start clamps to start + 15 min', () => {
    expect(withEnd(appointment, '2026-07-07T10:00:00').end).toBe('2026-07-07T10:15:00');
    expect(withEnd(appointment, '2026-07-06T18:00:00').end).toBe('2026-07-07T10:15:00');
  });
});

describe('scopeAllocations / newScopedDraft (Swing parity — PRD 094)', () => {
  const chips = [
    { id: 'r1', kind: 'resource', label: 'Kamera G40' },
    { id: 'u1', kind: 'user', label: 'admin' },
    { id: 'r2', kind: 'resource', label: 'Raum C452' },
    { id: 'ev1', kind: 'event', label: 'Physik' },
  ];

  it('keeps only resource chips, as applies-to-all allocations', () => {
    expect(scopeAllocations(chips)).toEqual([
      { allocatableId: 'r1', allocatableName: 'Kamera G40', appointmentIds: null },
      { allocatableId: 'r2', allocatableName: 'Raum C452', appointmentIds: null },
    ]);
  });

  it('no scope → no allocations', () => {
    expect(scopeAllocations([])).toEqual([]);
  });

  it('newScopedDraft seeds the draft with the scope resources', () => {
    const draft = newScopedDraft('ausleihe', new Date('2026-07-07T12:00:00'), chips, 'e-x');
    expect(draft.id).toBe('e-x');
    expect(draft.typeKey).toBe('ausleihe');
    expect(draft.allocations.map((a) => a.allocatableId)).toEqual(['r1', 'r2']);
    expect(draft.appointments.length).toBe(1); // still a normal new draft
  });
});

describe('rangeScopedDraft (month-grid drag-create — PRD 095 Phase 3)', () => {
  const chips = [
    { id: 'r1', kind: 'resource', label: 'Kamera G40' },
    { id: 'u1', kind: 'user', label: 'admin' },
  ];

  it('single-day range → one 09:00–10:00 appointment on that day', () => {
    const draft = rangeScopedDraft('event', chips, '2026-07-22', '2026-07-22');
    expect(draft.appointments).toHaveLength(1);
    expect(draft.appointments[0].start).toBe('2026-07-22T09:00:00');
    expect(draft.appointments[0].end).toBe('2026-07-22T10:00:00');
    expect(draft.appointments[0].repeating).toBeNull();
  });

  it('multi-day range → one appointment from first day 09:00 to last day 17:00', () => {
    const draft = rangeScopedDraft('event', chips, '2026-07-22', '2026-07-24');
    expect(draft.appointments[0].start).toBe('2026-07-22T09:00:00');
    expect(draft.appointments[0].end).toBe('2026-07-24T17:00:00');
  });

  it('carries the scope resources and typeKey like newScopedDraft', () => {
    const draft = rangeScopedDraft('ausleihe', chips, '2026-07-01', '2026-07-02');
    expect(draft.typeKey).toBe('ausleihe');
    expect(draft.allocations.map((a) => a.allocatableId)).toEqual(['r1']);
    expect(draft.persisted).toBe(false);
  });
});
