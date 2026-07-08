/**
 * PRD 091 Phase 4.2 — pure transforms for the recurrence editor. No Angular.
 *
 * All functions return fresh rule objects (the sheet routes them through its
 * mutateDraft funnel, so history snapshots must never share references).
 *
 * Weekday convention: rapla core, 1=Sunday … 7=Saturday — the GraphQL wire
 * passes core values through untranslated (schema doc fixed 2026-07-08; the
 * old "0-6" comment was wrong).
 */

import type { RepeatingRule } from './event-draft';

export type RepeatType = RepeatingRule['type'];
export type EndMode = 'FOREVER' | 'UNTIL' | 'COUNT';

export const RAPLA_WEEKDAYS_MONDAY_FIRST: readonly { value: number; label: string }[] = [
  { value: 2, label: 'Mo' },
  { value: 3, label: 'Di' },
  { value: 4, label: 'Mi' },
  { value: 5, label: 'Do' },
  { value: 6, label: 'Fr' },
  { value: 7, label: 'Sa' },
  { value: 1, label: 'So' },
];

/** rapla weekday (1=So…7=Sa) of an ISO LocalDateTime / date string. */
export function raplaWeekday(iso: string): number {
  return new Date(iso).getDay() + 1;
}

/**
 * Fresh rule on type selection — type-specific fields reset (the Swing
 * `savedRepeatingType` analog): WEEKLY seeds the start's weekday, everything
 * else derives from the start date; ending defaults to FOREVER (gcal parity;
 * Swing's default too).
 */
export function defaultRule(type: RepeatType, startIso: string): RepeatingRule {
  return {
    type,
    interval: 1,
    end: null,
    count: null,
    weekdays: type === 'WEEKLY' ? [raplaWeekday(startIso)] : null,
    exceptions: [],
  };
}

export function endModeOf(rule: RepeatingRule): EndMode {
  if (rule.end) return 'UNTIL';
  if (rule.count !== null) return 'COUNT';
  return 'FOREVER';
}

function isoDatePlusDays(startIso: string, days: number): string {
  const d = new Date(startIso);
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Switch the ending mode; the newly-active field gets a sensible seed
 * (UNTIL: start + 90 days, COUNT: 10), the inactive one is cleared — the
 * wire discriminates on end/count presence (end wins, see endModeOf).
 */
export function withEndMode(rule: RepeatingRule, mode: EndMode, startIso: string): RepeatingRule {
  return {
    ...rule,
    weekdays: rule.weekdays ? [...rule.weekdays] : null,
    exceptions: [...rule.exceptions],
    end: mode === 'UNTIL' ? (rule.end ?? isoDatePlusDays(startIso, 90)) : null,
    count: mode === 'COUNT' ? (rule.count ?? 10) : null,
  };
}

export function withInterval(rule: RepeatingRule, interval: number): RepeatingRule {
  return {
    ...rule,
    weekdays: rule.weekdays ? [...rule.weekdays] : null,
    exceptions: [...rule.exceptions],
    interval: Math.max(1, Math.floor(interval) || 1),
  };
}

export function withCount(rule: RepeatingRule, count: number): RepeatingRule {
  return {
    ...rule,
    weekdays: rule.weekdays ? [...rule.weekdays] : null,
    exceptions: [...rule.exceptions],
    end: null,
    count: Math.max(1, Math.floor(count) || 1),
  };
}

export function withUntil(rule: RepeatingRule, endDate: string): RepeatingRule {
  return {
    ...rule,
    weekdays: rule.weekdays ? [...rule.weekdays] : null,
    exceptions: [...rule.exceptions],
    end: endDate,
    count: null,
  };
}

/**
 * Toggle one weekday (WEEKLY only). The empty set is representable — the
 * panel shows an inline error instead of auto-correcting (OQ7 direction).
 */
export function toggleWeekday(rule: RepeatingRule, weekday: number): RepeatingRule {
  const current = rule.weekdays ?? [];
  const weekdays = current.includes(weekday)
    ? current.filter((w) => w !== weekday)
    : [...current, weekday].sort((a, b) => a - b);
  return { ...rule, weekdays, exceptions: [...rule.exceptions] };
}

/** Toggle a skip date ('YYYY-MM-DD') — the preview's click-to-skip gesture (UC-E4). */
export function toggleException(rule: RepeatingRule, day: string): RepeatingRule {
  const exceptions = rule.exceptions.includes(day)
    ? rule.exceptions.filter((e) => e !== day)
    : [...rule.exceptions, day].sort();
  return { ...rule, weekdays: rule.weekdays ? [...rule.weekdays] : null, exceptions };
}

const TYPE_NAMES: Record<RepeatType, [string, string]> = {
  DAILY: ['Täglich', 'Tage'],
  WEEKLY: ['Wöchentlich', 'Wochen'],
  MONTHLY: ['Monatlich', 'Monate'],
  YEARLY: ['Jährlich', 'Jahre'],
};

const WEEKDAY_LABELS: Record<number, string> = Object.fromEntries(
  RAPLA_WEEKDAYS_MONDAY_FIRST.map((w) => [w.value, w.label]),
);

/** One-line human summary, e.g. "Wöchentlich am Di + Do · 10 Termine". */
export function ruleSummary(rule: RepeatingRule, startIso: string): string {
  const [oneName, manyName] = TYPE_NAMES[rule.type];
  let pattern = rule.interval === 1 ? oneName : `Alle ${rule.interval} ${manyName}`;
  if (rule.type === 'WEEKLY') {
    const days = (rule.weekdays ?? []).map((w) => WEEKDAY_LABELS[w] ?? String(w)).join(' + ');
    pattern += ` am ${days || '—'}`;
  } else if (rule.type === 'MONTHLY') {
    const start = new Date(startIso);
    pattern += ` am ${Math.ceil(start.getDate() / 7)}. ${WEEKDAY_LABELS[raplaWeekday(startIso)]}`;
  } else if (rule.type === 'YEARLY') {
    const start = new Date(startIso);
    pattern += ` am ${start.getDate()}.${start.getMonth() + 1}.`;
  }
  const mode = endModeOf(rule);
  const end =
    mode === 'FOREVER'
      ? 'endet nie'
      : mode === 'COUNT'
        ? `${rule.count} Termine`
        : `bis ${rule.end}`;
  return `${pattern} · ${end}`;
}
