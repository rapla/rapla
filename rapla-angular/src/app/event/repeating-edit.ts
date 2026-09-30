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

import { localeId, t } from '../i18n/i18n.service';
import type { RepeatingRule } from './event-draft';

export type RepeatType = RepeatingRule['type'];
export type EndMode = 'FOREVER' | 'UNTIL' | 'COUNT';

/** Short weekday name for a rapla weekday (1=Sunday … 7=Saturday); 2024-01-01 is a Monday. */
function weekdayShort(raplaDay: number): string {
  return new Intl.DateTimeFormat(localeId(), { weekday: 'short' })
    .format(new Date(2024, 0, raplaDay - 1))
    .replace(/\.$/, '');
}

const weekdayEntry = (value: number): { value: number; label: string } => ({
  value,
  get label() {
    return weekdayShort(value);
  },
});

export const RAPLA_WEEKDAYS_MONDAY_FIRST: readonly { value: number; label: string }[] = [
  2, 3, 4, 5, 6, 7, 1,
].map(weekdayEntry);

/** rapla weekday (1=So…7=Sa) of an ISO LocalDateTime / date string. */
export function raplaWeekday(iso: string): number {
  return new Date(iso).getDay() + 1;
}

/**
 * Fresh rule on type selection — type-specific fields reset (the Swing
 * `savedRepeatingType` analog): WEEKLY seeds the start's weekday, everything
 * else derives from the start date; ending defaults to "after 10 occurrences"
 * (user ruling 2026-09-30 — an open-ended series is the exception, not the default).
 */
export function defaultRule(type: RepeatType, startIso: string): RepeatingRule {
  return {
    type,
    interval: 1,
    end: null,
    count: 10,
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

/**
 * The start's weekday is the series anchor and always part of a WEEKLY rule
 * (Swing locks its checkbox). When the start moves to another weekday the anchor
 * follows: the old weekday leaves unless the new one was already selected —
 * the {@code AppointmentImpl.move} rule.
 */
export function withAnchorWeekday(
  rule: RepeatingRule,
  oldStartIso: string,
  newStartIso: string,
): RepeatingRule {
  if (rule.type !== 'WEEKLY' || !rule.weekdays) return rule;
  const oldWd = raplaWeekday(oldStartIso);
  const newWd = raplaWeekday(newStartIso);
  if (oldWd === newWd) return rule;
  const kept = rule.weekdays.includes(newWd)
    ? rule.weekdays
    : rule.weekdays.filter((w) => w !== oldWd);
  const weekdays = [...new Set([...kept, newWd])].sort((a, b) => a - b);
  return { ...rule, weekdays, exceptions: [...rule.exceptions] };
}

const DAY_MS = 24 * 60 * 60_000;

/**
 * True when one occurrence lasts longer than the shortest gap to the next one,
 * i.e. the repetitions overlap themselves (a two-day appointment repeated daily).
 * The gap is the rule's smallest period: interval × 1/7/28/365 days, for WEEKLY
 * with several weekdays the closest pair (wrapping over the interval).
 */
export function occurrencesOverlap(
  rule: RepeatingRule,
  a: { start: string; end: string },
): boolean {
  const duration = new Date(a.end).getTime() - new Date(a.start).getTime();
  const interval = Math.max(1, rule.interval);
  let gapDays: number;
  switch (rule.type) {
    case 'DAILY':
      gapDays = interval;
      break;
    case 'WEEKLY': {
      const wds = [...(rule.weekdays ?? [])].sort((x, y) => x - y);
      gapDays = 7 * interval;
      for (let i = 0; i < wds.length; i++) {
        const next = i + 1 < wds.length ? wds[i + 1] : wds[0] + 7 * interval;
        gapDays = Math.min(gapDays, next - wds[i]);
      }
      break;
    }
    case 'MONTHLY':
      gapDays = 28 * interval;
      break;
    case 'YEARLY':
      gapDays = 365 * interval;
      break;
    default:
      return false;
  }
  return duration > gapDays * DAY_MS;
}

/** Toggle a skip date ('YYYY-MM-DD') — the preview's click-to-skip gesture (UC-E4). */
export function toggleException(rule: RepeatingRule, day: string): RepeatingRule {
  const exceptions = rule.exceptions.includes(day)
    ? rule.exceptions.filter((e) => e !== day)
    : [...rule.exceptions, day].sort();
  return { ...rule, weekdays: rule.weekdays ? [...rule.weekdays] : null, exceptions };
}

const TYPE_NAMES: Record<RepeatType, [string, string]> = {
  DAILY: ['event_rep_daily_one', 'event_rep_daily_many'],
  WEEKLY: ['event_rep_weekly_one', 'event_rep_weekly_many'],
  MONTHLY: ['event_rep_monthly_one', 'event_rep_monthly_many'],
  YEARLY: ['event_rep_yearly_one', 'event_rep_yearly_many'],
};

/** One-line human summary, e.g. "Wöchentlich am Di + Do · 10 Termine". */
export function ruleSummary(rule: RepeatingRule, startIso: string): string {
  const [oneName, manyName] = TYPE_NAMES[rule.type];
  let pattern = rule.interval === 1 ? t(oneName) : t('event_rep_every', rule.interval, t(manyName));
  if (rule.type === 'WEEKLY') {
    const days = (rule.weekdays ?? []).map((w) => weekdayShort(w)).join(' + ');
    pattern = t('event_rep_on', pattern, days || '—');
  } else if (rule.type === 'MONTHLY') {
    const start = new Date(startIso);
    pattern = t(
      'event_rep_on_nth',
      pattern,
      Math.ceil(start.getDate() / 7),
      weekdayShort(raplaWeekday(startIso)),
    );
  } else if (rule.type === 'YEARLY') {
    const start = new Date(startIso);
    pattern = t('event_rep_on', pattern, `${start.getDate()}.${start.getMonth() + 1}.`);
  }
  const mode = endModeOf(rule);
  const end =
    mode === 'FOREVER'
      ? t('event_rep_ends_never')
      : mode === 'COUNT'
        ? t('event_rep_count', rule.count)
        : t('event_rep_until', rule.end);
  return `${pattern} · ${end}`;
}
