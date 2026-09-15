import { describe, it, expect } from 'vitest';

import type { ResourceItem } from './resource-selection-store';
import {
  buildTree,
  filterTree,
  membersOf,
  pathKeysTo,
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
  it('groups by the first path level, A–Z, with the ungrouped resources after the groups', () => {
    const tree = buildTree([
      res('r1', 'Hörsaal 1', [['Gebäude B']]),
      res('r2', 'Labor', []),
      res('r3', 'Hörsaal 2', [['Gebäude A']]),
      res('r4', 'Aula', []),
    ]);
    expect(labels(tree)).toEqual(['Gebäude A', 'Gebäude B', 'Aula', 'Labor']);
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

  describe('D12 caps and the path to the selection', () => {
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

    it('names the groups on the path to the selected resources', () => {
      const tree = buildTree([
        res('r1', 'Hörsaal 1', [['Gebäude A', 'EG']]),
        res('r2', 'Labor', [['Gebäude B']]),
        res('r3', 'Aula'),
      ]);
      expect([...pathKeysTo(tree, new Set(['r1', 'r3']))].sort()).toEqual([
        '/Gebäude A',
        '/Gebäude A/EG',
      ]);
    });
  });

  describe('filterTree', () => {
    const tree = buildTree([
      res('r1', 'Hörsaal 1', [['Gebäude A']]),
      res('r2', 'Labor', [['Gebäude A']]),
      res('r3', 'Hörsaal 2', [['Gebäude B']]),
      res('r4', 'Aula'),
    ]);

    it('keeps matching resources with their groups and opens those groups', () => {
      const { nodes, expanded } = filterTree(tree, 'hörsaal');
      expect(labels(nodes)).toEqual(['Gebäude A', 'Gebäude B']);
      expect(labels(nodes[0].children)).toEqual(['Hörsaal 1']);
      expect(expanded.has(nodes[0].key) && expanded.has(nodes[1].key)).toBe(true);
    });

    it('a group whose own name matches keeps all its members', () => {
      const { nodes } = filterTree(tree, 'gebäude a');
      expect(labels(nodes)).toEqual(['Gebäude A']);
      expect(labels(nodes[0].children)).toEqual(['Hörsaal 1', 'Labor']);
    });

    it('a blank query keeps the whole tree and opens nothing', () => {
      const { nodes, expanded } = filterTree(tree, '  ');
      expect(nodes).toEqual(tree);
      expect(expanded.size).toBe(0);
    });
  });
});
