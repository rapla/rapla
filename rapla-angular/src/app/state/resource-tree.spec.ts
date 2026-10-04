import { describe, it, expect } from 'vitest';

import type { ResourceItem } from './resource-selection-store';
import {
  buildTree,
  membersOf,
  resourceNodes,
  searchGroups,
  typeFolders,
  visibleRows,
  type TreeNode,
} from './resource-tree';

const res = (id: string, label: string, groupPaths: string[][] = []): ResourceItem => ({
  id,
  label,
  kind: 'resource',
  typeKey: 'room',
  typeName: 'Raum',
  groupPaths,
});

const labels = (nodes: TreeNode[]) => nodes.map((n) => n.label);

describe('resource tree (PRD 119 D2/D11)', () => {
  it('groups by the first path level, A–Z, with the ungrouped resources after the groups in list order (PRD 127 D6)', () => {
    const tree = buildTree([
      res('r1', 'Hörsaal 1', [['Gebäude B']]),
      res('r2', 'Labor', []),
      res('r3', 'Hörsaal 2', [['Gebäude A']]),
      res('r4', 'Aula', []),
    ]);
    expect(labels(tree)).toEqual(['Gebäude A', 'Gebäude B', 'Labor', 'Aula']);
    expect(tree[0].kind).toBe('group');
    expect(tree[2].kind).toBe('resource');
  });

  it('drops empty group names — the resource stays ungrouped (review S2)', () => {
    const tree = buildTree([
      res('r1', 'Aula', [[''], ['  ']]),
      res('r2', 'Labor', [['Gebäude A', '']]),
    ]);
    expect(labels(tree)).toEqual(['Gebäude A', 'Aula']);
    expect(labels(tree[0].children)).toEqual(['Labor']);
  });

  it('puts a resource under each of its paths and counts distinct members', () => {
    const tree = buildTree([
      res('r1', 'Hörsaal 1', [['Gebäude A'], ['Gebäude B']]),
      res('r2', 'Hörsaal 2', [['Gebäude A']]),
    ]);
    const [a, b] = tree;
    expect(labels(a.children)).toEqual(['Hörsaal 1', 'Hörsaal 2']);
    expect(labels(b.children)).toEqual(['Hörsaal 1']);
    expect(a.count).toBe(2);
    expect(b.count).toBe(1);
  });

  it('nests longer paths as deeper group levels (generic over path length)', () => {
    const tree = buildTree([
      res('c1', 'WI 2026 A', [['Wirtschaft', 'Wirtschaftsinformatik']]),
      res('c2', 'WI 2026 B', [['Wirtschaft', 'Wirtschaftsinformatik']]),
      res('c3', 'BWL 2026', [['Wirtschaft', 'BWL']]),
    ]);
    expect(labels(tree)).toEqual(['Wirtschaft']);
    expect(tree[0].count).toBe(3);
    expect(labels(tree[0].children)).toEqual(['BWL', 'Wirtschaftsinformatik']);
    expect(labels(tree[0].children[1].children)).toEqual(['WI 2026 A', 'WI 2026 B']);
  });

  it('collects the distinct resources below a node for "alle wählen"', () => {
    const tree = buildTree([
      res('r1', 'Hörsaal 1', [
        ['A', 'X'],
        ['A', 'Y'],
      ]),
      res('r2', 'Hörsaal 2', [['A', 'Y']]),
    ]);
    expect(membersOf(tree[0]).map((x) => x.id)).toEqual(['r1', 'r2']);
  });

  it('shows only the expanded levels as rows, with their depth', () => {
    const tree = buildTree([res('r1', 'Hörsaal 1', [['Gebäude A']]), res('r2', 'Aula')]);
    expect(visibleRows(tree, new Set()).map((r) => [r.node?.label, r.depth])).toEqual([
      ['Gebäude A', 0],
      ['Aula', 0],
    ]);
    const open = new Set([tree[0].key]);
    expect(visibleRows(tree, open).map((r) => [r.node?.label, r.depth])).toEqual([
      ['Gebäude A', 0],
      ['Hörsaal 1', 1],
      ['Aula', 0],
    ]);
  });

  describe('D12 caps', () => {
    const many = (n: number, path: string[][] = []) =>
      Array.from({ length: n }, (_, i) => res(`r${i}`, `Raum ${String(i).padStart(3, '0')}`, path));
    const shape = (rows: ReturnType<typeof visibleRows>) =>
      rows.map((r) => (r.node ? r.node.label : `+${r.hidden}@${r.more}`));

    it('the children of one node stop at 100, followed by a "more" row for that node', () => {
      const tree = buildTree(many(150, [['Gebäude A']]));
      const rows = visibleRows(tree, new Set(['/Gebäude A']));
      expect(rows.length).toBe(1 + 100 + 1);
      expect(shape(rows).at(-1)).toBe('+50@/Gebäude A');
      expect(rows.at(-1)?.depth).toBe(1);
    });

    it('the top level is capped too, and a raised limit reveals the next block', () => {
      const tree = buildTree(many(250));
      expect(shape(visibleRows(tree, new Set())).at(-1)).toBe('+150@');
      const more = visibleRows(tree, new Set(), new Map([['', 200]]));
      expect(more.length).toBe(201);
      expect(shape(more).at(-1)).toBe('+50@');
    });
  });

  describe('PRD 120 — nesting by parents', () => {
    const node = (
      id: string,
      label: string,
      parentIds: string[] = [],
      groupPaths: string[][] = [],
    ) => ({
      ...res(id, label, groupPaths),
      parentIds,
    });
    const building = node('b', 'Gebäude A');
    const room = node('r', 'Raum 1', ['b']);
    const part = node('p', 'Raum 1a', ['r']);
    const ids = (nodes: TreeNode[]) => nodes.map((n) => n.item?.id);

    it('building ▸ room ▸ sub-room, from the whole list, rooms keep their own place', () => {
      const tree = buildTree([building], [building, room, part]);
      expect(ids(tree)).toEqual(['b']);
      expect(ids(tree[0].children)).toEqual(['r']);
      expect(ids(tree[0].children[0].children)).toEqual(['p']);
      expect(ids(buildTree([building, room, part]))).toEqual(['b', 'r', 'p']);
    });

    it('a course under two groups appears under each', () => {
      const course = node('c', 'Kurs', ['g1', 'g2']);
      const tree = buildTree([node('g1', 'Gruppe 1'), node('g2', 'Gruppe 2'), course]);
      expect(ids(tree[0].children)).toEqual(['c']);
      expect(ids(tree[1].children)).toEqual(['c']);
    });

    it('nests under categorization groups too (D8)', () => {
      const tree = buildTree([node('b', 'Gebäude A', [], [['Campus']]), room]);
      expect(ids(tree[0].children[0].children)).toEqual(['r']);
    });

    it('a cycle A→B→A and a self-reference stop at the first repeat on the path', () => {
      const a = node('a', 'A', ['b', 'a']);
      const b = node('b', 'B', ['a']);
      const tree = buildTree([a, b]);
      expect(ids(tree[0].children)).toEqual(['b']);
      expect(ids(tree[0].children[0].children)).toEqual([]);
      const open = new Set([tree[0].key, tree[0].children[0].key]);
      expect(visibleRows(tree, open).map((r) => r.node?.label)).toEqual(['A', 'B', 'B']);
    });

    it('an expanded resource shows its children; alle wählen stops at a resource (D7)', () => {
      const tree = buildTree([node('b', 'Gebäude A', [], [['Campus']]), room]);
      const open = new Set([tree[0].key, tree[0].children[0].key]);
      expect(visibleRows(tree, open).map((r) => [r.node?.label, r.depth])).toEqual([
        ['Campus', 0],
        ['Gebäude A', 1],
        ['Raum 1', 2],
        ['Raum 1', 0],
      ]);
      expect(membersOf(tree[0]).map((x) => x.id)).toEqual(['b']);
    });

    it('resourceNodes nests flat-chip rows from the whole list, with the path guard', () => {
      const [b] = resourceNodes([building], [building, room, part]);
      expect(b.key).toBe('#b');
      expect(ids(b.children)).toEqual(['r']);
      expect(ids(b.children[0].children)).toEqual(['p']);
      const [a] = resourceNodes(
        [node('a', 'A', ['c'])],
        [node('a', 'A', ['c']), node('c', 'C', ['a'])],
      );
      expect(ids(a.children)).toEqual(['c']);
      expect(ids(a.children[0].children)).toEqual([]);
    });
  });

  describe('PRD 127 — server order, type folders, grouped search', () => {
    const it2 = (
      id: string,
      label: string,
      typeKey: string,
      typeName: string,
      parentIds: string[] = [],
    ): ResourceItem => ({ id, label, kind: 'resource', typeKey, typeName, parentIds });
    const kurs = it2('k', 'Zeta-Kurs', 'course', 'Kurse');
    const raum2 = it2('r2', 'Raum Z', 'room', 'Räume');
    const raum1 = it2('r1', 'Raum A', 'room', 'Räume');
    const teil2 = it2('t2', 'Teil Z', 'room', 'Räume', ['r2']);
    const teil1 = it2('t1', 'Teil A', 'room', 'Räume', ['r2']);
    const ids = (nodes: TreeNode[]) => nodes.map((n) => n.item?.id);

    it('children of a resource keep the order of the whole list, not A–Z (D6)', () => {
      const tree = buildTree([raum2], [raum2, teil2, teil1]);
      expect(ids(tree[0].children)).toEqual(['t2', 't1']);
    });

    it('one folder per type in order of first appearance, each holding its type tree', () => {
      const all = [kurs, raum2, raum1];
      const folders = typeFolders(all, all);
      expect(folders.map((f) => [f.key, f.label, f.kind, f.count])).toEqual([
        ['@course', 'Kurse', 'type', 1],
        ['@room', 'Räume', 'type', 2],
      ]);
      expect(ids(folders[1].children)).toEqual(['r2', 'r1']);
      expect(membersOf(folders[1]).map((x) => x.id)).toEqual(['r2', 'r1']);
    });

    it('rows of two folders never share a key, even under equal group names', () => {
      const a = { ...it2('a', 'A', 'room', 'Räume'), groupPaths: [['Campus']] };
      const b = { ...it2('b', 'B', 'course', 'Kurse'), groupPaths: [['Campus']] };
      const folders = typeFolders([a, b], [a, b]);
      expect(folders[0].children[0].key).not.toBe(folders[1].children[0].key);
    });

    it('search is flat and grouped by type in folder order, users last (D2)', () => {
      const users: ResourceItem[] = [{ id: 'u1', label: 'Raumplaner', kind: 'user' }];
      const all = [kurs, raum2, teil2, raum1];
      const groups = searchGroups(all, users, 'ra', 'Benutzer');
      expect(groups.map((g) => [g.key, g.label, g.kind, g.count])).toEqual([
        ['?room', 'Räume', 'heading', 2],
        ['?users', 'Benutzer', 'heading', 1],
      ]);
      expect(ids(groups[0].children)).toEqual(['r2', 'r1']);
      expect(ids(groups[0].children[0].children)).toEqual(['t2']);
      expect(groups[1].children[0].item?.id).toBe('u1');
      expect(searchGroups(all, users, '  ', 'Benutzer')).toEqual([]);
    });

    it('alle wählen on a heading takes its hits', () => {
      const [rooms] = searchGroups([raum2, teil2, raum1], [], 'raum', 'Benutzer');
      expect(membersOf(rooms).map((x) => x.id)).toEqual(['r2', 'r1']);
    });
  });
});
