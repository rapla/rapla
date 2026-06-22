import type { ViewColumn } from '../graphql/graphql.service';

/**
 * Projects an aggregation/pivot row ({@code appointmentBlockStats}: {@code keys}
 * + {@code values} + {@code count}) into a FLAT {@code { alias: value }} row,
 * driven by the column descriptors. Keeps the renderer type-agnostic: flat views
 * (no {@code kind}) pass through untouched, aggregation views get projected — the
 * downstream table/grouping code never knows the difference.
 */

interface EntityNode {
  id?: unknown;
  name?: unknown;
  classification?: Record<string, unknown>;
}

/**
 * Navigate an entity path: {@code id} → {@code entity.id}; {@code name} →
 * {@code entity.name}; any other segment → {@code entity.classification[segment]}
 * (which may itself be a nested entity node). E.g. {@code "Gebaeude.name"} →
 * {@code entity.classification.Gebaeude.name}.
 */
function resolveEntityPath(entity: unknown, path: string): unknown {
  let cur: unknown = entity;
  for (const seg of path.split('.')) {
    if (cur == null || typeof cur !== 'object') return null;
    const node = cur as EntityNode;
    if (seg === 'id') cur = node.id;
    else if (seg === 'name') cur = node.name;
    else cur = node.classification?.[seg];
  }
  return cur ?? null;
}

interface StatKey {
  /** Group dimension entries are POSITIONAL — no key field on the wire. */
  value?: unknown;
  entity?: unknown;
}
interface StatValue {
  key: string;
  number?: unknown;
}
interface StatRow {
  keys?: StatKey[];
  values?: StatValue[];
  count?: unknown;
}

/** True when any column carries a {@code kind} → the rows are aggregation stats. */
export function isProjectedView(columns: ViewColumn[]): boolean {
  return columns.some((c) => !!c.kind);
}

/**
 * Map each group alias → its POSITIONAL index. The {@code keys} array entries are
 * positional (no {@code key} field): the i-th {@code kind:group} column maps to
 * {@code keys[i]}. Entity columns reference their group by alias via this map.
 */
function groupIndexByAlias(columns: ViewColumn[]): Map<string, number> {
  const map = new Map<string, number>();
  let i = 0;
  for (const c of columns) if (c.kind === 'group') map.set(c.alias, i++);
  return map;
}

function projectCell(row: StatRow, col: ViewColumn, groupIdx: Map<string, number>): unknown {
  switch (col.kind) {
    case 'group': {
      const idx = groupIdx.get(col.alias) ?? -1;
      return row.keys?.[idx]?.value ?? null;
    }
    case 'entity': {
      const idx = typeof col.group === 'string' ? (groupIdx.get(col.group) ?? -1) : -1;
      const entity = row.keys?.[idx]?.entity;
      return col.path ? resolveEntityPath(entity, col.path) : null;
    }
    case 'value':
      return row.values?.find((v) => v.key === col.alias)?.number ?? null;
    case 'count':
      return row.count ?? null;
    default:
      return (row as Record<string, unknown>)[col.alias] ?? null;
  }
}

export function projectRow(
  row: Record<string, unknown>,
  columns: ViewColumn[],
): Record<string, unknown> {
  const groupIdx = groupIndexByAlias(columns);
  const out: Record<string, unknown> = {};
  for (const col of columns) out[col.alias] = projectCell(row as StatRow, col, groupIdx);
  return out;
}
