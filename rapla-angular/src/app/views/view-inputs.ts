/**
 * Client-side resolver for {@code extensions.view.inputs} default specs (PRD 074,
 * locked 2026-06-21). Date defaults are never absolute (would go stale) — they are
 * structured {@code { anchor, offset, unit }} the SPA resolves at render time. This
 * is the single general rule (no closed sentinel list, no SPA redeploy per window).
 */

export type Anchor = 'TODAY' | 'WEEK_START' | 'MONTH_START';
export type Unit = 'DAYS' | 'WEEKS' | 'MONTHS';

export interface InputDefault {
  anchor: Anchor;
  offset: number;
  unit: Unit;
}

export interface ViewInput {
  name: string;
  control: string;
  default?: InputDefault;
}

const fmt = (d: Date): string => `${d.toISOString().slice(0, 10)}T00:00:00`;

/**
 * Resolve one anchor+offset spec to a zoneless {@code LocalDateTime} string.
 * Computed in UTC for determinism (same approach as {@code weekWindow}).
 */
export function resolveAnchorOffset(spec: InputDefault, now: Date = new Date()): string {
  const base = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

  switch (spec.anchor) {
    case 'WEEK_START': {
      const mondayOffset = (base.getUTCDay() + 6) % 7; // Mon=0 … Sun=6
      base.setUTCDate(base.getUTCDate() - mondayOffset);
      break;
    }
    case 'MONTH_START':
      base.setUTCDate(1);
      break;
    case 'TODAY':
      break;
  }

  switch (spec.unit) {
    case 'DAYS':
      base.setUTCDate(base.getUTCDate() + spec.offset);
      break;
    case 'WEEKS':
      base.setUTCDate(base.getUTCDate() + spec.offset * 7);
      break;
    case 'MONTHS':
      base.setUTCMonth(base.getUTCMonth() + spec.offset);
      break;
  }

  return fmt(base);
}

/**
 * Resolve the date-range window from a view's {@code inputs} block: the
 * {@code DATE_RANGE_START}/{@code DATE_RANGE_END} controls become {@code from}/
 * {@code to}. Returns null if either range control (with a default) is missing.
 */
export function resolveWindowFromInputs(
  inputs: ViewInput[],
  now: Date = new Date(),
): { from: string; to: string } | null {
  const start = inputs.find((i) => i.control === 'DATE_RANGE_START')?.default;
  const end = inputs.find((i) => i.control === 'DATE_RANGE_END')?.default;
  if (!start || !end) return null;
  return { from: resolveAnchorOffset(start, now), to: resolveAnchorOffset(end, now) };
}
