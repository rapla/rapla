import { describe, it, expect } from 'vitest';
import { projectRow, isProjectedView } from './stat-projection';
import type { ViewColumn } from '../graphql/graphql.service';

// The Raumauslastung column descriptors (server) + one stat row.
const COLUMNS: ViewColumn[] = [
  { alias: 'raum', header: 'raum', kind: 'group', type: 'Allocatable' },
  { alias: 'id', header: 'id', kind: 'entity', group: 'raum', path: 'id' },
  {
    alias: 'plaetze',
    header: 'plaetze',
    kind: 'entity',
    group: 'raum',
    path: 'AnzahlPlaetzeInsgesamt',
  },
  { alias: 'gebName', header: 'gebName', kind: 'entity', group: 'raum', path: 'Gebaeude.name' },
  {
    alias: 'gebKey',
    header: 'gebKey',
    kind: 'entity',
    group: 'raum',
    path: 'Gebaeude.Gebaeudename',
  },
  { alias: 'minuten', header: 'minuten', kind: 'value', fn: 'SUM' },
  { alias: 'termine', header: 'termine', kind: 'value', fn: 'COUNT' },
  { alias: 'count', header: 'count', kind: 'count', type: 'Int' },
];

const STAT_ROW = {
  // keys entries are POSITIONAL on the wire (no `key` field) — the i-th group
  // column maps to keys[i]. Here the single group column `raum` → keys[0].
  keys: [
    {
      value: 'A 1.100 Großer Hörsaal',
      entity: {
        id: 'r68dbdf1',
        classification: {
          AnzahlPlaetzeInsgesamt: 120,
          Gebaeude: {
            name: 'Gebäude A',
            classification: { Gebaeudename: 'MOS Gebäude A' },
          },
        },
      },
    },
  ],
  values: [
    { key: 'minuten', number: 90 },
    { key: 'termine', number: 1 },
  ],
  count: 1,
};

describe('isProjectedView', () => {
  it('is true when any column carries a kind (aggregation view)', () => {
    expect(isProjectedView(COLUMNS)).toBe(true);
  });
  it('is false for flat views (no kind)', () => {
    expect(isProjectedView([{ alias: 'name', header: 'Titel', type: 'String' }])).toBe(false);
  });
});

describe('projectRow', () => {
  const flat = projectRow(STAT_ROW, COLUMNS);

  it('group column → the key value', () => {
    expect(flat['raum']).toBe('A 1.100 Großer Hörsaal');
  });

  it('entity id path → entity.id', () => {
    expect(flat['id']).toBe('r68dbdf1');
  });

  it('entity attribute path → entity.classification[attr]', () => {
    expect(flat['plaetze']).toBe(120);
  });

  it('nested entity path "Gebaeude.name" → entity.classification.Gebaeude.name', () => {
    expect(flat['gebName']).toBe('Gebäude A');
  });

  it('nested attribute path "Gebaeude.Gebaeudename" → …Gebaeude.classification.Gebaeudename', () => {
    expect(flat['gebKey']).toBe('MOS Gebäude A');
  });

  it('value columns → the aggregate number (SUM / COUNT)', () => {
    expect(flat['minuten']).toBe(90);
    expect(flat['termine']).toBe(1);
  });

  it('count column → the row count', () => {
    expect(flat['count']).toBe(1);
  });

  it('a missing entity path resolves to null, not a throw', () => {
    const r = projectRow({ keys: [{ key: 'raum', value: 'x', entity: null }] }, COLUMNS);
    expect(r['gebName']).toBeNull();
  });
});
