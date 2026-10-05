import type { ResourceItem } from './resource-selection-store';
import type { FilterEntry } from './filter-store';
import { PAGE_SIZE, filterRows } from './resource-picker';

/** PRD 119 D2/D11, PRD 127 — a node of the picker tree: a section, a type folder, a search heading, a group level or a resource. */
export interface TreeNode {
  key: string;
  label: string;
  kind: 'section' | 'type' | 'heading' | 'group' | 'resource' | 'entry';
  item?: ResourceItem;
  /** PRD 128 — a conflict or request leaf: the chip it makes and the date a click jumps to. */
  entry?: FilterEntry;
  date?: string | null;
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

/**
 * Groups by `groupPaths` (any depth); a resource sits under each of its paths, ungrouped ones after the groups.
 * PRD 120 — a resource node's children are the resources of `all` that name it in `parentIds`; a cycle stops at the
 * first repeat on the path.
 */
export function buildTree(
  items: readonly ResourceItem[],
  all: readonly ResourceItem[] = items,
  prefix = '',
  childrenOf: ReadonlyMap<string, ResourceItem[]> = childIndex(all),
): TreeNode[] {
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
  return toNodes(root, prefix, childrenOf);
}

/** PRD 120 — parent id → the resources of `all` that name it in `parentIds`. */
export function childIndex(all: readonly ResourceItem[]): Map<string, ResourceItem[]> {
  const childrenOf = new Map<string, ResourceItem[]>();
  for (const item of all) {
    for (const parent of new Set(item.parentIds ?? [])) {
      const list = childrenOf.get(parent) ?? [];
      list.push(item);
      childrenOf.set(parent, list);
    }
  }
  return childrenOf;
}

/** PRD 120 D9 — the flat chips' rows as resource nodes, in their order, nested by `parentIds` like {@link buildTree}. */
export function resourceNodes(
  items: readonly ResourceItem[],
  all: readonly ResourceItem[],
  childrenOf: ReadonlyMap<string, ResourceItem[]> = childIndex(all),
  prefix = '',
): TreeNode[] {
  return items.map((item) => resourceNode(item, prefix, childrenOf, new Set()));
}

function resourceNode(
  item: ResourceItem,
  prefix: string,
  childrenOf: ReadonlyMap<string, ResourceItem[]>,
  path: ReadonlySet<string>,
): TreeNode {
  const key = `${prefix}#${item.id}`;
  const below = new Set(path).add(item.id);
  return {
    key,
    label: item.label,
    kind: 'resource',
    item,
    children: (childrenOf.get(item.id) ?? [])
      .filter((child) => !below.has(child.id))
      .map((child) => resourceNode(child, key, childrenOf, below)),
    count: 1,
  };
}

function toNodes(
  level: Level,
  prefix: string,
  childrenOf: ReadonlyMap<string, ResourceItem[]>,
): TreeNode[] {
  const groups = [...level.groups.entries()]
    .map(([name, child]) => {
      const key = `${prefix}/${name}`;
      const node: TreeNode = {
        key,
        label: name,
        kind: 'group',
        children: toNodes(child, key, childrenOf),
        count: 0,
      };
      node.count = membersOf(node).length;
      return node;
    })
    .sort(byNodeLabel);
  const leaves = level.items.map((item) => resourceNode(item, prefix, childrenOf, new Set()));
  return [...groups, ...leaves];
}

/** The distinct resources below a node — what "alle wählen" selects. PRD 120 D7: a resource stands for its subtree. */
export function membersOf(node: TreeNode): ResourceItem[] {
  const seen = new Map<string, ResourceItem>();
  const walk = (n: TreeNode) => {
    if (n.item && !seen.has(n.item.id)) seen.set(n.item.id, n.item);
    if (n.kind !== 'resource') n.children.forEach(walk);
  };
  walk(node);
  return [...seen.values()];
}

/** PRD 127 D1/D5 — one folder per type, in order of first appearance in the server-sorted list, holding its type tree. */
export function typeFolders(
  items: readonly ResourceItem[],
  all: readonly ResourceItem[],
  childrenOf: ReadonlyMap<string, ResourceItem[]> = childIndex(all),
): TreeNode[] {
  return [...byType(items).entries()].map(([typeKey, members]) => ({
    key: `@${typeKey}`,
    label: members[0].typeName ?? typeKey,
    kind: 'type' as const,
    children: buildTree(members, all, `@${typeKey}`, childrenOf),
    count: members.length,
  }));
}

function byType(items: readonly ResourceItem[]): Map<string, ResourceItem[]> {
  const types = new Map<string, ResourceItem[]>();
  for (const item of items) {
    const key = item.typeKey ?? '';
    const list = types.get(key);
    if (list) list.push(item);
    else types.set(key, [item]);
  }
  return types;
}

/**
 * PRD 127 D2 — the search hits flat under one heading per type, in folder order, users last; each hit keeps its own
 * children (PRD 120).
 */
export function searchGroups(
  resources: readonly ResourceItem[],
  users: readonly ResourceItem[],
  query: string,
  usersLabel: string,
  all: readonly ResourceItem[] = resources,
): TreeNode[] {
  if (!query.trim()) return [];
  const childrenOf = childIndex(all);
  const heading = (key: string, label: string, hits: ResourceItem[]): TreeNode => ({
    key,
    label,
    kind: 'heading',
    children: hits.map((item) => resourceNode(item, key, childrenOf, new Set())),
    count: hits.length,
  });
  const groups = [...byType(filterRows(resources, query)).entries()].map(([typeKey, hits]) =>
    heading(`?${typeKey}`, hits[0].typeName ?? typeKey, hits),
  );
  const userHits = filterRows(users, query);
  return userHits.length ? [...groups, heading('?users', usersLabel, userHits)] : groups;
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
    if (node.children.length && expanded.has(node.key)) {
      rows.push(...visibleRows(node.children, expanded, limits, depth + 1, node.key));
    }
  }
  if (nodes.length > limit) rows.push({ more: parentKey, hidden: nodes.length - limit, depth });
  return rows;
}
