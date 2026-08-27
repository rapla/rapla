import { describe, expect, it } from 'vitest';

import {
  changedItems,
  currentSemester,
  draftWithGroups,
  linkedOfGroups,
  openItemsOfGroups,
  semesterRange,
  windowSemesterDate,
  type LinkedEvent,
  type WorklistItem,
} from './import-models';

const item = (over: Partial<WorklistItem>): WorklistItem => ({
  sourceId: 'v:1',
  kind: 'v',
  name: 'Mathematik II',
  unit: null,
  semester: 'SoSe 2026',
  state: 'OPEN',
  changed: false,
  changedSince: null,
  groupIds: ['g1'],
  groupName: 'STG-TINF23B',
  boundReservationId: null,
  fullName: null,
  ...over,
});

describe('openItemsOfGroups', () => {
  it('keeps only OPEN items of the groups, deduped, name-sorted', () => {
    const items = [
      item({ sourceId: 'v:1', name: 'Zeta', groupIds: ['g1'] }),
      item({ sourceId: 'v:2', name: 'Alpha', groupIds: ['g1'] }),
      item({ sourceId: 'v:2', name: 'Alpha', groupIds: ['g2'] }),
      item({ sourceId: 'v:3', name: 'Beta', groupIds: ['g3'] }),
      item({ sourceId: 'v:4', name: 'Gamma', groupIds: ['g1'], state: 'BOUND' }),
    ];
    expect(openItemsOfGroups(items, ['g1', 'g2']).map((i) => i.name)).toEqual(['Alpha', 'Zeta']);
  });

  it('narrows to one semester when given (worklist loads unscoped)', () => {
    const items = [
      item({ sourceId: 'v:1', name: 'Zeta', semester: 'SoSe 2026' }),
      item({ sourceId: 'v:2', name: 'Alpha', semester: 'WiSe 2025/26' }),
    ];
    expect(openItemsOfGroups(items, ['g1'], 'SoSe 2026').map((i) => i.name)).toEqual(['Zeta']);
    expect(openItemsOfGroups(items, ['g1']).map((i) => i.name)).toEqual(['Alpha', 'Zeta']);
  });
});

describe('linkedOfGroups', () => {
  const ev = (over: Partial<LinkedEvent>): LinkedEvent => ({
    id: 'r1',
    name: 'Mathe',
    externalId: 'Vorlesung:1',
    firstDate: '2026-04-20T08:00:00',
    allocatableIds: ['g1'],
    ...over,
  });

  it('keeps only events of the groups, deduped, name-sorted', () => {
    const events = [
      ev({ id: 'r1', name: 'Zeta' }),
      ev({ id: 'r2', name: 'Alpha', allocatableIds: ['g1', 'g2'] }),
      ev({ id: 'r2', name: 'Alpha' }),
      ev({ id: 'r3', name: 'Beta', allocatableIds: ['g9'] }),
    ];
    expect(linkedOfGroups(events, ['g1']).map((e) => e.name)).toEqual(['Alpha', 'Zeta']);
  });
});

describe('draftWithGroups (parked drop → editor draft)', () => {
  const draft = () =>
    ({
      id: 'e1',
      persisted: false,
      typeKey: 'event',
      values: {},
      appointments: [],
      allocations: [{ allocatableId: 'r1', allocatableName: 'Raum 1', appointmentIds: null }],
      lastChanged: null,
    }) as Parameters<typeof draftWithGroups>[0];

  it('adds every Kurs group as applies-to-all allocation', () => {
    const out = draftWithGroups(draft(), [
      { id: 'g1', name: 'MOS-TINF23A' },
      { id: 'g2', name: 'MOS-TINF23B' },
    ]);
    expect(out.allocations.map((a) => a.allocatableId)).toEqual(['r1', 'g1', 'g2']);
    expect(out.allocations[1].appointmentIds).toBeNull();
  });

  it('never duplicates a group the template already allocates', () => {
    const base = draft();
    base.allocations.push({
      allocatableId: 'g1',
      allocatableName: 'MOS-TINF23A',
      appointmentIds: null,
    });
    const out = draftWithGroups(base, [{ id: 'g1', name: 'MOS-TINF23A' }]);
    expect(out.allocations.filter((a) => a.allocatableId === 'g1')).toHaveLength(1);
  });
});

describe('changedItems', () => {
  it('keeps only changed items, deduped + sorted', () => {
    const items = [
      item({ sourceId: 'v:1', name: 'Zeta', state: 'BOUND' }),
      item({ sourceId: 'v:2', name: 'Alpha', state: 'BOUND', changed: true }),
      item({ sourceId: 'v:2', name: 'Alpha', state: 'BOUND', changed: true, groupIds: ['g2'] }),
      item({ sourceId: 'v:4', name: 'Delta', state: 'OPEN' }),
    ];
    expect(changedItems(items).map((i) => i.name)).toEqual(['Alpha']);
  });
});

describe('semester helpers', () => {
  it('derives the current semester from the date', () => {
    expect(currentSemester(new Date('2026-08-05'))).toBe('SoSe 2026');
    expect(currentSemester(new Date('2026-11-02'))).toBe('WiSe 2026/27');
    expect(currentSemester(new Date('2027-02-10'))).toBe('WiSe 2026/27');
  });

  it('a boundary-straddling week counts as the semester it mostly shows', () => {
    const w = { from: '2025-09-29T00:00:00', to: '2025-10-06T00:00:00' };
    expect(currentSemester(windowSemesterDate(w))).toBe('WiSe 2025/26');
    const plainSose = { from: '2026-05-04T00:00:00', to: '2026-05-11T00:00:00' };
    expect(currentSemester(windowSemesterDate(plainSose))).toBe('SoSe 2026');
  });

  it('semesterRange spans the whole semester (end exclusive)', () => {
    expect(semesterRange(new Date('2026-08-05'))).toEqual({
      from: '2026-04-01T00:00:00',
      to: '2026-10-01T00:00:00',
    });
    expect(semesterRange(new Date('2025-12-08'))).toEqual({
      from: '2025-10-01T00:00:00',
      to: '2026-04-01T00:00:00',
    });
    expect(semesterRange(new Date('2026-02-10'))).toEqual({
      from: '2025-10-01T00:00:00',
      to: '2026-04-01T00:00:00',
    });
  });
});
