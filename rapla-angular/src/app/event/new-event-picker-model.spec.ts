import { describe, expect, it } from 'vitest';

import type { EventDraft } from './event-draft';
import {
  buildPickItems,
  draftFromTemplate,
  filterPickItems,
  pushRecent,
  type PickItem,
} from './new-event-picker-model';

const types = [
  { key: 'event', name: 'Veranstaltung' },
  { key: 'exam', name: 'Klausur' },
];
const templates = [
  { id: 't1', name: 'TINF23B4 Mathe 1', path: [] },
  { id: 't2', name: 'WWI23A Mathe 1', path: [] },
];

describe('buildPickItems / filterPickItems (PRD 104 D6)', () => {
  it('pins types before templates', () => {
    const items = buildPickItems(types, templates);
    expect(items.map((i) => i.kind)).toEqual(['type', 'type', 'template', 'template']);
  });

  it('filters BOTH kinds, keeping types pinned within the result', () => {
    const items = buildPickItems(types, templates);
    const hits = filterPickItems(items, 'mathe');
    expect(hits.map((i) => i.id)).toEqual(['t1', 't2']);
    const mixed = filterPickItems(items, 'a');
    expect(mixed[0].kind).toBe('type');
  });

  it('ANDs whitespace-split terms case-insensitively', () => {
    const items = buildPickItems(types, templates);
    expect(filterPickItems(items, 'tinf23 MATHE').map((i) => i.id)).toEqual(['t1']);
  });
});

describe('pushRecent (PRD 104 D5)', () => {
  it('prepends, dedupes by kind+id and caps at 6', () => {
    let recents: PickItem[] = [];
    for (let i = 1; i <= 8; i++)
      recents = pushRecent(recents, { kind: 'template', id: 't' + i, name: 'T' + i });
    expect(recents.length).toBe(6);
    expect(recents[0].id).toBe('t8');
    recents = pushRecent(recents, { kind: 'type', id: 't5', name: 'Typ t5' });
    expect(recents.filter((r) => r.id === 't5').length).toBe(2); // different kinds coexist
    recents = pushRecent(recents, { kind: 'type', id: 't5', name: 'Typ t5' });
    expect(recents.filter((r) => r.kind === 'type' && r.id === 't5').length).toBe(1);
  });
});

describe('draftFromTemplate (PRD 104 D8)', () => {
  const source: EventDraft = {
    id: 'e-template-res',
    persisted: true,
    typeKey: 'event',
    values: { name: 'Vorlagen-Event' },
    appointments: [
      {
        id: 'a-one',
        start: '2001-10-16T12:00:00',
        end: '2001-10-16T14:00:00',
        allDay: false,
        repeating: {
          type: 'WEEKLY',
          interval: 1,
          end: '2001-12-18T14:00:00',
          count: null,
          weekdays: null,
          exceptions: ['2001-10-30T12:00:00'],
        },
      },
      {
        id: 'a-two',
        start: '2001-10-18T09:00:00',
        end: '2001-10-18T10:00:00',
        allDay: false,
        repeating: null,
      },
    ],
    allocations: [
      { allocatableId: 'r1', allocatableName: 'Raum 1', appointmentIds: ['a-one'] },
      { allocatableId: 'r2', allocatableName: 'Raum 2', appointmentIds: null },
    ],
    lastChanged: '2001-10-16T12:00:00',
  };

  it('re-keys every id, remaps restrictions and resets persistence', () => {
    const draft = draftFromTemplate(source, null);
    expect(draft.id).not.toBe(source.id);
    expect(draft.persisted).toBe(false);
    expect(draft.lastChanged).toBeNull();
    const newIds = draft.appointments.map((a) => a.id);
    expect(newIds).not.toContain('a-one');
    expect(draft.allocations[0].appointmentIds).toEqual([newIds[0]]);
    expect(draft.allocations[1].appointmentIds).toBeNull();
  });

  it('keeps dates untouched without a target', () => {
    const draft = draftFromTemplate(source, null);
    expect(draft.appointments[0].start).toBe('2001-10-16T12:00:00');
  });

  it('day-aligns the EARLIEST appointment onto the target, times kept, gaps preserved', () => {
    const draft = draftFromTemplate(source, { day: '2026-07-20', startMin: null });
    expect(draft.appointments[0].start).toBe('2026-07-20T12:00:00');
    expect(draft.appointments[0].end).toBe('2026-07-20T14:00:00');
    expect(draft.appointments[1].start).toBe('2026-07-22T09:00:00'); // +2 days gap kept
  });

  it('minute-aligns onto a dragged slot and shifts series bounds + exceptions along', () => {
    const draft = draftFromTemplate(source, { day: '2026-07-20', startMin: 8 * 60 });
    expect(draft.appointments[0].start).toBe('2026-07-20T08:00:00');
    expect(draft.appointments[0].end).toBe('2026-07-20T10:00:00');
    const rule = draft.appointments[0].repeating;
    expect(rule?.end).toBe('2026-09-21T10:00:00'); // +63 days from the aligned start
    expect(rule?.exceptions).toEqual(['2026-08-03T08:00:00']); // +14 days, slot time
  });
});
