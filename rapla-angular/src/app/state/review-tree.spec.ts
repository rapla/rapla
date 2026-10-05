import { describe, expect, it } from 'vitest';

import type { ResourceItem } from './resource-selection-store';
import {
  conflictEntry,
  conflictTree,
  filterReviewTree,
  requestEntry,
  requestFirstDate,
  requestTree,
  type ConflictWire,
  type RequestWire,
} from './review-tree';

const res = (id: string, label: string, typeKey: string, typeName: string): ResourceItem => ({
  id,
  label,
  kind: 'resource',
  typeKey,
  typeName,
});
const RESOURCES = [
  res('r1', 'Raum 1', 'room', 'Raum'),
  res('r2', 'Raum 2', 'room', 'Raum'),
  res('b1', 'Beamer', 'device', 'Gerät'),
];

const conflict = (id: string, resourceId: string, disabled = false): ConflictWire => ({
  id: `CONFLICT;${resourceId};${id}a;${id}b`,
  startDate: '2026-10-05T10:00:00',
  disabled,
  description: 'belegt',
  resource: { id: resourceId, name: RESOURCES.find((r) => r.id === resourceId)!.label },
  reservation1: { name: 'Vorlesung A' },
  reservation2: id === 'masked' ? null : { name: 'Vorlesung B' },
});

const request = (resourceId: string, reservationId: string): RequestWire => ({
  resource: { id: resourceId, name: RESOURCES.find((r) => r.id === resourceId)!.label },
  reservationId,
  reservation: { name: 'Tagung' },
  appointments: [{ start: '2026-10-07T09:00:00' }, { start: '2026-10-06T09:00:00' }],
});

describe('review-tree — PRD 128 Konflikte / Ressourcenanfragen sections', () => {
  it('conflicts: type → resource with counts, disabled ones as their own group (Swing createConflictModel)', () => {
    const tree = conflictTree(
      [
        { resourceId: 'r1', disabled: false, count: 3 },
        { resourceId: 'b1', disabled: false, count: 1 },
        { resourceId: 'r2', disabled: true, count: 2 },
      ],
      new Map(),
      RESOURCES,
      'Deaktiviert',
    );
    expect(tree.map((n) => [n.kind, n.label, n.count])).toEqual([
      ['type', 'Raum', 3],
      ['type', 'Gerät', 1],
      ['group', 'Deaktiviert', 2],
    ]);
    expect(tree[0].children.map((n) => [n.kind, n.key, n.label, n.count])).toEqual([
      ['group', 'k:res:r1', 'Raum 1', 3],
    ]);
    expect(tree[2].children[0].children.map((n) => n.key)).toEqual(['k:d:res:r2']);
  });

  it('a loaded resource lists its conflicts of the matching state as entries', () => {
    const loaded = new Map([['r1', [conflict('x', 'r1'), conflict('y', 'r1', true)]]]);
    const tree = conflictTree(
      [
        { resourceId: 'r1', disabled: false, count: 1 },
        { resourceId: 'r1', disabled: true, count: 1 },
      ],
      loaded,
      RESOURCES,
      'Deaktiviert',
    );
    const active = tree[0].children[0].children;
    expect(active.map((n) => [n.kind, n.label, n.date])).toEqual([
      ['entry', '05.10. 10:00 Vorlesung A ↔ Vorlesung B', '2026-10-05T10:00:00'],
    ]);
    expect(active[0].entry?.id).toBe('CONFLICT;r1;xa;xb');
    expect(tree[1].children[0].children[0].children.map((n) => n.entry?.id)).toEqual([
      'CONFLICT;r1;ya;yb',
    ]);
  });

  it('a masked other side shows the description instead of its name', () => {
    const tree = conflictTree(
      [{ resourceId: 'r1', disabled: false, count: 1 }],
      new Map([['r1', [conflict('masked', 'r1')]]]),
      RESOURCES,
      'Deaktiviert',
    );
    expect(tree[0].children[0].children[0].label).toBe('05.10. 10:00 Vorlesung A ↔ belegt');
  });

  it('conflict chip: label names the case, scopes the resource (OQ13)', () => {
    expect(conflictEntry(conflict('x', 'r1'))).toEqual({
      id: 'CONFLICT;r1;xa;xb',
      kind: 'conflict',
      label: '⚠ Raum 1 · 05.10.',
      resourceName: 'Raum 1',
    });
  });

  it('requests: type → resource → one entry per reservation; the chip names the case', () => {
    const tree = requestTree([request('b1', 'v1'), request('r1', 'v1')], RESOURCES);
    expect(tree.map((n) => [n.label, n.count])).toEqual([
      ['Raum', 1],
      ['Gerät', 1],
    ]);
    const leaf = tree[1].children[0].children[0];
    expect([leaf.kind, leaf.label, leaf.date]).toEqual([
      'entry',
      'Tagung · 06.10.',
      '2026-10-06T09:00:00',
    ]);
    expect(leaf.entry).toEqual(requestEntry(request('b1', 'v1')));
    expect(requestEntry(request('b1', 'v1'))).toEqual({
      id: 'REQUEST;b1;v1',
      kind: 'request',
      label: '? Beamer · Tagung',
      resourceName: 'Beamer',
    });
    expect(requestFirstDate({ ...request('b1', 'v1'), appointments: [] })).toBeNull();
  });

  it('search in Prüfen narrows to resources whose name matches; folder counts follow (OQ16)', () => {
    const tree = conflictTree(
      [
        { resourceId: 'r1', disabled: false, count: 3 },
        { resourceId: 'r2', disabled: false, count: 2 },
        { resourceId: 'b1', disabled: false, count: 1 },
        { resourceId: 'r2', disabled: true, count: 4 },
      ],
      new Map(),
      RESOURCES,
      'Deaktiviert',
    );
    const hits = filterReviewTree(tree, 'raum 2');
    expect(hits.map((n) => [n.label, n.count])).toEqual([
      ['Raum', 2],
      ['Deaktiviert', 4],
    ]);
    expect(hits[0].children.map((n) => n.label)).toEqual(['Raum 2']);
    expect(filterReviewTree(tree, 'nichts')).toEqual([]);
  });
});
