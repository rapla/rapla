import { ViewColumn } from './graphql.service';

/**
 * PRD 078 — pure rendering helpers for the generic view table. Convention over
 * config (PRD 074 §"Preferred design"): a column's value is read by its alias
 * from the GraphQL row, then rendered by shape — scalar as-is (datetime
 * formatted), an object by its {@code displayName}, a list joined. No DOM, no
 * Angular: tier-5 unit-testable in isolation.
 */

const DEFAULT_JOIN = ', ';

/**
 * Reduce a single GraphQL value to its display string. A nested object is the
 * one scalar leaf the query selected for that cell — which the author may name
 * {@code displayName} OR {@code name} (real views use both, e.g.
 * {@code allocatables { name }}). Prefer the conventional name fields, else the
 * first string-valued property.
 */
function scalarize(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') {
    const o = value as Record<string, unknown>;
    const candidate =
      o['displayName'] ?? o['name'] ?? Object.values(o).find((v) => typeof v === 'string');
    return candidate === null || candidate === undefined ? '' : String(candidate);
  }
  return String(value);
}

/** Format a scalar string by the column's type hint (only datetime needs work today). */
function formatScalar(s: string, type?: string): string {
  if (!s) return s;
  if (type === 'DateTime' || type === 'LocalDateTime') {
    const d = new Date(s);
    if (!isNaN(d.getTime())) return d.toLocaleString();
  }
  return s;
}

/**
 * Render one cell: the value at {@code column.alias} in {@code row}, shaped by
 * convention. Arrays join (separator = {@code column.join} or {@code ', '});
 * objects collapse to {@code displayName}; scalars format by {@code type}.
 */
export function renderCell(row: Record<string, unknown>, column: ViewColumn): string {
  const value = row?.[column.alias];
  if (value === null || value === undefined) return '';
  if (Array.isArray(value)) {
    return value
      .map((v) => scalarize(v))
      .filter((s) => s.length > 0)
      .join(column.join ?? DEFAULT_JOIN);
  }
  return formatScalar(scalarize(value), column.type);
}
