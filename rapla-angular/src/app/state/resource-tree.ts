import type { ResourceItem } from './resource-selection-store';
import { PAGE_SIZE, byLabel, filterRows } from './resource-picker';

/** PRD 119 D2/D11 — a node of the picker tree under a type chip: a group level or a resource. */
export interface TreeNode {
  key: string;
  label: string;
  kind: 'group' | 'resource';
  item?: ResourceItem;
  children: TreeNode[];
  /** Distinct resources below this node (1 for a resource). */
  count: number;
}

export interface NodeRow {
  node: TreeNode;
  depth: number;
  more?: undefined;
  hidden?: undefined;
}

/** PRD 119 D12 — "Weitere n anzeigen" after the first block of one node's children (`more` = that node's key, '' = top level). */
export interface MoreRow {
  node?: undefined;
  more: string;
  hidden: number;
  depth: number;
}

export type TreeRow = NodeRow | MoreRow;

interface Level {
  groups: Map<string, Level>;
  items: ResourceItem[];
}

const collator = new Intl.Collator('de');

function byNodeLabel(a: TreeNode, b: TreeNode): number {
  return collator.compare(a.label, b.label);
}

/** Groups by `groupPaths` (any depth); a resource sits under each of its paths, ungrouped ones after the groups. */
export function buildTree(items: readonly ResourceItem[]): TreeNode[] {
  const root: Level = { groups: new Map(), items: [] };
  for (const item of items) {
    const paths = (item.groupPaths ?? [])
      .map((p) => p.filter((name) => name.trim()))
      .filter((p) => p.length > 0);
    if (!paths.length) {
      root.items.push(item);
      continue;
    }
    for (const path of paths) {
      let level = root;
      for (const name of path) {
        let next = level.groups.get(name);
        if (!next) {
          next = { groups: new Map(), items: [] };
          level.groups.set(name, next);
        }
        level = next;
      }
      if (!level.items.some((x) => x.id === item.id)) level.items.push(item);
    }
  }
  return toNodes(root, '');
}

function toNodes(level: Level, prefix: string): TreeNode[] {
  const groups = [...level.groups.entries()]
    .map(([name, child]) => {
      const key = `${prefix}/${name}`;
      const node: TreeNode = {
        key,
        label: name,
        kind: 'group',
        children: toNodes(child, key),
        count: 0,
      };
      node.count = membersOf(node).length;
      return node;
    })
    .sort(byNodeLabel);
  const leaves = [...level.items].sort(byLabel).map(
    (item): TreeNode => ({
      key: `${prefix}#${item.id}`,
      label: item.label,
      kind: 'resource',
      item,
      children: [],
      count: 1,
    }),
  );
  return [...groups, ...leaves];
}

/** The distinct resources below a node — what "alle wählen" selects. */
export function membersOf(node: TreeNode): ResourceItem[] {
  const seen = new Map<string, ResourceItem>();
  const walk = (n: TreeNode) => {
    if (n.item && !seen.has(n.item.id)) seen.set(n.item.id, n.item);
    n.children.forEach(walk);
  };
  walk(node);
  return [...seen.values()];
}

/** The rows the picker renders: every node of an expanded group, indented by depth; each level stops at its limit (D12). */
export function visibleRows(
  nodes: readonly TreeNode[],
  expanded: ReadonlySet<string>,
  limits: ReadonlyMap<string, number> = new Map(),
  depth = 0,
  parentKey = '',
): TreeRow[] {
  const limit = limits.get(parentKey) ?? PAGE_SIZE;
  const rows: TreeRow[] = [];
  for (const node of nodes.slice(0, limit)) {
    rows.push({ node, depth });
    if (node.kind === 'group' && expanded.has(node.key)) {
      rows.push(...visibleRows(node.children, expanded, limits, depth + 1, node.key));
    }
  }
  if (nodes.length > limit) rows.push({ more: parentKey, hidden: nodes.length - limit, depth });
  return rows;
}

/** The groups on the path to any of the given resources — open by default (D12). */
export function pathKeysTo(nodes: readonly TreeNode[], ids: ReadonlySet<string>): Set<string> {
  const keys = new Set<string>();
  const walk = (node: TreeNode): boolean => {
    if (node.kind === 'resource') return !!node.item && ids.has(node.item.id);
    let hit = false;
    for (const child of node.children) if (walk(child)) hit = true;
    if (hit) keys.add(node.key);
    return hit;
  };
  nodes.forEach(walk);
  return keys;
}

/**
 * PRD 119 D3 — narrow the tree by the query: matching resources keep their groups, which open; a group whose own
 * name matches keeps all its members.
 */
export function filterTree(
  nodes: readonly TreeNode[],
  query: string,
): { nodes: TreeNode[]; expanded: Set<string> } {
  const expanded = new Set<string>();
  if (!query.trim()) return { nodes: [...nodes], expanded };
  const matches = (label: string) => filterRows([{ id: label, label }], query).length > 0;
  const walk = (list: readonly TreeNode[]): TreeNode[] => {
    const out: TreeNode[] = [];
    for (const node of list) {
      if (node.kind === 'resource') {
        if (node.item && filterRows([node.item], query).length) out.push(node);
        continue;
      }
      if (matches(node.label)) {
        expanded.add(node.key);
        out.push(node);
        continue;
      }
      const children = walk(node.children);
      if (children.length) {
        expanded.add(node.key);
        const kept: TreeNode = { ...node, children };
        kept.count = membersOf(kept).length;
        out.push(kept);
      }
    }
    return out;
  };
  return { nodes: walk(nodes), expanded };
}
