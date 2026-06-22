/**
 * PRD 078 — pure grouping helper for date-grouped views (e.g. the weekly Wochenansicht). Buckets generic GraphQL rows
 * by the weekday of a LocalDateTime field (read by a configurable alias), in
 * German Montag→Sonntag order. Timezone-safe: only the date PART is parsed, the
 * weekday computed via {@code Date.UTC} so the host timezone never shifts a row
 * into the wrong day. No DOM, no Angular: tier-5 unit-testable in isolation.
 */

export interface WeekdayGroup {
  /** 1=Montag … 7=Sonntag; 0 = the trailing "Ohne Datum" bucket. */
  weekday: number;
  label: string;
  rows: Record<string, unknown>[];
}

/** A generic section: rows sharing one column value. */
export interface RowGroup {
  key: string;
  label: string;
  rows: Record<string, unknown>[];
}

/**
 * Generic group-by-column: bucket rows by the string value of {@code alias},
 * preserving first-seen group order and input order within each group. The label
 * IS the value (the server provides display-ready values, e.g. a weekday-name
 * column). Empty/missing values collect into a trailing "—" group.
 */
export function groupByColumn(rows: Record<string, unknown>[], alias: string): RowGroup[] {
  const order: string[] = [];
  const buckets = new Map<string, Record<string, unknown>[]>();
  for (const row of rows) {
    const raw = row[alias];
    const key = raw == null || raw === '' ? '' : String(raw);
    let bucket = buckets.get(key);
    if (!bucket) {
      bucket = [];
      buckets.set(key, bucket);
      order.push(key);
    }
    bucket.push(row);
  }
  const keys = order.filter((k) => k !== '');
  if (order.includes('')) keys.push('');
  return keys.map((k) => ({ key: k, label: k === '' ? '—' : k, rows: buckets.get(k)! }));
}

const WEEKDAY_LABELS: Record<number, string> = {
  1: 'Montag',
  2: 'Dienstag',
  3: 'Mittwoch',
  4: 'Donnerstag',
  5: 'Freitag',
  6: 'Samstag',
  7: 'Sonntag',
};

const NO_DATE_LABEL = 'Ohne Datum';

/**
 * Weekday (1=Montag … 7=Sonntag) of a LocalDateTime string, or 0 when the date
 * part is missing or unparseable. Reads only 'YYYY-MM-DD' and computes the day
 * via UTC so the result is independent of the host timezone.
 */
function weekdayOf(value: unknown): number {
  if (typeof value !== 'string') return 0;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return 0;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return 0;
  const utc = new Date(Date.UTC(year, month - 1, day));
  // Round-trip guard: rejects e.g. 2026-02-31 (rolls into March).
  if (
    utc.getUTCFullYear() !== year ||
    utc.getUTCMonth() !== month - 1 ||
    utc.getUTCDate() !== day
  ) {
    return 0;
  }
  const dow = utc.getUTCDay(); // Sun=0 … Sat=6
  return dow === 0 ? 7 : dow;
}

/**
 * Bucket rows by the weekday of {@code dateAlias}. Groups are ordered
 * Montag→Sonntag, weekdays with no rows are omitted, and within each group the
 * input order is preserved (stable). Rows whose date is missing/unparseable
 * collect into a trailing {@code weekday: 0} "Ohne Datum" group, emitted last
 * only when non-empty.
 */
export function groupByWeekday(
  rows: Record<string, unknown>[],
  dateAlias = 'start',
): WeekdayGroup[] {
  const buckets = new Map<number, Record<string, unknown>[]>();
  for (const row of rows) {
    const weekday = weekdayOf(row[dateAlias]);
    let bucket = buckets.get(weekday);
    if (!bucket) {
      bucket = [];
      buckets.set(weekday, bucket);
    }
    bucket.push(row);
  }

  const groups: WeekdayGroup[] = [];
  for (let weekday = 1; weekday <= 7; weekday++) {
    const bucket = buckets.get(weekday);
    if (bucket && bucket.length > 0) {
      groups.push({ weekday, label: WEEKDAY_LABELS[weekday], rows: bucket });
    }
  }

  const noDate = buckets.get(0);
  if (noDate && noDate.length > 0) {
    groups.push({ weekday: 0, label: NO_DATE_LABEL, rows: noDate });
  }

  return groups;
}
